/************************************************************************
Copyright 2018 eBay Inc.
Author/Developer: Brendan McCarthy
 
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
 
    https://www.apache.org/licenses/LICENSE-2.0
 
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
**************************************************************************/
package com.ebay.bascomtask.main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.experimental.theories.internal.ParameterizedAssertionError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ebay.bascomtask.config.BascomConfigFactory;
import com.ebay.bascomtask.config.IBascomConfig;
import com.ebay.bascomtask.config.ITaskClosureGenerator;
import com.ebay.bascomtask.exceptions.InvalidGraph;
import com.ebay.bascomtask.exceptions.InvalidTask;
import com.ebay.bascomtask.exceptions.RuntimeGraphError;
import com.ebay.bascomtask.main.Call.Param;
import com.ebay.bascomtask.main.Task.Instance;
import com.ebay.bascomtask.main.Task.TaskMethodBehavior;

/**
 * A dataflow-driven collector and executor of tasks that implicitly honors all dependencies while pursuing maximum
 * parallelization with a minimum number of threads.
 * <p>
 * A task can be any POJO which can have any number of {@literal @}Work or {@literal @}PassThru annotated methods, which
 * we refer to as task methods. Once added, {@link #execute()} will execute those methods either if they have no
 * arguments or once those arguments become available as a result of executing other tasks. A task method's arguments
 * will never be null, so no null checks are required in task methods. For any POJO added as a task, either its
 * {@literal @}Work or its {@literal @}PassThru method(s) will be executed (never both), depending on whether it was
 * added by {@link #addWork(Object)} or by {@link #addPassThru(Object)}. {@literal @}PassThru is intended for use when a
 * task needs to do only simple bookkeeping but otherwise is not actively contributing to the overall result. Typically
 * a given POJO will have just one {@literal @}Work and possibly one {@literal @}PassThru method, but can have any
 * number of these; at runtime, whichever method matches based on available inputs will fire (execute).
 * <p>
 * An example pojo invoked with BascomTask might be as follows:
 * 
 * <pre>
 * class MyTask {
 *   {@literal @}Work void exec(MyOtherTask x, SomeOtherTask y) {...}
 * }
 * Orchestrator orc = Orchestrator.create();
 * orc.addWork(new new MyTask());
 * orc.addWork(new new MyOtherTask());
 * orc.addWork(new new SomeOtherTask());
 * orc.execute();
 * </pre>
 * 
 * If MyOtherTask and SomOtherTask have no common dependencies then they may be executed in parallel in different
 * threads. BascomTask is dataflow driven, attempting to execute tasks in parallel where possible while avoiding
 * wasteful creation of threads where possible.
 * <p>
 * Multiple instances of a given POJO class can also be added, each being executed separately and each being supplied to
 * all downstream task methods with that type as an argument. If two instances of MyOtherTask were added to the previous
 * example, each would start in its own thread and MyTask.exec() would be invoked twice, each time with a different
 * MyOtherTask instance. The default behavior for a task that receives multiple calls is simply to allow them to proceed
 * independently each firing in turn to any downstream tasks. This assumes that the task (MyTask in the above example)
 * is thread-safe. There are several options for varying this behavior by adding a scope argument to {@literal @}Work,
 * see {@link com.ebay.bascomtask.annotations.Scope} for a description of options. Even simpler is to simply change a
 * task argument to a list, in which case all instances of that type will be received at once.
 * 
 * @author brendanmccarthy
 */
public class Orchestrator {

    static final Logger LOG = LoggerFactory.getLogger(Orchestrator.class);

    private static class TaskRec {
        final List<DataFlowSource.Instance> added = new ArrayList<>(); // Unique elements
        final List<Binding> fired = new ArrayList<>(); // May have dups
    }

    /**
     * The config that will be used during any executions of this orchestrator.
     */
    final IBascomConfig config = BascomConfigFactory.getConfig();

    /**
     * The interceptor used for making POJO task method calls. If this remains null (because the caller has not set it,
     * which will usually be the case), then the default interceptor will be retrieved from <code>config</code>.
     */
    private ITaskClosureGenerator closureGenerator = null;

    /**
     * Allow for an override to be set, otherwise one will be retrieved from global config.
     */
    private ITaskClosureGenerator overrideClosureGenerator = null;

    /**
     * How we know which task instances are active for a given POJO task class,
     * relative to this orchestrator. For handling inheritance, an added instance of
     * <Sub extends Base> will result in two entries in this map, one for each of the types.
     */
    private final Map<Class<?>, TaskRec> taskMapByType = new HashMap<>();

    /**
     * Generally needed when constructing or modifying a new runtime graph of tasks.
     */
    private final List<Task.Instance> allTasks = new ArrayList<>();

    /**
     * Associates each POJO task with its Task.Instance.
     */
    private final Map<Object, Task.Instance> pojoMap = new HashMap<>();

    /**
     * To check for name conflicts
     */
    private final Map<String, Task.Instance> taskMapByName = new HashMap<>();

    /**
     * All Call.Instances of all Task.Instances in this orchestrator.
     */
    private final Map<Call, List<Call.Instance>> callMap = new HashMap<>();

    /**
     * All Param.Instances of all such calls in this orchestrator.
     */
    private final Map<Param, List<Param.Instance>> paramMap = new HashMap<>();

    /**
     * Aggregates exceptions, which may be more than one when sub-threads independently generate exceptions.
     */
    private List<Exception> exceptions = null;

    /**
     * A monotonically increasing counter.
     */
    final AtomicInteger threadsCreated = new AtomicInteger(0);

    /**
     * Number of spawned threads that have not yet terminated, increased when a thread is pulled from the pool, and
     * decreased when returned.
     */
    private int threadBalance = 0;

    /**
     * How {@link #execute()} knows to exit: when this list is empty. NoWait tasks are never added to this list.
     */
    private final Set<Task.Instance> waitForTasks = new HashSet<>();

    /**
     * Set before spawning a new thread, when it is detected that the main thread is idle and may as well do the work
     * itself.
     */
    private TaskMethodClosure invocationPickup = null;

    /**
     * How a non-main thread knows the main thread's invocationPickup can be set
     */
    private boolean waiting = false;

    /**
     * Accumulates instances prior to execute() -- thread-specific to avoid conflicts between two separate threads
     * dynamically adding tasks at the same time
     */
    private Map<Thread, List<Task.Instance>> nestedAdds = new HashMap<>();

    /**
     * For debugging/logging each Orchestrator has a unique id
     */
    private final String id;

    /**
     * When to timeout
     */
    private long maxExecutionTimeMillis = 0;

    /**
     * How long to wait before waking up to see if we can terminate
     */
    private long maxWaitTime = 0;

    /**
     * For tracking execution time
     */
    private long startTime = 0;

    /**
     * For debugging, callers can optionally set a name
     */
    private String name = null;

    /**
     * Set to true after first invocation
     */
    private boolean hasBeenInvokedYet = false;

    /**
     * For determining when to log debug messages while waiting
     */
    private int waitLoopDebugCounter = 0;

    /**
     * Records thread-specific metadata as a map rather than thread-local, so that it can be retrieved in any context.
     */
    private Map<Thread, TaskThreadStat> threadMap = new ConcurrentHashMap<>();

    /**
     * The top-level orchestrator may have a rollback orchestrator.
     */
    private Orchestrator rollback = null;

