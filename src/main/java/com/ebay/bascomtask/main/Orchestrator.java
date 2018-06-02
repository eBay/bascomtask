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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ebay.bascomtask.config.BascomConfigFactory;
import com.ebay.bascomtask.config.IBascomConfig;
import com.ebay.bascomtask.exceptions.InvalidGraph;
import com.ebay.bascomtask.exceptions.InvalidTask;
import com.ebay.bascomtask.exceptions.RuntimeGraphError;
import com.ebay.bascomtask.main.Call.Param;
import com.ebay.bascomtask.main.Task.TaskMethodBehavior;

/**
 * A dataflow-driven collector and executor of tasks that implicitly honors all 
 * dependencies while pursuing maximum parallelization with a minimum number of threads. 
 * <p>
 * A task can be any POJO which can have any number of {@literal @}Work or {@literal @}PassThru 
 * annotated methods, which we refer to as task methods.
 * Once added, {@link #execute()} will execute those methods either if they have no arguments
 * or once those arguments become available as a result of executing other tasks. A task method's 
 * arguments will never be null, so no null checks are required in task methods.
 * For any POJO added as a task, either its {@literal @}Work or its {@literal @}PassThru method(s) 
 * will be executed (never both), depending on whether it was added by {@link #addWork(Object)} 
 * or by {@link #addPassThru(Object)}. {@literal @}PassThru is intended for use when a
 * task needs to do only simple bookkeeping but otherwise is not actively contributing
 * to the overall result. Typically a given POJO will have just one {@literal @}Work and possibly
 * one {@literal @}PassThru method, but can have any number of these; at runtime, whichever method
 * matches based on available inputs will fire (execute). 
 * <p>
 * An example pojo invoked with BascomTask might be as follows:
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
 * If MyOtherTask and SomOtherTask have no common dependencies then they may be executed in
 * parallel in different threads. BascomTask is dataflow driven, attempting to execute tasks
 * in parallel where possible while avoiding wasteful creation of threads where possible.
 * <p>
 * Multiple instances of a given POJO class can also be added, each being executed separately and
 * each being supplied to all downstream task methods with that type as an argument.
 * If two instances of MyOtherTask were added to the previous example, each would start in its own thread and
 * MyTask.exec() would be invoked twice, each time with a different MyOtherTask instance.
 * The default behavior for a task that receives multiple calls is simply to allow them to proceed
 * independently each firing in turn to any downstream tasks. This assumes that the task (MyTask
 * in the above example) is thread-safe. There are several options for varying this behavior by
 * adding a scope argument to {@literal @}Work, see {@link com.ebay.bascomtask.annotations.Scope} for a description of options. Even simpler
 * is to simply change a task argument to a list, in which case all instances of that type will be
 * received at once.
 *  
 * @author brendanmccarthy
 */
public class Orchestrator {
	
	static final Logger LOG = LoggerFactory.getLogger(Orchestrator.class);
	
	private static class TaskRec {
		final List<Task.Instance> added = new ArrayList<>();  // Unique elements
		final List<Call.Instance.Firing> fired = new ArrayList<>();  // May have dups
	}

	/**
	 * How we know which task instances are active for a given Task, relative to this orchestrator
	 */
	private final Map<Task,TaskRec> taskMapByType = new HashMap<>();
	
	/*
	 * For performance we also maintain the list directly
	 */
	private final List<Task.Instance> allTasks = new ArrayList<>();
	
	/**
	 * Associates each user pojo task with its Task.Instance
	 */
	private final Map<Object,Task.Instance> pojoMap = new HashMap<>();

	/**
	 * To check for name conflicts
	 */
	private final Map<String,Task.Instance> taskMapByName = new HashMap<>();
	
	/**
	 * All Calls of all Task.Instances in this orchestrator
	 */
	private final Map<Call,List<Call.Instance>> callMap = new HashMap<>();
	
	/**
	 * All Parameters of all such calls in this orchestrator
	 */
	private final Map<Param,List<Param.Instance>> paramMap = new HashMap<>();
	
	/**
	 * Aggregates exceptions from sub-threads to roll-up to the main thread
	 */
	private List<Exception> exceptions = new ArrayList<>();

	/**
	 * A monotonically increasing counter
	 */
	private final AtomicInteger threadsCreated = new AtomicInteger(0);
	
	/**
	 * Number of spawned threads that have not yet terminated
	 */
	private int threadBalance = 0;
	