    /**
     * True iff this is an internally-created rollback orchestrator.
     */
    private final boolean isRollBack;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Orchestrator(");
        sb.append(id);
        sb.append(",#tasks=");
        sb.append(allTasks.size());
        sb.append(",#calls=");
        sb.append(callMap.size());
        sb.append(",#waiting=");
        sb.append(waitForTasks.size());
        sb.append(",#threads=");
        sb.append(threadBalance);
        sb.append(')');
        return sb.toString();
    }
    
    /**
     * Creates a new Orchestrator. New Orchestrator instances are typically created anew
     * for each unit of work, and this is the standard way to create one. 
     * @return empty and read-to-use Orchestrator
     */
    public static Orchestrator create() {
        return new Orchestrator(false);
    }

    private static ProfilingTracker keeper = null;    
    
    /**
     * Returns an object for reporting stats across all orchestrators.
     * @return stat profiling object
     */
    public static ProfilingTracker stat() {
        synchronized (Orchestrator.class) {
            if (keeper == null) {
                keeper = new ProfilingTracker();
            }
        }
        return keeper;
    }

    /**
     * Constructor hidden behind static create method in anticipation of future caching or other variations.
     */
    private Orchestrator(boolean isRollBack) {
        this.isRollBack = isRollBack;
        this.id = String.valueOf(hashCode());
    }

    private ExecutionStats waitStats = new ExecutionStats();
    private ExecutionStats noWaitStats = new ExecutionStats();

    public class ExecutionStats {
        /**
         * Each invocation of a task method increments this field by 1
         */
        private int taskInvocationCount;

        /**
         * Each invocation of a task method adds to this field
         */
        private long accumulatedTaskTime;

        /**
         * Absolute time when processing completes
         */
        private long endTime;

        ExecutionStats() {
        }

        ExecutionStats(ExecutionStats that) {
            this.taskInvocationCount = that.taskInvocationCount;
            this.accumulatedTaskTime = that.accumulatedTaskTime;
            this.endTime = that.endTime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o instanceof ExecutionStats) {
                ExecutionStats that = (ExecutionStats) o;
                if (this.getOrchestrator() != that.getOrchestrator())
                    return false;
                if (this.taskInvocationCount != that.taskInvocationCount)
                    return false;
                if (this.accumulatedTaskTime != that.accumulatedTaskTime)
                    return false;
                if (this.endTime != that.endTime)
                    return false;
            }
            else {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Orchestrator.this,taskInvocationCount,accumulatedTaskTime,endTime);
        }

        @Override
        public String toString() {
            return ("Ex(tasks=" + taskInvocationCount + ",par=" + getParallelizationSaving() + ",acc="
                    + accumulatedTaskTime + ",time=" + getExecutionTime());
        }

        private Orchestrator getOrchestrator() {
            return Orchestrator.this;
        }

        private void update(long incrementalTime) {
            this.accumulatedTaskTime += incrementalTime;
            this.taskInvocationCount++;
        }

        /**
         * Returns the number of task method calls made.
         * 
         * @return count of number of tasks executed
         */
        public int getNumberOfTasksExecuted() {
            return taskInvocationCount;
        }

        /**
         * Returns the time saved by executing tasks in parallel rather than sequentially. Note that if all tasks were
         * sequential (due to ordering), then this value would be slightly negative, accounting for the overhead of the
         * orchestrator itself.
         * 
         * @return time saved in ms
         */
        public long getParallelizationSaving() {
            return accumulatedTaskTime - getExecutionTime();
        }