	/**
	 * How {@link #execute()} knows to exit: when this list is empty
	 */
	private final Set<Task.Instance> waitForTasks = new HashSet<>();
	
	/**
	 * Pending call set by a different thread, waiting for main thread to pick it up
	 */
	private Invocation invocationPickup = null;
	
	/**
	 * How a non-main thread knows the main thread's invocationPickup can be set
	 */
	private boolean waiting = false;
	
	/**
	 * Accumulates instances prior to execute() -- thread-specific to avoid conflicts
	 * between two separate threads dynamically adding tasks at the same time 
	 */
	private Map<Thread,List<Task.Instance>> nestedAdds = new HashMap<>();
	
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
	
	public static Orchestrator create() {
		return new Orchestrator();
	}
	
	/**
	 * Constructor hidden behind static create method in anticipation of
	 * future caching or other variations.
	 */
	private Orchestrator() {
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
	    
	    ExecutionStats() {}
	    ExecutionStats(ExecutionStats that) {
	        this.taskInvocationCount = that.taskInvocationCount;
	        this.accumulatedTaskTime = that.accumulatedTaskTime;
	        this.endTime = that.endTime;
	    }
	    
	    @Override
	    public boolean equals(Object o) {
	        if (this==o) return true;
	        if (o instanceof ExecutionStats) {
	            ExecutionStats that = (ExecutionStats)o;
	            if (this.getOrchestrator() != that.getOrchestrator()) return false;
	            if (this.taskInvocationCount != that.taskInvocationCount) return false;
	            if (this.accumulatedTaskTime != that.accumulatedTaskTime) return false;
	            if (this.endTime != that.endTime) return false;
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
	        return ("Ex(tasks="+taskInvocationCount+",par="+getParallelizationSaving()+",acc="+accumulatedTaskTime+",time="+getExecutionTime());
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
	     * @return count of number of tasks executed
	     */
	    public int getNumberOfTasksExecuted() {
	        return taskInvocationCount;
	    }
	    
	    /**
	     * Returns the time saved by executing tasks in parallel rather than sequentially.
	     * Note that if all tasks were sequential (due to ordering), then this value would
	     * be slightly negative, accounting for the overhead of the orchestrator itself.
	     * @return time saved in ms
	     */
	    public long getParallelizationSaving() {
	        return accumulatedTaskTime - getExecutionTime();
	    }
	    
	    /**
	     * Returns the time spent during the last call to {@link #execute()}. 
	     * The result is undefined is that call has not yet completed. Any uncompleted
	     * nowait tasks are not accounted for.
	     * @return execution time in ms
	     */
	    public long getExecutionTime() {
	        return endTime - startTime;
	    }
	}
	
	/**
	 * Returns a snapshot of execution statistics resulting from a previous execution, excluding any nowait tasks.
	 * This method is guaranteed to return the same result once the outermost execute() has completed.
	 * @return stats snapshot
	 */
	public ExecutionStats getStats() {
	    return new ExecutionStats(waitStats);
	}

	/**
	 * Returns a snapshot of execution statistics resulting from a previous execution, including any nowait tasks.
	 * This method is guaranteed to return the same result once the outermost execute() has completed
	 * <i>and</i> all nowait tasks have completed.
	 * @return stats snapshot
	 */
	public ExecutionStats getNoWaitStats() {
	    return new ExecutionStats(noWaitStats);
	}
	
	/**
	 * Sets the id of any thread spawned during execution by this orchestrator.
	 * Publicly exposed to allow for different configuration strategies.
	 * Must be invoked once thread is pulled from its pool.
	 */
	public void setThreadId() {
		Thread.currentThread().setName("BT:"+id+'#'+threadsCreated.incrementAndGet());
	}

	/**
	 * Returns the number of additional threads (not including the starting thread)
	 * used in computing the result from this orchestrator. The value may increase
	 * until all tasks are terminated.
	 * @return count of created threads
	 */
	public int getNumberOfThreadsCreated() {
		return threadsCreated.get();
	}

	/**
	 * Returns the number of threads that have been spawned but not yet completed.
	 * The returned value may only be &gt; 0 after a call to {@link #execute()} if
	 * there are nowait tasks.
	 * @return number of open threads
	 */
	public int getNumberOfOpenThreads() {
	    return threadBalance;
	}
	
	/**
	 * A convenience method, adds a task with a default name that is either
	 * active or passive depending on the given condition. 
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
	 * Adds a task to be made available to other task's task ({@literal @}Work or {@literal @}PassThru) methods, 
	 * and whose own {@literal @}Work methods will only be invoked (fired) when its task arguments have so fired.
	 * <p>
	 * The rules for execution are as follows:
	 * <ul>
	 * <li> A POJO can have no {@literal @}Work methods in which case it will instantly be available to other tasks.
	 * <li> Any {@literal @}Work method with no arguments will be started immediately in its own thread (which
	 * might be the calling thread of the orchestrator if it is not busy with other work).
	 * <li> Any {@literal Work} method with arguments will only be invoked when all of its task parameters have
	 * themselves completed with a proper return (see last point below).
	 * <li> Each {@literal @}Work method will be invoked with the cross-product of all available matching instances.
	 * </ul>
	 * <p>
	 * Regarding the last point, although it is common to add just one instance of a given POJO type,
	 * it is safe to add any number. The default behavior of {@literal @}Work methods receiving argument sequences
	 * with multiple instances is that each is executed potentially in parallel. Different options can be set through
	 * {@literal @}Work.scope, see {@link com.ebay.bascomtask.annotations.Scope}. Alternatively, a @{literal @}Work 
	 * method parameter can simply be a {@link java.util.List} of tasks in which case the entire set of matching 
	 * instances will be made available once all instances are available.
	 * <p>
	 * An added task will have no effect until {@link #execute(long)} is called, either directly or implicitly
	 * as a result of a nested task method completing.
	 * <p>
	 * A {@literal @}Work can return a boolean result or simply be void, which equates to returning {@code true}.
	 * A return value of {@code false} indicates that downstream tasks should not fire: any task fires only if
	 * all of its inputs have fired, except for {@link java.util.List} parameters, which never prevent firing
	 * but instead any false-returning tasks will be excluded from the list (which means that a list parameter 
	 * may be empty).
	 * 
     * @param task java POJO to add as task 
	 * @return a 'shadow' task object which allows for various customizations
	 */
	public ITask addWork(Object task) {
		return add(task,TaskMethodBehavior.WORK);
	}
	

	/**
	 * Adds a task whose {@literal @}PassThru methods (rather than its {@literal @}@Work methods) will 
 	 * be invoked, always in the calling thread -- no separate thread is created to execute a {@literal @}PassThru
 	 * method since it assumed to perform simple actions like providing a default or passing-through its arguments
 	 * with no or little change. Otherwise behaves the same as {@link #addWork(Object)}.
	 * @param task java POJO to add as task 
	 * @return a 'shadow' task object which allows for various customizations
	 */
	public ITask addPassThru(Object task) {
		return add(task,Task.TaskMethodBehavior.PASSTHRU);
	}
	
	/**
	 * Adds an object without considering any {@literal @}Work or {@literal @}PassThru methods even if they
	 * exist, as if the object did not have such methods in the first place. This enables the object to be
	 * make available as a parameter to other task methods without the object's task methods firing. Usage of 
	 * this method is relatively uncommon, but does provide another way to handle variant situations be 
	 * allowing a task object to be exposed but have its behavior managed outside the scope of an orchestrator.
	 * @param task to add
	 * @return a 'shadow' task object which allows for various customizations
	 */
	public ITask addIgnoreTaskMethods(Object task) {
		return add(task,Task.TaskMethodBehavior.NONE);
	}

	/**
	 * Records an added task to be added during a subsequent execute(), whether the latter is explicit
	 * or implied as a result of a nested inline call. We don't add right away because of the possibility
	 * of other active threads. The actual addition is only performed when we can synchronize the 
	 * orchestrator and perform the additions atomically without impacting running threads.
	 * @param targetTask java POJO to add as simple task 
	 * @param workElsePassThru
	 * @return
	 */
	private ITask add(Object targetTask,  Task.TaskMethodBehavior taskMethodBehavior) {
		Class<?> targetClass = targetTask.getClass();
		Task task = TaskParser.parse(targetClass);
		Task.Instance taskInstance = task.new Instance(this,targetTask,taskMethodBehavior);
		Thread t = Thread.currentThread();
		synchronized (nestedAdds) {
			List<Task.Instance> taskInstances = nestedAdds.get(t);
			if (taskInstances==null) {
				taskInstances = new ArrayList<>();
				nestedAdds.put(t,taskInstances);
			}
			taskInstances.add(taskInstance);
		}
		return taskInstance;
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
	 * @param name
	 */
	void checkPendingTaskInstanceInstanceName(String name) {
		checkUniqueTaskInstanceName(name);
		List<Task.Instance> pending = nestedAdds.get(Thread.currentThread());
		for (Task.Instance nextTaskInstance: pending) {
			if (name.equals(nextTaskInstance.getName())) {
				throw new InvalidTask.NameConflict("Task name\"" + name + "\" already in use");
			}
		}
		// There still might be a conflict when we add nestedAdds to the graph, but we can't check
		// that until that actually occurs due the way names are auto-generated.
	}
	
	private void checkUniqueTaskInstanceName(String name) {
		if (taskMapByName.get(name) != null) {
			throw new InvalidTask.NameConflict("Task name\"" + name + "\" already in use");
		}
	}
	
	
	/**
	 * Calls {@link #execute(long)} with a default of 10 minutes.
	 */
	public void execute() {
		execute(TimeUnit.MINUTES.toMillis(10L));
	}

	/**
	 * Begins processing of previously added tasks, spawning new threads where possible to exploit 
	 * parallel execution. This method returns when all no-wait tasks are finished. Conversely, 
	 * a task <i>will</i> hold up the exit of this method when all of the following apply: 
	 * <ol>
	 * <li> It has <i>not</i> been flagged as no-wait
	 * <li> It has at least one {@literal @}Work (or {@literal @}PassThru) method
	 * <li> All of its methods that can fire (because there are matching instances as parameters) have fired
	 * <li> It has at least one such fireable method that may fire again
	 * </ol>
	 * This method can safely be called multiple times, each time accounting for any new tasks added and firing
	 * all tasks that are firable as a result of those new tasks added. In this way, each call to execute acts
	 * like a synchronizer across all executable tasks. Tasks added within a nested task can but do not have
	 * to call execute() if they add tasks, as there is an implicit call done automatically in this case.
	 * <p>
	 * Consistency checks are performed prior to any task being started, and if a violation is found a subclass
	 * of InvalidGraph is thrown.
	 * <p>
	 * If any task throws an exception, which would have to be a non-checked exception, it will be propagated 
	 * from this call. Once such an exception is thrown, the orchestrator ceases to start new tasks and instead
	 * waits for all open threads to finish before returning (by throwing the exception in question). Currently,
	 * only the first exception is thrown if there happens to be more than one.
	 * @param maxExecutionTimeMillis to timeout
	 * @throws RuntimeException generated from a task
	 * @throws RuntimeGraphError.Timeout when the requested timeout has been exceeded
	 * @throws InvalidGraph.MissingDependents if a task cannot be exited because it has no mathcing {@literal @}Work dependents
	 * @throws InvalidGraph.Circular if a circular reference between two tasks is detected
	 * @throws InvalidGraph.MultiMethod if a task has more than one callable method and is not marked multiMethodOk()
	 * (or {@literal @}PassThru) method that has all of its parameters available as instances
	 */
	public void execute(long maxExecutionTimeMillis) {
		final Thread t = Thread.currentThread();
		List<Task.Instance> taskInstances = nestedAdds.remove(t);
		if (callingThread != null) {
			executeTasks(taskInstances,"nested");
		}
		else {
			this.maxExecutionTimeMillis = maxExecutionTimeMillis;
			this.maxWaitTime = Math.min(maxExecutionTimeMillis,500);
			startTime = System.currentTimeMillis();
			callingThread = t;
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
	}
	
	private void executeTasks(List<Task.Instance> taskInstances, String context) {
		Invocation inv = executeTasks(taskInstances,context,null);
		invokeAndFinish(inv,"own",true);
	}
	
	private synchronized Invocation executeTasks(List<Task.Instance> taskInstances, String context, Invocation inv) {
		if (taskInstances != null) {
			int currentTaskCount = allTasks.size();
			List<Call.Instance> roots = linkGraph(taskInstances);
			if (roots.size() > 0) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("firing {} with {}->{} tasks / {} roots\n{}",context,currentTaskCount,allTasks.size(),roots.size(),getGraphState());
				}
				inv = fireRoots(roots,inv);
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
	 * Adds the given tasks and links each parameter of each applicable (@Work or @PassThru) call with each instance 
	 * of that parameter's type. Also verifies that all tasks are callable, by ensuring that they have at least one 
	 * such call that has all parameters available.
	 * @throws InvalidGraph if any task is un-callable, and if so include list of all such tasks (if more than one)
	 * @return non-null but possibly empty list of instances ready to fire
	 */
	private List<Call.Instance> linkGraph(List<Task.Instance> taskInstances) {
		// First establish all references from the orchestrator to taskInstances.
		// Later steps depend on these references having been set.
		for (Task.Instance taskInstance: taskInstances) {
			addActual(taskInstance);
		}
		// Ensure explicitBeforeDependencies are set on all taskInstances so that we
		// can update thresholds in the next step
		for (Task.Instance taskInstance: taskInstances) {
		    taskInstance.updateExplicitDependencies(pojoMap);
		}
		List<Call.Instance> roots = new ArrayList<>();
		// Now the taskInstances themselves can be linked
		for (Task.Instance taskInstance: taskInstances) {
			if (taskInstance.calls.size()==0) {
				// If no calls at all, the task is trivially available to all who depend on it.
				// We create a dummy call so the task can then be processed like any other root.
				roots.add(taskInstance.genNoCall());
				// Since this task is already available as a parameter, no point in having any
				// explicit dependencies on it, should there be any
				// TODO thread-safe dynamic explicits
				taskInstance.explicitBeforeDependencies = null;
			}
			else {
				linkAll(taskInstance,roots);
			}
		}
		// With all linkages in place, thresholds can be (re)computed, and final verification performed
		List<Call.Param.Instance> badParams = recomputeAllThresholdsAndVerify();
		if (badParams != null) {
			throwUncompletableParams(badParams);
		}
		return roots;
	}

    private void addActual(Task.Instance taskInstance) {
		allTasks.add(taskInstance);
		pojoMap.put(taskInstance.targetPojo,taskInstance);
		recordCallsAndParameterInstances(taskInstance);
		Task task = taskInstance.getTask();
		TaskRec rec = taskMapByType.get(task);
		if (rec==null) {
			rec = new TaskRec();
			taskMapByType.put(task,rec);
		}
		taskInstance.setIndexInType(rec.added.size());
		String tn = taskInstance.getName();
		checkUniqueTaskInstanceName(tn);
		taskMapByName.put(tn,taskInstance);
		rec.added.add(taskInstance);
	}
	
	private void recordCallsAndParameterInstances(Task.Instance task) {
		for (Call.Instance callInstance: task.calls) {
			Call call = callInstance.getCall();
			List<Call.Instance> callInstances = callMap.get(call);
			if (callInstances==null) {
				callInstances = new ArrayList<>();
				callMap.put(call,callInstances);
			}
			for (Param.Instance nextParamInstance: callInstance.paramInstances) {
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
	 * @return possibly null list of bad parameters
	 */
	private List<Call.Param.Instance> recomputeAllThresholdsAndVerify() {
		linkLevel++;
		List<Call.Param.Instance> badParams = null;
		for (Task.Instance nextTaskInstance: allTasks) {
			computeThreshold(nextTaskInstance,linkLevel);
			if (nextTaskInstance.calls.size() > 0) {  // Don't do the enclosed checks if there are no calls
				// If at least one call for a task has all parameters, the graph is resolvable.
				// If there is no such call, report all unbound parameters.
				if (!nextTaskInstance.isCompletable()) {
					if (badParams == null) {
						badParams = new ArrayList<>();
					}
					boolean any = false;
					for (Call.Instance nextCall: nextTaskInstance.calls) {
						for (Param.Instance nextParam: nextCall.paramInstances) {
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
	 * Links a taskInstance together with any incoming tasks or outgoing parameters
	 * @param taskInstance to link
	 * @param roots to add to for each call of each of the supplied taskInstances that is immediately ready to fire
	 */
	private void linkAll(Task.Instance taskInstance, List<Call.Instance> roots) {
		int matchableCallCount = 0;
		Map<Task,List<Task.Instance>> explicitsBefore = taskInstance.groupBeforeDependencies();
		for (Call.Instance callInstance: taskInstance.calls) {
			boolean match = true;
			boolean root = true;
			for (int i=0; i < callInstance.paramInstances.length; i++) {
				Call.Param.Instance paramInstance = callInstance.paramInstances[i];
				TaskRec rec = enumerateDependentTaskParameters(paramInstance,explicitsBefore);
				if (rec==null) {
					match = root = false;
				}
				else {
					for (Task.Instance supplierTaskInstance: rec.added) {
						backLink(supplierTaskInstance,paramInstance);
					}
					int numberAlreadyFired = rec.fired.size();
					if (numberAlreadyFired==0) {
						root = false;
					}
					else if (paramInstance.getParam().isList && numberAlreadyFired < paramInstance.getThreshold()) {
						root = false;
					}
					// Add all tasks that have already fired
					callInstance.startingFreeze[i] = numberAlreadyFired;
					for (Call.Instance.Firing fired: rec.fired) {
						paramInstance.bindings.add(fired);
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
		// Although new taskInstances are linked above, this final pass ensures that taskInstance is
		// backlinked to any previously-existing instances.
		for (Param backParam: taskInstance.getTask().backList) {
			List<Param.Instance> paramInstances = paramMap.get(backParam);
			if (paramInstances != null) {
				// It will be null if it links e.g. to a passthru method but was added as a work task 
				for (Param.Instance nextParamInstance: paramInstances) {
				    if (!nextParamInstance.isExplicitlyWired()) {
				        backLink(taskInstance,nextParamInstance);
				    }
				}
			}
		}
	}

    private boolean addExplicitNonParameters(Task.Instance taskInstance, Map<Task, List<Task.Instance>> explicitsBefore) {
        boolean root = true;
        for (Entry<Task, List<Task.Instance>> next: explicitsBefore.entrySet()) {
            Task task = next.getKey();
            List<Task.Instance> befores = next.getValue();
        	TaskRec rec = taskMapByType.get(task);
        	for (Call.Instance call: taskInstance.calls) {
        	    Call.Param.Instance paramInstance = call.addHiddenParameter(task,befores);
        	    int sz = 0;
        	    for (Task.Instance before: befores) {
        	        backLink(before,paramInstance);
        	        for (Call.Instance.Firing fired: rec.fired) {
        	            if (fired.pojoCalled == before.targetPojo) {
        	                paramInstance.bindings.add(fired);
        	                sz++;
        	            }
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
	 * Produces a record of all the tasks upon which the given call parameter must wait, accounting for auto and explicit wiring.
	 * @param paramInstance of target call
	 * @param explicitsBefore containing explicitly-declared dependencies, and from which formal param matches will be removed
	 * @return
	 */
	private TaskRec enumerateDependentTaskParameters(Call.Param.Instance paramInstance, Map<Task, List<Task.Instance>> explicitsBefore) {
	    Task task = paramInstance.getTask();	    
        TaskRec rec = taskMapByType.get(task);
        if (explicitsBefore != null) {  // If no explicit parameters than all known instances are auto-wired
            List<Task.Instance> befores = explicitsBefore.get(task);
            if (befores != null) {
                TaskRec alt = new TaskRec();
                for (Task.Instance next: befores) {
                    alt.added.add(next);
                    explicitsBefore.remove(task);  // Not a 'hidden' param if it's an explicit formal param                    
                    for (Call.Instance.Firing firing: rec.fired) {
                        if (firing.pojoCalled == next.targetPojo) {
                            alt.fired.add(firing);
                        }
                    }
                }
                rec = alt;
            }
        }
        return rec;
    }

	/**
	 * If there is any task that lacks at least one resolvable call, generate an error
	 * and include all unresolvable parameters so the user can result them with a holistic
	 * view rather having to fix/re-execute/find-problem/fix/etc.
	 * @param badParams
	 */
	private void throwUncompletableParams(List<Call.Param.Instance> badParams) {
		StringBuilder sb = new StringBuilder();
		sb.append("Call parameters have no matching task instance:\n");
		for (Call.Param.Instance param: badParams) {
			sb.append("    ");
			sb.append(param.getTypeName());
			sb.append(" in ");
			sb.append( param.getCall().getCall().signature());
			sb.append('\n');
		}
		throw new InvalidGraph.MissingDependents(sb.toString());
	}

	/**
	 * Link a taskInstance to a parameter that expects it, also bumping the threshold on that parameter.
	 * @param taskInstance
	 * @param paramInstance
	 */
	private void backLink(Task.Instance taskInstance, Param.Instance paramInstance) {
		// The relationship might already have been established because we link incoming *and* 
		// outgoing in the same batch, i.e. between an "A" and a "B(A)" this method is
		// called twice but should only be counted once.
		if (!taskInstance.backList.contains(paramInstance)) {
			taskInstance.backList.add(paramInstance);
			paramInstance.incoming.add(taskInstance);
			paramInstance.bumpThreshold();
			Task.Instance backTaskInstance = paramInstance.getCall().taskInstance;
			if (backTaskInstance.wait) {
				waitForTasks.add(backTaskInstance);
			}
		}
	}
	
	/**
	 * Computes the completion threshold for each task/call instance. Must be called 
	 * for each TaskInstance during initialization; although this method is invoked
	 * recursively that recursion calculation is only done once per task instance.
	 * @param taskInstance
	 * @return
	 */
	private int computeThreshold(Task.Instance taskInstance, int level) {
		return computeThreshold(taskInstance, level, taskInstance);
	}
	private int computeThreshold(final Task.Instance base, final int level, final Task.Instance taskInstance) {
		if (taskInstance.recomputeForLevel(level)) {
			int tc = 0;
			for (Call.Instance nextCall: taskInstance.calls) {
				int cc = 1;
				for (Call.Param.Instance nextParam: nextCall) {
					int pc = 0;
					if (nextParam.getParam().isList) {
						pc = 1;
					}
					else {
					    for (Task.Instance nextTaskInstance: nextParam.incoming) {
					        if (base==nextTaskInstance) {
					            throw new InvalidGraph.Circular("Circular reference " + taskInstance.getName() + " and " + base.getName());
					        }
					        pc += computeThreshold(base,level,nextTaskInstance);
					    }
					}
					cc *= pc;
				}
				nextCall.setCompletionThreshold(cc);
				tc += cc;
			}
			if (taskInstance.setCompletionThreshold(tc)) {
				if (taskInstance.wait) {
					// Ensure that a task that may have already completed is added back into wait list
					waitForTasks.add(taskInstance);
				}
			}
		}
		int result = taskInstance.getCompletionThreshold();
		if (taskInstance.calls.size()==0) {
			// With no calls, the taskInstance has no threshold but we return a 1 here
			// to indicate that a thread will still go through this taskInstance and 
			// invoke dependents.
			result += 1;
		}
		return result;
	}
	
	
	/**
	 * Exceptions are stored in member variable that may be added to by the main
	 * or spawned threads. Pick them up here and propagate.
	 */
	private void checkForExceptions() {
		if (exceptions.size() > 0) {
			// TODO collect if more than one
			Exception e = exceptions.get(0);
			if (e instanceof RuntimeException) {
			    throw (RuntimeException)e;
			}
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Unlike other threads, the main task may have to wait for others
	 * to complete. As an optimization, while it is waiting, another
	 * thread might give it work (rather than incur the cost of another
	 * thread startup while the main thread sits idle).
	 */
	private void waitForCompletion() {
		outer: while (true) {
			while (invocationPickup != null) {
			    Invocation inv = null;
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
					if (wfc==0) {
						break outer;
					}
					int exs = exceptions.size();
					if (exs > 0) {
					    if (threadBalance==0) {
					        break outer;
					    }
					}
					else if (threadBalance==0) { // No other active threads? 
					    String msg = "this task";
					    if (wfc != 1) msg = "these " +  wfc + " tasks";
					    msg = "Stalled on " + msg + ": " + Arrays.toString(waitForTasks.toArray());
					    throw new RuntimeGraphError.Stall(msg);
					}
					LOG.debug("Waiting on {} tasks and {} threads",wfc,threadBalance);
					try {
						waiting = true;
						this.wait(maxWaitTime);
						long now = System.currentTimeMillis();
						if (now > startTime + maxExecutionTimeMillis) {
							LOG.error("Timing out after {}ms, state:\n{}",maxExecutionTimeMillis,getGraphState());
							throw new RuntimeGraphError.Timeout(maxExecutionTimeMillis);
						}
					} catch (InterruptedException e) {
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
		};
	}

	synchronized boolean requestMainThreadComplete(Invocation inv) {
		if (waiting && invocationPickup == null) {
		    if (inv.getCallInstance().taskInstance.wait) { // Don't put nowait tasks on calling thread
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
	 * @return multi-line graph state summary
	 */
	public String getGraphState() {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Task.Instance taskInstance: allTasks) {
			for (Call.Instance callInstance: taskInstance.calls) {
				if (!first) sb.append(",\n");
				first = false;
				sb.append("   ");
				sb.append(callInstance.hasCompleted() ? ' ' : '!');
				sb.append("  ");
				sb.append(callInstance.formatState());
			}
		}
		return sb.toString();
	}

	private Invocation fireRoots(List<Call.Instance> roots, Invocation inv) {
		for (Call.Instance nextBackCallInstance: roots) {
			//inv = nextBackCallInstance.bind(this, "TBD", null, -1, inv);
			int[] freeze = nextBackCallInstance.startingFreeze;
			inv = nextBackCallInstance.crossInvoke(inv, freeze, null, -1, this, "root");
		}
		return inv;
	}
	
	/**
	 * Invokes a task method then follows up with any on-completion bookkeeping
	 * which may include recursively invoking (or spawning new threads for) tasks 
	 * that become ready as a result of the incoming call having completed.
	 * @param inv
	 * @param context descriptive term for log messages
	 */
	void invokeAndFinish(Invocation inv, String context, boolean fire) {
		if (inv != null) {
			Call.Instance callInstance = inv.getCallInstance();
			Task.Instance taskOfCallInstance = callInstance.getTaskInstance();
			taskOfCallInstance.startOneCall();
			Call.Instance.Firing firing = inv.invoke(this,context,fire);
			Task task = taskOfCallInstance.getTask();
			TaskRec rec = taskMapByType.get(task);

			inv = processPostExecution(rec,callInstance,taskOfCallInstance,firing);
			invokeAndFinish(inv,context,true); // If inv not null, it will represent a call that can be fired
			Object[] followArgs = callInstance.popSequential();
			if (followArgs != null) {
				inv = new Invocation(callInstance,followArgs);
				invokeAndFinish(inv,"follow",true);
			}
		}
	}
	
	/*
	void removeFromWaitingList(Task.Instance taskInstance) {
		waitForTasks.remove(taskInstance);
	}
	*/

	synchronized void spawn(final Invocation invocation) {
		if (!requestMainThreadComplete(invocation)) {
			LOG.debug("Spawning \"{}\"",invocation);
			final Orchestrator lock = this;
			threadBalance++;
			final IBascomConfig config = BascomConfigFactory.getConfig();
			ExecutorService executor = config.getExecutor();
			final Thread parent = Thread.currentThread();
			executor.execute(new Runnable() {
				@Override
				public void run() {
					config.linkParentChildThread(lock,parent,Thread.currentThread());
					Exception err = null;
					try {
						invokeAndFinish(invocation,"spawned",true);
					}
					catch (Exception e) {
						err = e;
					}
					finally {
						int tb;
						synchronized(lock) {
							if (err != null) {
								lock.exceptions.add(err);
							}
							tb = --lock.threadBalance;
							if (tb==0) {
								// This could possibly be overwritten if threadBalance is driven to 
								// zero more than once during an execution
								lock.noWaitStats.endTime = System.currentTimeMillis();
							}
							lock.notifyAll();
						}
						if (LOG.isDebugEnabled()) {
							String how = err==null? "normally" : "with exception";
							LOG.debug("Thread completed {} {} and returning to pool, {} open threads remaining",invocation,how,tb);
						}
						config.unlinkParentChildThread(lock,parent,Thread.currentThread());
					}
				}
			});
		}
	}
	
	/**
	 * After a task method has been invoked, look for more work, spawning more threads if there is more than one ready-to-go
	 * non-light method (execute the light methods directly by this thread). The work here involves possibly changing fields
	 * in this class and is therefore synchronized. However, call invocations are either spawned and or one is provided as
	 * a return result to be executed after the lock on this class has been released. (TBD... light tasks are executed while
	 * the lock is still held).
	 * @param completed
	 */
	private synchronized Invocation processPostExecution(TaskRec rec, Call.Instance callInstance, Task.Instance taskOfCallInstance, Call.Instance.Firing firing) {
	    noWaitStats.update(firing.executionTime); 
	    if (callingThread != null) {
	        waitStats.update(firing.executionTime);
	    }
		boolean complete = false;
		rec.fired.add(firing);
		if (taskOfCallInstance.completeOneCall()) {
			complete = true;
			waitForTasks.remove(taskOfCallInstance);
		}
		List<Task.Instance> newTaskInstances = nestedAdds.remove(Thread.currentThread());
		Invocation inv = null;
		if (newTaskInstances != null) {
			inv = executeTasks(newTaskInstances,"nested",null);
		}
		List<Call.Param.Instance> backList = taskOfCallInstance.backList;
		String cmsg = complete?"":"in";
		LOG.debug("On {}complete exit from {} eval {} backLinks ",cmsg,callInstance,backList.size());
		inv = continueOrSpawn(taskOfCallInstance,firing,"back",inv);
		return inv;
	}

	private synchronized Invocation continueOrSpawn(Task.Instance taskInstance, Call.Instance.Firing firing, String context, Invocation inv) {
		List<Call.Param.Instance> backList = taskInstance.backList;
		for (Call.Param.Instance nextBackParam: backList) {
			Call.Instance nextCallInstance = nextBackParam.callInstance;
			Object actualParam = taskInstance.targetPojo;
			inv = nextCallInstance.bind(this,context,actualParam,firing,nextBackParam,inv);
		}
		return inv;
	}
}