        /**
         * Returns the time spent during the last call to {@link #execute()}. The result is undefined is that call has
         * not yet completed. Any uncompleted nowait tasks are not accounted for.
         * 
         * @return execution time in ms
         */
        public long getExecutionTime() {
            synchronized (Orchestrator.this) {
                return endTime - startTime;
            }
        }
    }

    /**
     * Returns a snapshot of execution statistics resulting from a previous execution, excluding any nowait tasks. This
     * method is guaranteed to return the same result once the outermost execute() has completed.
     * 
     * @return stats snapshot
     */
    public ExecutionStats getStats() {
        return new ExecutionStats(waitStats);
    }

    /**
     * Returns a snapshot of execution statistics resulting from a previous execution, including any nowait tasks. This
     * method is guaranteed to return the same result once the outermost execute() has completed <i>and</i> all nowait
     * tasks have completed.
     * 
     * @return stats snapshot
     */
    public ExecutionStats getNoWaitStats() {
        return new ExecutionStats(noWaitStats);
    }

    /**
     * Sets a name used on entry and exit debugging statements, useful for identify which orchestrator is being invoked
     * when there are many involved.
     * 
     * @param name to include in debug statements
     * @return this instance for fluent-style chaining
     */
    public Orchestrator name(String name) {
        this.name = name;
        return this;
    }

    /**
     * The name previously set by {@link #name(String)}
     * 
     * @return the name or null if not previously set
     */
    public String getName() {
        return name;
    }

    /**
     * Returns an id for this instance which has very low probability of clashing with other ids.
     * 
     * @return id of this object
     */
    public String getId() {
        return id;
    }

    /**
     * Sets a closure generator to use for this orchestrator, rather than the default which is retrieved from whichever
     * IBascomConfig is active.
     * 
     * @param generator to use in place of default
     * @return this instance for fluent-style chaining
     * @see com.ebay.bascomtask.config.IBascomConfig#getExecutionHook(Orchestrator, String)
     */
    public synchronized Orchestrator closureGenerator(ITaskClosureGenerator generator) {
        this.overrideClosureGenerator = generator;
        return this;
    }

    TaskMethodClosure getTaskMethodClosure(TaskMethodClosure parent, Call.Instance callInstance, Object[] args, TaskMethodClosure longestIncoming) {
        TaskMethodClosure closure = null;
        if (parent != null) {
            closure = parent.getClosure();
            if (closure != null) {
                closure.setParent(parent);
            }
            // If return true, then default to generator
        }
        if (closure == null) {
            closure = closureGenerator.getClosure();
        }
        closure.initCall(callInstance,args,longestIncoming);
        return closure;
    }

    public TaskThreadStat getThreadStatForCurrentThread() {
        Thread t = Thread.currentThread();
        TaskThreadStat threadStat = threadMap.get(t);
        if (threadStat == null) {
            throw new RuntimeException("Unbound threadStat from thread name=" + t.getName() + " id=" + t.getId());
        }
        return threadStat;
    }

    void setThreadStatForCurrentThread(TaskThreadStat stat) {
        threadMap.put(Thread.currentThread(),stat);
    }

    /**
     * Returns the number of additional threads (not including the starting thread) used in computing the result from
     * this orchestrator. The value may increase until all tasks are terminated.
     * 
     * @return count of created threads
     */
    public int getNumberOfThreadsCreated() {
        return threadsCreated.get();
    }

    /**
     * Returns the number of threads that have been spawned but not yet completed. The returned value may only be &gt; 0
     * after a call to {@link #execute()} if there are nowait tasks.
     * 
     * @return number of open threads
     */
    public int getNumberOfOpenThreads() {
        return threadBalance;
    }

    /**
     * A convenience method, adds a task with a default name that is either active or passive depending on the given
     * condition.
     * 
     * @param task java POJO to add as task
     * @param cond if true then add as active else add as passive
     * @return a 'shadow' task object which allows for various customizations
     */
    public ITask addConditionally(Object task, boolean cond) {
        if (cond) {
            return addWork(task);
        }
        else {
            return addPassThru(task);
        }
    }

    /**
     * Adds a task to be made available to other task's task ({@literal @}Work or {@literal @}PassThru) methods, and
     * whose own {@literal @}Work methods will only be invoked (fired) when its task arguments have so fired.
     * <p>
     * The rules for execution are as follows:
     * <ul>
     * <li>A POJO can have no {@literal @}Work methods in which case it will instantly be available to other tasks.
     * <li>Any {@literal @}Work method with no arguments will be started immediately in its own thread (which might be
     * the calling thread of the orchestrator if it is not busy with other work).
     * <li>Any {@literal Work} method with arguments will only be invoked when all of its task parameters have
     * themselves completed with a proper return (see last point below).
     * <li>Each {@literal @}Work method will be invoked with the cross-product of all available matching instances.
     * </ul>
     * <p>
     * Regarding the last point, although it is common to add just one instance of a given POJO type, it is safe to add
     * any number. The default behavior of {@literal @}Work methods receiving argument sequences with multiple instances
     * is that each is executed potentially in parallel. Different options can be set through {@literal @}Work.scope,
     * see {@link com.ebay.bascomtask.annotations.Scope}. Alternatively, a @{literal @}Work method parameter can simply
     * be a {@link java.util.List} of tasks in which case the entire set of matching instances will be made available
     * once all instances are available.
     * <p>
     * An added task will have no effect until {@link #execute(long)} is called, either directly or implicitly as a
     * result of a nested task method completing.
     * <p>
     * A {@literal @}Work can return a boolean result or simply be void, which equates to returning {@code true}. A
     * return value of {@code false} indicates that downstream tasks should not fire: any task fires only if all of its
     * inputs have fired, except for {@link java.util.List} parameters, which never prevent firing but instead any
     * false-returning tasks will be excluded from the list (which means that a list parameter may be empty).
     * 
     * @param task java POJO to add as task
     * @return a 'shadow' task object which allows for various customizations
     */
    public ITask addWork(Object task) {
        return add(task,TaskMethodBehavior.WORK);
    }

    /**
     * Adds a task whose {@literal @}PassThru methods (rather than its {@literal @}@Work methods) will be invoked,
     * always in the calling thread -- no separate thread is created to execute a {@literal @}PassThru method since it
     * assumed to perform simple actions like providing a default or passing-through its arguments with no or little
     * change. Otherwise behaves the same as {@link #addWork(Object)}.
     * 
     * @param task java POJO to add as task
     * @return a 'shadow' task object which allows for various customizations
     */
    public ITask addPassThru(Object task) {
        return add(task,Task.TaskMethodBehavior.PASSTHRU);
    }

    /**
     * Adds an object without considering any {@literal @}Work or {@literal @}PassThru methods even if they exist, as if
     * the object did not have such methods in the first place. This enables the object to be make available as a
     * parameter to other task methods without the object's task methods firing. Usage of this method is relatively
     * uncommon, but does provide another way to handle variant situations be allowing a task object to be exposed but
     * have its behavior managed outside the scope of an orchestrator.
     * 
     * @param task to add
     * @return a 'shadow' task object which allows for various customizations
     */
    public ITask addIgnoreTaskMethods(Object task) {
        return add(task,Task.TaskMethodBehavior.NONE);
    }

    /**
     * Records an added task to be added during a subsequent execute(), whether the latter is explicit or implied as a
     * result of a nested inline call. We don't add right away because of the possibility of other active threads. The
     * actual addition is only performed when we can synchronize the orchestrator and perform the additions atomically
     * without impacting running threads.
     * 
     * @param targetTask java POJO to add as simple task
     * @param taskMethodBehavior describing how task was added
     * @return newly created ITask
     */
    private ITask add(Object targetTask, Task.TaskMethodBehavior taskMethodBehavior) {
        Class<?> targetClass = targetTask.getClass();
        Task task = TaskParser.parse(targetClass);
        Task.Instance taskInstance = task.new Instance(this,targetTask,taskMethodBehavior);
        Thread t = Thread.currentThread();
        synchronized (nestedAdds) {
            List<Task.Instance> taskInstances = nestedAdds.get(t);
            if (taskInstances == null) {
                taskInstances = new ArrayList<>();
                nestedAdds.put(t,taskInstances);
            }
            taskInstances.add(taskInstance);
        }
        return taskInstance;
    }
    
    /**
     * Returns the ITask wrapper for a POJO task that has already been added to this orchestrator
     * @param targetTask to match 
     * @return ITask for previously added POJO or null if no such POJO was ever added 
     */
    public synchronized ITask asAdded(Object targetTask) {
        ITask task = pojoMap.get(targetTask);
        if (task == null) {
            List<Task.Instance> tasks = nestedAdds.get(Thread.currentThread());
            if (tasks != null) {
                for (Task.Instance next: tasks) {
                    if (next.getTargetPojo() == targetTask) {
                        task = next;
                        break;
                    }
                }
            }
        }
        return task;
    }

    void notifyWaitStatusChange(com.ebay.bascomtask.main.Task.Instance instance, boolean wait) {
        if (wait) {
            waitForTasks.add(instance);
        }
        else {
            waitForTasks.remove(instance);
        }
    }

    /**
     * Throws exception conflicts with a task already linked in the graph, or another task added but not yet linked
     * 
     * @param name
     */
    void checkPendingTaskInstanceInstanceName(String name) {
        checkUniqueTaskInstanceName(name);
        List<Task.Instance> pending = nestedAdds.get(Thread.currentThread());
        for (Task.Instance nextTaskInstance : pending) {
            if (name.equals(nextTaskInstance.getName())) {
                throw new InvalidTask.NameConflict("Task name\"" + name + "\" already in use");
            }
        }
        // There still might be a conflict when we add nestedAdds to the graph, but we can't
        // check that until that actually occurs due the way names are auto-generated.
    }

    private void checkUniqueTaskInstanceName(String name) {
        if (taskMapByName.get(name) != null) {
            throw new InvalidTask.NameConflict("Task name\"" + name + "\" already in use");
        }
    }

    public synchronized void recordException(Call.Instance callInstance, Exception e) {
        recordException(e);
        callInstance.taskInstance.addException(e);
    }

    public synchronized void recordException(Exception e) {
        if (exceptions == null) {
            exceptions = new ArrayList<>();
        }
        else if (exceptions.contains(e)) {
            return;
        }
        exceptions.add(e);
    }

    /**
     * Calls {@link #execute(long)} with a default from current
     * {@link com.ebay.bascomtask.config.IBascomConfig#getDefaultOrchestratorTimeoutMs()}
     */
    public void execute() {
        execute(null);
    }

    public void execute(String pass) {
        execute(config.getDefaultOrchestratorTimeoutMs(),pass);
    }

    public void execute(long maxExecutionTimeMillis) {
        execute(maxExecutionTimeMillis,null);
    }

    /**
     * Begins processing of previously added tasks within the calling thread, spawning new threads where possible to
     * exploit parallel execution and returning when all no-wait tasks are finished. Any task <i>will</i> hold up the
     * exit of this method when all of the following apply:
     * <ol>
     * <li>It has <i>not</i> been flagged as no-wait
     * <li>It has at least one {@literal @}Work (or {@literal @}PassThru) method that can fire (because there are
     * matching instances as parameters)
     * <li>At least one of those methods has not completed or may fire again
     * </ol>
     * This method can safely be called multiple times, each time accounting for any new tasks added and firing all
     * tasks that are fireable as a result of those new tasks added. In this way, each call to execute acts like a
     * synchronizer across all executable tasks. Tasks added within a nested task can but do not have to call execute()
     * if they add tasks, as there is an implicit call done automatically in this case. Note that tasks added by other
     * threads will be invisible to the calling thread.
     * <p>
     * Consistency checks are performed prior to any task being started, and if a violation is found a subclass of
     * InvalidGraph is thrown.
     * <p>
     * If any task throws an exception, which would have to be a non-checked exception, it will be propagated from this
     * call. Once such an exception is thrown, the orchestrator ceases to start new tasks and instead waits for all open
     * threads to finish, begins @{link {@link #rollback()} processing, and then re-throws the original exception.
     * 
     * @param maxExecutionTimeMillis after which no new tasks are created (no active tasks are interrupted)
     * @param pass description passed to config executors, useful (often only for logging) for distinguishing one
     *            invocation this method from another
     * @throws RuntimeException generated from a task
     * @throws RuntimeGraphError.Multi if more than one exception is thrown from different tasks
     * @throws RuntimeGraphError.Timeout when the requested timeout has been exceeded
     * @throws InvalidTask.AlreadyAdded if the same task instance was added more than once
     * @throws InvalidTask.BadParam if a task method has a parameter that cannot be processed
     * @throws InvalidGraph.MissingDependents if a task cannot be exited because it has no matching {@literal @}Work
     *             dependents
     * @throws InvalidGraph.Circular if a circular reference between two tasks is detected
     * @throws InvalidGraph.MultiMethod if a task has more than one callable method and is not marked multiMethodOk()
     * @throws InvalidGraph.ViolatedProvides if a task was indicated to
     *             {@link com.ebay.bascomtask.main.ITask#provides(Class)} an instance but this was not done (or
     *             {@literal @}PassThru) method that has all of its parameters available as instances
     */
    public void execute(long maxExecutionTimeMillis, String pass) {
        final Thread t = Thread.currentThread();
        List<Task.Instance> taskInstances = nestedAdds.remove(t);
        boolean currentThreadIsCallingThread;
        synchronized (this) {
            if (callingThread == null) {
                // Set root TaskThreadStat to current thread
                setThreadStatForCurrentThread(new TaskThreadStat(this,0,0,null));

                this.maxExecutionTimeMillis = maxExecutionTimeMillis;
                this.maxWaitTime = Math.min(maxExecutionTimeMillis,500);
                startTime = System.currentTimeMillis();
                callingThread = t;

                if (overrideClosureGenerator != null) {
                    closureGenerator = overrideClosureGenerator;
                }
                else {
                    closureGenerator = config.getExecutionHook(this,pass);
                }
            }
            currentThreadIsCallingThread = callingThread == t;
        }

        if (currentThreadIsCallingThread) { // Can only happen once for top-level calling thread
            try {
                executeTasks(taskInstances,"top-level");
                waitForCompletion();
                checkForExceptions();
            }
            finally {
                synchronized (this) {
                    callingThread = null;
                    waitStats.endTime = noWaitStats.endTime = System.currentTimeMillis();
                }
            }
        }
        else { // Otherwise process taskInstances added by this thread if any
            if (threadMap.get(t) == null) {
                // Edge case: if the caller attempts to invoke execute again from a thread outside
                // of executor control, it won't have a map entry. Easiest to just ignore such a
                // case since since it would have dubious utility.
                LOG.warn("Rejected secondary attempt to invoke execute() at top-level");
            }
            executeTasks(taskInstances,"nested");
        }
    }

    private void executeTasks(List<Task.Instance> taskInstances, String context) {
        TaskMethodClosure inv = executeTasks(taskInstances,context,null);
        invokeAndFinish(inv,"own",true);
    }

    private class OrcRun {
        final String pfx;
        final int numberTasks;
        final String tasksPlural;
        final int numberRoots;
        final String rootsPlural;
        final String id;
        final int currentTaskCount;
        final String context;

        OrcRun(List<Call.Instance> roots, String context) {
            this.pfx = hasBeenInvokedYet ? "" : "first ";
            this.numberTasks = allTasks.size();
            this.tasksPlural = numberTasks == 1 ? "" : "s";
            this.numberRoots = roots.size();
            this.rootsPlural = numberRoots == 1 ? "" : "s";
            this.id = name == null ? "" : ("\"" + name + "\"");
            this.currentTaskCount = allTasks.size();
            this.context = context;
        }

        void report(String where) {
            report(where,null);
        }

        void report(String where, TaskMethodClosure inv) {
            if (LOG.isDebugEnabled()) {
                String gs = getGraphState();
                String last = inv == null ? ("\n" + gs) : (", pending " + inv + ("\n" + gs));
                LOG.debug("{} {}{} {}with {}->{} task{} / {} root{}{}",where,pfx,context,id,currentTaskCount,
                        numberTasks,tasksPlural,numberRoots,rootsPlural,last);
            }
        }
    }

    private synchronized TaskMethodClosure executeTasks(List<Task.Instance> taskInstances, String context,
            TaskMethodClosure inv) {
        if (taskInstances != null) {

            List<Call.Instance> roots = linkGraph(taskInstances);
            if (roots.size() > 0) {
                OrcRun run = null;
                if (LOG.isDebugEnabled()) {
                    run = new OrcRun(roots,context);
                    run.report("firing");
                }
                inv = fireRoots(roots,inv);
                if (run != null) {
                    run.report("exiting",inv);
                }
            }
        }
        return inv;
    }

    /**
     * Thread which calls begin(), is non-null only when outermost execute() is active
     */
    private Thread callingThread = null;

    boolean isCallingThread() {
        return Thread.currentThread() == callingThread;
    }

    int linkLevel = 0;

    /**
     * Adds the given tasks and links each parameter of each applicable (@Work or @PassThru) call with each instance of
     * that parameter's type. Also verifies that all tasks are callable, by ensuring that they have at least one such
     * call that has all parameters available.
     * 
     * @param taskInstances to be added to the graph
     * @throws InvalidGraph if any task is un-callable, and if so include list of all such tasks (if more than one)
     * @return non-null but possibly empty list of instances ready to fire
     */
    private List<Call.Instance> linkGraph(List<Task.Instance> taskInstances) {
        Map<Class<?>, Task.Instance> toBeProvided = null;
        // First establish all references from the orchestrator to taskInstances.
        // Later steps depend on these references having been set.
        for (Task.Instance taskInstance : taskInstances) {
            addAncestryMapping(taskInstance);
            List<Class<?>> provides = taskInstance.getProvides();
            if (provides != null && provides.size() > 0) {
                if (toBeProvided == null) {
                    toBeProvided = new HashMap<>();
                }
                // Only need 1 to ensure that the dependent task gets kicked off
                toBeProvided.put(provides.get(0),taskInstance);
            }
        }
        // Ensure explicitBeforeDependencies are set on all taskInstances so
        // that we can update thresholds in the next step
        for (Task.Instance taskInstance : taskInstances) {
            taskInstance.updateExplicitDependencies(pojoMap);
        }
        // Same effect as previous, but specifically accounts for the case where
        // an existing task (not in taskInstances) must come before this one.
        for (Task.Instance taskInstance : allTasks) {
            taskInstance.updateExplicitDependencies(pojoMap);
        }
        
        List<Call.Instance> roots = new ArrayList<>();
        // Now the taskInstances themselves can be linked
        for (Task.Instance taskInstance : taskInstances) {
            if (taskInstance.calls.size() == 0) {
                // If no calls at all, the task is trivially available to all who depend on it.
                // We create a dummy call so the task can then be processed like any other root.
                roots.add(taskInstance.genNoCall());
                // Since this task is already available as a parameter, no point
                // in having any explicit dependencies on it, should there be any
                // TODO thread-safe dynamic explicits
                taskInstance.clearExplicits();
            }
            else {
                linkAll(taskInstance,roots);
            }
        }
        // With all linkages in place, thresholds can be (re)computed, and final verification performed
        List<Call.Param.Instance> badParams = recomputeAllThresholdsAndVerify(toBeProvided);
        if (badParams != null) {
            throwUncompletableParams(badParams);
        }
        return roots;
    }

    private void addAncestryMapping(Task.Instance taskInstance) {
        if (pojoMap.get(taskInstance.targetPojo) != null) {
            throw new InvalidTask.AlreadyAdded("Invalid attempt to add task twice: " + taskInstance.targetPojo);
        }
        pojoMap.put(taskInstance.targetPojo,taskInstance);
        allTasks.add(taskInstance);
        TaskRec rec = mapAncestry(taskInstance);
        setTaskInstanceName(taskInstance,rec);
        recordCallsAndParameterInstances(taskInstance);
    }
    
    private void setTaskInstanceName(Task.Instance taskInstance, TaskRec rec) {
        taskInstance.setIndexInType(rec.added.size()-1);
        String tn = taskInstance.getName();
        checkUniqueTaskInstanceName(tn);
        taskMapByName.put(tn,taskInstance);
    }
    
    private TaskRec mapAncestry(DataFlowSource.Instance source) {
        TaskRec firstRec = null;
        for (Class<?> next: source.getSource().ancestry) {
            TaskRec nextRec = addTypeMapping(source,next);
            if (firstRec==null) {
                firstRec = nextRec;
            }
        }
        return firstRec;
    }

    private TaskRec addTypeMapping(DataFlowSource.Instance taskInstance, /*Task task, */ Class<?> taskClass) {
        TaskRec rec = taskMapByType.get(taskClass);
        if (rec == null) {
            rec = new TaskRec();
            taskMapByType.put(taskClass,rec);
        }
        else {
            System.out.println("here: " + taskInstance + " ==> " + taskClass);
        }
        rec.added.add(taskInstance);
        return rec;
    }
    
    private void recordCallsAndParameterInstances(Task.Instance task) {
        for (Call.Instance callInstance : task.calls) {
            mapAncestry(callInstance);
            Call call = callInstance.getCall();
            List<Call.Instance> callInstances = callMap.get(call);
            if (callInstances == null) {
                callInstances = new ArrayList<>();
                callMap.put(call,callInstances);
            }
            for (Param.Instance nextParamInstance : callInstance.paramInstances) {
                Param nextParam = nextParamInstance.getParam();
                List<Param.Instance> paramInstances = paramMap.get(nextParam);
                if (paramInstances == null) {
                    paramInstances = new ArrayList<>();
                    paramMap.put(nextParam,paramInstances);
                }
                paramInstances.add(nextParamInstance);
            }
        }
    }

    /**
     * Recomputes thresholds and returns bad parameters if any
     * 
     * @return possibly null list of bad parameters
     */
    private List<Call.Param.Instance> recomputeAllThresholdsAndVerify(final Map<Class<?>, Task.Instance> toBeProvided) {
        linkLevel++;
        List<Call.Param.Instance> badParams = null;
        for (Task.Instance nextTaskInstance : allTasks) {
            computeThreshold(nextTaskInstance,linkLevel,toBeProvided);
            if (nextTaskInstance.calls.size() > 0) { // Don't do the enclosed checks if there are no calls
                // If at least one call for a task has all parameters, the graph is resolvable.
                // If there is no such call, report all unbound parameters.
                if (!nextTaskInstance.isCompletable()) {
                    if (badParams == null) {
                        badParams = new ArrayList<>();
                    }
                    boolean any = false;
                    for (Call.Instance nextCall : nextTaskInstance.calls) {
                        for (Param.Instance nextParam : nextCall.paramInstances) {
                            if (nextParam.getThreshold() == 0) {
                                badParams.add(nextParam);
                                any = true;
                            }
                        }
                    }
                    if (!any) {
                        throw new RuntimeException("Internal error, task falsely not completable: " + nextTaskInstance);
                    }
                }
            }
        }
        return badParams;
    }

    /**
     * Links a taskInstance together with any incoming tasks or outgoing parameters.
     * 
     * @param taskInstance to link
     * @param roots to add to for each call of each of the supplied taskInstances that is immediately ready to fire
     */
    private void linkAll(Task.Instance taskInstance, List<Call.Instance> roots) {
        int matchableCallCount = 0;
        List<Task.Instance> explicitsBefore = taskInstance.getExplicitsBefore();
        for (Call.Instance callInstance : taskInstance.calls) {
            System.out.println("LinkAll call="+callInstance);
            boolean match = true;
            boolean root = true;
            for (int i = 0; i < callInstance.paramInstances.length; i++) {
                Call.Param.Instance paramInstance = callInstance.paramInstances[i];
                System.out.println("LinkAll param("+i+")="+paramInstance);
                TaskRec rec = enumerateDependentTaskParameters(paramInstance,explicitsBefore);
                if (rec == null) {
                    if (isInjectable(paramInstance)) {
                        // Perform the same effects as below without accounting
                        // for anything having fired
                        TaskMethodClosure injectionClosure = new TaskMethodClosure();
                        injectionClosure.initCall(taskInstance);
                        Binding binding = new Binding(injectionClosure,taskInstance);
                        paramInstance.addActual(binding);
                        callInstance.startingFreeze[i] = 1;
                    }
                    else {
                        match = root = false;
                    }
                }
                else {
                    final int indexFrom = paramInstance.getParam().getIndexFrom();
                    int indexTo = paramInstance.getParam().getIndexTo();
                    if (indexTo < 0) {  // e.g. list
                        indexTo = rec.added.size();
                    }
                    else if (indexTo > rec.added.size()) {
                        throw new RuntimeException("TBD too many, expecting " + indexTo + " got " + indexTo);
                    }
                    //for (DataFlowSource.Instance supplierTaskInstance : rec.added) {
                    for (int tix=indexFrom; tix < indexTo; tix++) {
                        DataFlowSource.Instance supplierTaskInstance = rec.added.get(tix);
                        boolean linked = backLink(supplierTaskInstance,paramInstance);
                        System.out.println("Next DFS-" + linked + " " + supplierTaskInstance + " to " +paramInstance + ", from="+indexFrom +", to="+indexTo);
                        if (!linked) {
                            break;
                        }
                    }
                    int numberAlreadyFired = rec.fired.size();
                    if (numberAlreadyFired == 0) {
                        root = false;
                    }
                    else if (paramInstance.getParam().accumulate() && numberAlreadyFired < paramInstance.getThreshold()) {
                        root = false;
                    }
                    // Add all tasks that have already fired
                    callInstance.startingFreeze[i] = numberAlreadyFired;
                    for (Binding fired : rec.fired) {
                        paramInstance.addActual(fired);
                    }
                }
            }
            if (explicitsBefore != null) {
                root &= addExplicitNonParameters(taskInstance,explicitsBefore);
            }
            if (root) {
                roots.add(callInstance);
            }
            if (match) {
                matchableCallCount++;
            }
        }
        if (matchableCallCount > 1 && !taskInstance.isMultiMethodOk()) {
            String msg = "Task " + taskInstance.getName() + " has " + matchableCallCount;
            msg += " matching calls, either remove the extra tasks or mark task as multiMethodOk()";
            throw new InvalidGraph.MultiMethod(msg);
        }
        // Although new taskInstances are linked above, this final pass ensures that
        // taskInstance is backlinked to any previously-existing instances.
        // 
        /* TBD needed? Breaks new logic
        for (Param backParam : taskInstance.getTask().backList) {
            List<Param.Instance> paramInstances = paramMap.get(backParam);
            if (paramInstances != null) {
                // It will be null if it links e.g. to a passthru method but was
                // added as a work task
                for (Param.Instance nextParamInstance : paramInstances) {
                    if (!nextParamInstance.isExplicitlyWired()) {
                        backLink(taskInstance,nextParamInstance);
                    }
                }
            }
        }
        */
    }

    private boolean isInjectable(Param.Instance paramInstance) {
        return ITask.class.equals(paramInstance.getTask().producesClass);
    }

    /**
     * Backlink explicit parameters that were not previously mapped to formal parameters.
     * @param taskInstance
     * @param explicitsBefore
     * @return
     */
    private boolean addExplicitNonParameters(Task.Instance taskInstance, List<Task.Instance> explicitsBefore) {
        boolean root = true;
        for (Task.Instance next : explicitsBefore) {
            Task task = next.getTask();
            TaskRec rec = taskMapByType.get(task.producesClass);
            for (Call.Instance call : taskInstance.calls) {
                Call.Param.Instance paramInstance = call.addHiddenParameter(task);
                int sz = 0;
                backLink(next,paramInstance);
                for (Binding fired : rec.fired) {
                    if (fired.closure.getTargetPojoTask() == next.targetPojo) {
                        paramInstance.addActual(fired);
                        sz++;
                    }
                }
                if (sz < paramInstance.getThreshold()) {
                    root = false;
                }
            }
        }
        return root;
    }

    /**
     * Produces a record of all the tasks upon which the given call parameter must wait, accounting for auto and
     * explicit wiring.
     * 
     * @param paramInstance of target call
     * @param explicitsBefore containing explicitly-declared dependencies, and from which formal param matches will be
     *            removed
     * @return record of all tasks that must fire before <code>paramInstance</code>
     */
    private TaskRec enumerateDependentTaskParameters(Call.Param.Instance paramInstance, List<Task.Instance> explicitsBefore) {
        //Task task = paramInstance.getTask();
        DataFlowSource source = paramInstance.getDataFlowSource();
        TaskRec rec = taskMapByType.get(source.producesClass);
        if (explicitsBefore != null) { // If no explicit parameters than all known instances are auto-wired
            for (int i=explicitsBefore.size()-1; i>=0; i--) {
                Task.Instance next = explicitsBefore.get(i);
                Class<?> nextClass = next.getTask().producesClass;
                if (source.producesClass.isAssignableFrom(nextClass)) {
                    TaskRec alt = new TaskRec();
                    alt.added.add(next);
                    explicitsBefore.remove(i); // Not a 'hidden' param if it's an explicit formal param
                    for (Binding fired : rec.fired) {
                        if (fired.closure.getTargetPojoTask() == next.targetPojo) {
                            alt.fired.add(fired);
                            // xxx review
                        }
                    }
                    rec = alt;
                }
            }
        }
        return rec;

    }

    /**
     * If there is any task that lacks at least one resolvable call, generate an error and include all unresolvable
     * parameters so the user can result them with a holistic view rather having to fix/re-execute/find-problem/fix/etc.
     * 
     * @param badParams
     */
    private void throwUncompletableParams(List<Call.Param.Instance> badParams) {
        StringBuilder sb = new StringBuilder();
        sb.append("Call parameters have no matching task instance:\n");
        for (Call.Param.Instance param : badParams) {
            sb.append("    ");
            sb.append(param.getTypeName());
            sb.append(" in ");
            sb.append(param.getCall().getCall().signature());
            sb.append('\n');
        }
        throw new InvalidGraph.MissingDependents(sb.toString());
    }

    /**
     * Links a taskInstance to a parameter that expects it, also bumping the threshold on that parameter.
     * 
     * @param taskInstance
     * @param paramInstance
     */
    private boolean backLink(DataFlowSource.Instance taskInstance, Param.Instance paramInstance) {
        System.out.println("backLink " + taskInstance + "-->"+paramInstance);
        // TBD comment
        // The relationship might already have been established because we link incoming *and*
        // outgoing in the same batch, i.e. between an "A" and a "B(A)" this method is
        // called twice but should only be counted once.
        for (Param.Instance next: taskInstance.backList) {
            if (next.getCall() == paramInstance.getCall()) {
                return false;
            }
        }
        taskInstance.backList.add(paramInstance);
        paramInstance.incoming.add(taskInstance);
        paramInstance.bumpThreshold();
        Task.Instance backTaskInstance = paramInstance.getCall().taskInstance;
        if (backTaskInstance.wait) {
            waitForTasks.add(backTaskInstance);
        }
        return true;
    }

    /**
     * Computes the completion threshold for each task/call instance. Must be called for each TaskInstance during
     * initialization; although this method is invoked recursively that recursion calculation is only done once per task
     * instance.
     * 
     * @param taskInstance
     * @param level should be unique per call
     * @param toBeProvided possibly null map of pojo task classes and the tasks that will
     *            {@link com.ebay.bascomtask.main.ITask#provides(Class)} them
     * @return how many times task must be invoked before it can be considered complete
     */
    private int computeThreshold(Task.Instance taskInstance, int level,
            final Map<Class<?>, Task.Instance> toBeProvided) {
        return computeThreshold(taskInstance,level,taskInstance,toBeProvided);
    }

    private int computeThreshold(
            final DataFlowSource.Instance base, 
            final int level, 
            final DataFlowSource.Instance dataFlowSource,
            final Map<Class<?>, 
            Task.Instance> toBeProvided) {
            boolean hasCalls = false;        
        if (dataFlowSource.recomputeForLevel(level)) {
            int tc = 0;
            for (Call.Instance nextCall : dataFlowSource.calls()) {
                hasCalls = true;
                int cc = 1;
                // Allowing for provides() tasks means we can't count on setting
                // thresholds based on existing instances, because
                // for a provides() task by definition we don't have an instance
                // available. This boolean is used to recognize calls
                // that would otherwise appear to not be completeable but in
                // fact are if any missing instances will be provided in
                // a nested task.
                boolean ccComplete = true;
                for (Call.Param.Instance nextParam : nextCall) {
                    int pc = 0;
                    if (nextParam.getParam().accumulate()) {
                        pc = 1;
                    }
                    else if (isInjectable(nextParam)) {
                        pc = 1;
                    }
                    else {
                        for (DataFlowSource.Instance nextSourceInstance : nextParam.incoming) {
                            if (base == nextSourceInstance) {
                                throw new InvalidGraph.Circular(
                                        "Circular reference " + dataFlowSource.getShortName() + " and " + base.getShortName());
                            }
                            DataFlowSource.Instance completableSource = nextSourceInstance.getCompletableSource();
                            pc += computeThreshold(base,level,completableSource,toBeProvided);
                        }
                    }
                    cc *= pc;
                    if (pc == 0) {
                        Task.Instance providingTask = toBeProvided == null ? null
                                : toBeProvided.get(nextParam.getTask().producesClass);
                        if (providingTask == null) {
                            ccComplete = false;
                        }
                        else {
                            Param.Instance hiddenParamInstance = nextCall.addHiddenParameter(providingTask.getTask());
                            backLink(providingTask,hiddenParamInstance);
                        }
                    }
                }
                nextCall.setCompletionThreshold(cc);
                tc += cc;
                if (cc == 0 && ccComplete) {
                    // When a call is not immediately completable but can be
                    // with provides(), flag this here so that task
                    // does not get rejected as uncompletable
                    dataFlowSource.setForceCompletable();
                }
            }
            if (dataFlowSource.setCompletionThreshold(tc)) {
                Task.Instance taskInstance = dataFlowSource.getTaskInstance();
                if (taskInstance.wait) {
                    // Ensure that a task that may have already completed is
                    // added back into wait list
                    waitForTasks.add(taskInstance);
                }
            }
        }
        int result = dataFlowSource.getCompletionThreshold();
        // Always return at least 1, so that dependent tasks thresholds will be computed properly.
        return Math.max(1,result);
    }

    /**
     * Exceptions are stored in member variable that may be added to by the main or spawned threads. Pick them up here
     * and propagate.
     */
    private synchronized void checkForExceptions() {
        if (exceptions != null) {
            try {
                rollback();
            }
            catch (Exception e) {
                exceptions.add(new RuntimeException("Durinng rollback",e));
            }
            int nx = exceptions.size();
            if (nx > 0) {
                Exception e = exceptions.get(0);
                if (nx > 1) {
                    throw new RuntimeGraphError.Multi(e,exceptions);
                }
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Force rollback. This gets invoked automatically when a task throws an exception, but clients may also force the
     * initiation of the rollback sequence after {@link #execute()} completes, if desired.
     * <p>
     * During rollback, any {@literal @}Rollback method on a completed task will be invoked, such that any downstream
     * tasks that were directly or indirectly dependent on this task will have had their {@literal @}Rollback methods
     * invoked first.
     */
    public void rollback() {
        if (rollback != null) {
            rollback.execute();
        }
    }

    /**
     * Validates that for each of the provided() classes in the given task instance, a matching instance was actually
     * provided
     * 
     * @param taskInstance that has a possibly null provided list
     * @throws InvalidGraph.ViolatedProvides if not
     */
    void validateProvided(Task.Instance taskInstance) {
        List<Class<?>> exp = taskInstance.getProvides();
        if (exp != null) {
            List<Task.Instance> got = nestedAdds.get(Thread.currentThread());
            outer: for (Class<?> nextExp : exp) {
                if (got != null) {
                    for (Task.Instance nextGot : got) {
                        if (nextGot.getTask().producesClass == nextExp) {
                            continue outer;
                        }
                    }
                }
                throw new InvalidGraph.ViolatedProvides("Task of type " + nextExp + " not provided");
            }
        }
    }

    /**
     * Unlike other threads, the main task may have to wait for others to complete. As an optimization, while it is
     * waiting, another thread might give it work (rather than incur the cost of another thread startup while the main
     * thread sits idle).
     */
    private void waitForCompletion() {
        outer: while (true) {
            while (invocationPickup != null) {
                TaskMethodClosure inv = null;
                synchronized (this) {
                    if (invocationPickup != null) {
                        inv = invocationPickup;
                        invocationPickup = null;
                    }
                }
                if (inv != null) {
                    invokeAndFinish(inv,"redirect",true);
                }
            }
            synchronized (this) {
                do {
                    int wfc = waitForTasks.size();
                    if (wfc == 0) {
                        break outer;
                    }
                    int exs = exceptions == null ? 0 : exceptions.size();
                    if (exs > 0) {
                        if (threadBalance == 0) {
                            break outer;
                        }
                    }
                    else if (threadBalance == 0) { // No other active threads?
                        String msg = "this task";
                        if (wfc != 1)
                            msg = "these " + wfc + " tasks";
                        msg = "Stalled on " + msg + ": " + Arrays.toString(waitForTasks.toArray());
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Stalled graph state:\n{}",getGraphState());
                        }
                        throw new RuntimeGraphError.Stall(msg);
                    }
                    if (++waitLoopDebugCounter > 10) {
                        waitLoopDebugCounter = 0;
                        String taskPlural = wfc > 1 ? "s" : "";
                        String threadPlural = threadBalance > 1 ? "s" : "";
                        String singleTask = "";
                        if (LOG.isDebugEnabled() && wfc == 1) {
                            Iterator<Task.Instance> itr = waitForTasks.iterator();
                            singleTask = ": " + itr.next().toString();
                        }
                        LOG.debug("Waiting on {} task{} and {} thread{}{}",wfc,taskPlural,threadBalance,threadPlural,
                                singleTask);
                    }
                    try {
                        waiting = true;
                        this.wait(maxWaitTime);
                        checkForTimeout();
                    }
                    catch (InterruptedException e) {
                        throw new RuntimeGraphError("Unexpected interruption",e);
                    }
                    finally {
                        waiting = false;
                    }
                    if (invocationPickup != null) {
                        continue outer;
                    }
                }
                while (true);
            }
        }
    }

    private void checkForTimeout() {
        long now = System.currentTimeMillis();
        if (now > startTime + maxExecutionTimeMillis) {
            LOG.error("Timing out after {}ms, state:\n{}",maxExecutionTimeMillis,getGraphState());
            throw new RuntimeGraphError.Timeout(maxExecutionTimeMillis);
        }
    }

    synchronized boolean requestMainThreadComplete(TaskMethodClosure inv) {
        if (waiting && invocationPickup == null) {
            if (inv.getCallInstance().taskInstance.wait) { // Don't put nowait
                                                           // tasks on calling
                                                           // thread
                LOG.debug("Pushing to main thread: {}",inv);
                invocationPickup = inv;
                notifyAll();
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a newline-separated list of all calls of all tasks.
     * 
     * @return multi-line graph state summary
     */
    public String getGraphState() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Task.Instance taskInstance : allTasks) {
            for (Call.Instance callInstance : taskInstance.calls) {
                if (!first)
                    sb.append(",\n");
                first = false;
                sb.append("   ");
                sb.append(callInstance.hasCompleted() ? ' ' : '!');
                sb.append("  ");
                sb.append(callInstance.formatState());
                List<Exception> xs = taskInstance.getExecutionExceptions();
                if (xs != null) {
                    sb.append(" ");
                    for (Exception next : xs) {
                        sb.append(" FAILED: ");
                        sb.append(next.getClass().getSimpleName());
                        String msg = next.getMessage();
                        if (msg != null) {
                            sb.append("(\"");
                            sb.append(msg);
                            sb.append("\")");
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    private TaskMethodClosure fireRoots(List<Call.Instance> roots, TaskMethodClosure inv) {
        for (Call.Instance nextBackCallInstance : roots) {
            //propagateForward(null,nextBackCallInstance.taskInstance,null,inv);
            int[] freeze = nextBackCallInstance.startingFreeze;
            inv = nextBackCallInstance.crossInvoke(null,inv,freeze,null,-1,this,"root");
            //inv = nextBackCallInstance.crossInvoke(null,nextBackCallInstance.taskInstance,inv,freeze,null,-1,this,"root");
            //Call call = nextBackCallInstance.getCall();
            //inv = nextBackCallInstance.crossInvoke(null,call,inv,freeze,null,-1,this,"root");
        }
        return inv;
    }

    /**
     * Invokes a task method then follows up with any on-completion bookkeeping which may include recursively invoking
     * (or spawning new threads for) tasks that become ready as a result of the incoming call having completed.
     * 
     * @param closureToInvoke
     * @param context descriptive term for log messages
     */
    void invokeAndFinish(TaskMethodClosure closureToInvoke, String context, boolean fire) {
        checkForTimeout();
        // Don't invoke any more tasks if there are any pending exceptions
        if (closureToInvoke != null && this.exceptions == null) {
            Call.Instance callInstance = closureToInvoke.getCallInstance();
            Task.Instance taskOfCallInstance = callInstance.getTaskInstance();
            taskOfCallInstance.startOneCall();
            Task task = taskOfCallInstance.getTask();

            try {
                closureToInvoke.invoke(this,context,fire);
                setForRollBack(closureToInvoke);

                TaskRec rec = taskMapByType.get(task.producesClass);
                TaskMethodClosure parent = closureToInvoke.getParent();

                TaskMethodClosure originalClosureToInvoke = closureToInvoke;
                closureToInvoke = processPostExecution(rec,callInstance,taskOfCallInstance,closureToInvoke);
                if (originalClosureToInvoke.isEndPath() && config.isProfilerActive()) {
                    ProfilingTracker keeper = Orchestrator.stat();
                    // Invoked in this non-synchronized method so that any synchronization it does will not lead to race condition
                    keeper.record(this,originalClosureToInvoke);
                }
                invokeAndFinish(closureToInvoke,context,true);
                Object[] followArgs = callInstance.popSequential();
                if (followArgs != null) {
                    closureToInvoke = getTaskMethodClosure(parent,callInstance,followArgs,originalClosureToInvoke.getLongestIncoming());
                    invokeAndFinish(closureToInvoke,"follow",fire);
                }
            }
            catch (Exception e) {
                recordException(callInstance,e);
            }
        }
    }

    /**
     * Adds a task to be rolled back, if later needed. Also adds this task as needing to occur before previously
     * executed dependent tasks.
     * 
     * @param closureToInvoke that was invoked
     */
    private void setForRollBack(TaskMethodClosure closureToInvoke) {
        Call.Instance callInstance = closureToInvoke.getCallInstance();
        if (!isRollBack) {
            synchronized (this) {
                if (rollback == null) {
                    rollback = new Orchestrator(true);
                }
                ITask rollBackTask = rollback.add(closureToInvoke.getTargetPojoTask(),TaskMethodBehavior.ROLLBACK);
                Iterator<Call.Param.Instance> itr = callInstance.iterator();
                while (itr.hasNext()) {
                    Call.Param.Instance next = itr.next();
                    next.addBefore(rollBackTask);
                }
            }
        }
    }

    synchronized void spawn(final TaskMethodClosure invocation) {
        if (!requestMainThreadComplete(invocation)) {
            LOG.debug("Spawning \"{}\"",invocation);
            final Orchestrator lock = this;
            threadBalance++;
            ExecutorService executor = config.getExecutor();
            final Thread parent = Thread.currentThread();
            final TaskThreadStat parentStat = threadMap.get(parent);
            // Offsetting by 1 ensures that 0 is left to refer to thread that invokes execute()
            final int globalThreadIndex = threadsCreated.incrementAndGet() + 1;
            invocation.prepare();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    Thread currentThread = Thread.currentThread();
                    TaskThreadStat threadStat;
                    synchronized (threadMap) {
                        int localThreadIndex = threadMap.size();
                        threadStat = new TaskThreadStat(lock,localThreadIndex,globalThreadIndex,parentStat);
                        lock.setThreadStatForCurrentThread(threadStat);
                    }
                    config.notifyThreadStart(threadStat);
                    Exception err = null;
                    try {
                        invokeAndFinish(invocation,"spawned",true);
                    }
                    catch (Exception e) {
                        // The exception will have been recorded at a deeper level if thrown from a pojo task
                        // method; ensure it is recorded here in case it was generated outside a pojo method
                        recordException(e);
                        err = e;
                    }
                    finally {
                        int tb;
                        synchronized (lock) {
                            tb = --lock.threadBalance;
                            if (tb == 0) {
                                // This could possibly be overwritten if
                                // threadBalance is driven to
                                // zero more than once during an execution
                                lock.noWaitStats.endTime = System.currentTimeMillis();
                            }
                            lock.notifyAll();
                        }
                        if (LOG.isDebugEnabled()) {
                            String how = err == null ? "normally" : "with exception";
                            LOG.debug("Thread completed {} {} and returning to pool, {} open threads remaining",
                                    invocation,how,tb);
                        }
                        lock.threadMap.remove(currentThread);
                        config.notifyThreadEnd(threadStat);
                    }
                }
            });
        }
    }

    /**
     * After a task method has been invoked, look for more work, spawning more threads if there is more than one
     * ready-to-go non-light method (execute the light methods directly by this thread). The work here involves possibly
     * changing fields in this class and is therefore synchronized. However, call invocations are either spawned and or
     * one is provided as a return result to be executed after the lock on this class has been released. (TBD... light
     * tasks are executed while the lock is still held).
     */
    private synchronized TaskMethodClosure processPostExecution(TaskRec rec, Call.Instance callInstance,
            Task.Instance taskOfCallInstance, TaskMethodClosure firing) {
        long duration = firing.getDurationMs();
        noWaitStats.update(duration);
        if (callingThread != null) {
            waitStats.update(duration);
        }
        boolean complete = false;
        //fireTree(taskOfCallInstance.getTask(),firing);

        // Both taskInstance and callInstance need to be marked as completable,
        // but here we're only interested in whether the task has completed
        if (taskOfCallInstance.completeOneCall()) {
            complete = true;
            waitForTasks.remove(taskOfCallInstance);
        }
        callInstance.completeOneCall();

        List<Task.Instance> newTaskInstances = nestedAdds.remove(Thread.currentThread());
        TaskMethodClosure inv = null;
        if (newTaskInstances != null) {
            inv = executeTasks(newTaskInstances,"nested",null);
        }
        if (LOG.isDebugEnabled()) {
            int taskSize = taskOfCallInstance.backList.size();
            int callSize = callInstance.backList.size();
            String cmsg = complete ? "" : "in";
            String plural = (taskSize+callSize)!= 1 ? "s" : "";
            LOG.debug("On {}complete exit from {} eval task={}/method={} backLink{} ",cmsg,callInstance,taskSize,callSize,plural);
        }
        inv = propagateForward(callInstance,taskOfCallInstance,firing,inv);
        return inv;
    }

    private TaskMethodClosure propagateForward(Call.Instance callInstance, Task.Instance taskOfCallInstance,
            TaskMethodClosure firing, TaskMethodClosure inv) {
        inv = continueOrSpawn(taskOfCallInstance,taskOfCallInstance.targetPojo,firing,"back-task",inv);
        inv = continueOrSpawn(callInstance,firing.getOutput(),firing,"back-method",inv);
        return inv;
    }
    
    private void fireTree(DataFlowSource task, Binding binding) {
        for (Class<?> next: task.ancestry) {
            TaskRec rec = taskMapByType.get(next);
            rec.fired.add(binding);
        }
    }

    int getCountOfNewTasks() {
        List<Instance> nt = nestedAdds.get(Thread.currentThread());
        return nt == null ? 0 : nt.size();
    }

    private synchronized TaskMethodClosure continueOrSpawn(DataFlowSource.Instance dataFlowSource, Object output, TaskMethodClosure firing,
            String context, TaskMethodClosure inv) {
        Binding binding = new Binding(firing,output);
        fireTree(dataFlowSource.getSource(),binding);
        List<Call.Param.Instance> backList = dataFlowSource.backList;        
        if (backList.isEmpty()) {
            firing.setIsEndPath();
        }
        else {
            for (Call.Param.Instance nextBackParam : backList) {
                Call.Instance nextCallInstance = nextBackParam.callInstance;
                inv = nextCallInstance.bind(this,context,firing,binding,nextBackParam,inv);
            }
        }
        return inv;
    }
}
