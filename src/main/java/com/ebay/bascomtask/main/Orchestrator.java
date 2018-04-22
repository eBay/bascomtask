package com.ebay.bascomtask.main;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

/**
 * A dataflow-driven collector and executor of tasks that implicitly honors all 
 * dependencies while pursuing maximum parallelization with a minimum number of threads. 
 * <p>
 * A task can be any POJO which can have any number of @Work or @PassThru annotated methods.
 * Once added, {@link #execute()} will execute those methods either if they have no arguments
 * or once those arguments become available as a result of executing other tasks. For
 * any POJO added as a task, either its @Work or its @PassThru method(s) will be
 * executed (never both), depending on whether it was added by {@link #addWork(Object)} 
 * or by {@link #addPassThru(Object)}. @PassThru is intended for use when a
 * task needs to do only simple bookkeeping but otherwise is not actively contributing
 * to the overall result. Commonly, a given POJO will have just one @Work and possibly
 * one @PassThru method, but can have any number of these; at runtime, whichever method
 * matches based on available inputs will fire (execute). 
 * <p>
 * A BascomTask hello world is simply as follows:
 * <p> 
 * <pre>
 * class HelloWorldTask {
 *   {@literal @}Work void exec() {
 *      System.out.println("Hello world");
 *   }
 * }
 * Orchestrator orc = Orchestrator.create();
 * orc.addWork(new HelloWorldTask());
 * orc.execute();
 * </pre>
 * If split across two tasks it could be thus:
 * <pre>
 * class HelloTask {
 *   String value;
 *   {@literal @}Work void exec() {
 *      value = "Hello";
 *   }
 * }
 * class WorldTask {
 *   {@literal @}Work void exec(HelloTask task) {
 *      System.out.println(task.value + " World");
 *   }
 * }
 * Orchestrator orc = Orchestrator.create();
 * orc.addWork(new HelloTask());
 * orc.addWork(new WorldTask());
 * orc.execute();
 * </pre>
 * In the example above, there is no opportunity for parallelism so both tasks would be executed in the
 * calling thread. In the following example, HelloTask and WorldTask can be executed in parallel so one
 * of them would be spawned in a separate thread:
 * <pre>
 * class HelloTask {
 *   String value;
 *   {@literal @}Work void exec() {
 *      value = "Hello";
 *   }
 * }
 * class WorldTask {
 *   String value;
 *   {@literal @}Work void exec() {
 *      value = "World";
 *   }
 * }
 * class CombinerTask {
 *   {@literal @}Work void exec(HelloTask helloTask, WorldTask worldTask) {
 *      System.out.println(helloTask.value + " " + worldTask.value);
 *   }
 * }
 * Orchestrator orc = Orchestrator.create();
 * orc.addWork(new HelloTask());
 * orc.addWork(new WorldTask());
 * orc.addWork(new CombinerTask());
 * orc.execute();
 * </pre>
 * 
 * <p>
 * Multiple instances of a given POJO class can also be added, each being executed separately and
 * each being supplied to all downstream @Work or @Passthru methods with that type as an argument.
 * If two HelloTasks were added to the previous example, each would start in its own thread and
 * CombinerTask.exec() would be invoked twice, each time with a different HelloTask instance.
 * The default behavior for a task that receives multiple calls is simply to allow them to proceed
 * independently each firing in turn to any downstream tasks. This assumes that the task (CombinerTask
 * in the above example) is thread-safe. There are several options for varying this behavior by
 * adding a scope argument to @Work, see {@link Scope} for a description of options. Even simplier
 * is to simply change a task argument to a list, then all instances of that type will be
 * received at once.
 *  
 * @author brendanmccarthy
 */
/**
 * @author bremccarthy
 *
 */
public class Orchestrator {
	
	static final Logger LOG = LoggerFactory.getLogger(Orchestrator.class);

	/**
	 * How we know which task instances are active for a given Task, relative to this orchestrator
	 */
	private final Map<Task,List<Task.Instance>> taskMapByType = new HashMap<>();
	
	/*
	 * For performance we also maintain the list directly
	 */
	private final List<Task.Instance> allTasks = new ArrayList<>();

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
	private long exitExecutionTime= 0;
	private long lastThreadCompleteTime = 0;
	
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
	
	/**
	 * Must be invoked once thread is pulled from its pool.
	 */
	public void setThreadId() {
		Thread.currentThread().setName(":"+id+'#'+threadsCreated.incrementAndGet());
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
	 * The returned value may only be > 0 after a call to {@link #execute()} if 
	 * there are nowait tasks.
	 * @return
	 */
	public int getNumberOfOpenThreads() {
		return threadBalance;
	}
	
	/**
	 * Return the time spent during the last call to {@link #execute()}. 
	 * The result is undefined is that call has not yet completed. Any uncompleted
	 * nowait tasks are not accounted for.
	 * @return
	 */
	public long getExecutionTimeWait() {
		return exitExecutionTime - startTime;
	}

	/**
	 * Like {@link #getExecutionTimeWait()} but in addition accounts for any
	 * nowait tasks; this result can change up until the time all nowait tasks
	 * are completed ({@link #getNumberOfOpenThreads()==0}).
	 * @return
	 */
	public long getExecutionTimeNoWait() {
		return lastThreadCompleteTime - startTime;
	}

	/**
	 * A convenience method, adds a task with a default name that is either
	 * active or passive depending on the given condition. 
	 * @param any java object
	 * @param cond if true then add as active else add as passive
	 */
	public void addConditionally(Object task, boolean cond) {
		if (cond) {
			addWork(task);
		}
		else {
			addPassThru(task);
		}
	}
	
	/**
	 * Adds a task to be made available to other task's task ({@literal @}Work or {@literal @}PassThru) methods, 
	 * and whose {@literal @}Work methods will only be invoked (fired) when its task arguments have so fired.
	 * <p>
	 * The rules for execution are as follows:
	 * <ul>
	 * <li> A POJO can have no {@literal @}Work methods in which case it will instantly be available to other tasks.
	 * <li> Any {@literal @}Work method with no arguments will be started immediately in its own thread (which
	 * might be the calling thread of the orchestrator if it is not busy with other work).
	 * <li> Any {@literal Work} method with arguments will only be invoked when its task parameters are
	 * ready and available.
	 * <li> Each {@literal @}Work method will in fact be invoked with the cross-product of all available matching
	 * instances.
	 * </ul>
	 * <p>
	 * Regarding the last point, although it is common to add just one instance of a given POJO type,
	 * it is safe to add any number. The default behavior of {@literal @}Work methods receiving argument sequences
	 * with multiple instances is that each is executed potentially in parallel. Different options can be set through
	 * {@literal @}Work.scope, see {@link com.ebay.bascomtask.annotations.Scope}. Alternatively, a @{literal @}Work 
	 * method parameter can simply be a list of tasks in which case the entire set of matching instances will
	 * be made available once all instances are available.
	 * 
	 * @param any java object
	 * @returns a 'shadow' task object which allows for various customization
	 */
	public ITask addWork(Object task) {
		return add(task,true);
	}
	

	/**
	 * Adds a task whose {@literal @}PassThru methods (rather than its {@literal @}@Work methods) will 
 	 * be invoked, always in the calling thread -- no separate thread is created to execute a {@literal @}PassThru
 	 * method since it assumed to perform simple actions like providing a default or passing-through its arguments
 	 * with no or little change. Otherwise behaves the same as {@link #addWork(Object)}.
	 * @param any java object
	 */
	public ITask addPassThru(Object task) {
		return add(task,false);
	}

	/**
	 * Records an added task to be added during a subsequent execute(), whether the latter is explicit
	 * or implied as a result of a nested inline call. We don't add right away because of the possibility
	 * of other active threads. The actual addition is only performed when we can synchronize the 
	 * orchestrator and perform the additions atomically without impacting running threads.
	 * @param targetTask
	 * @param workElsePassThru
	 * @return
	 */
	private ITask add(Object targetTask, boolean workElsePassThru) {
		Class<?> targetClass = targetTask.getClass();
		Task task = TaskParser.parse(targetClass);
		Task.Instance taskInstance = task.new Instance(this,targetTask,workElsePassThru);
		Thread t = Thread.currentThread();
		synchronized (nestedAdds) {
			List<Task.Instance> acc = nestedAdds.get(t);
			if (acc==null) {
				acc = new ArrayList<>();
				nestedAdds.put(t,acc);
			}
			acc.add(taskInstance);
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
	
	void setUniqueTaskInstanceName(Task.Instance taskInstance, String name) {
		if (taskMapByName.get(name) != null) {
			throw new InvalidTask.NameConflict("Task name\"" + name + "\" already in use");
		}
		taskMapByName.put(name,taskInstance);
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
	 * @param maxExecutionTimeMillis to timeout
	 * @throws RuntimeGraphError.Timeout when the requested timeout has been exceeded
	 * @throws InvalidGraph.MissingDependents if a task cannot be exited because it has no {@literal @}Work
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
				callingThread = null;
				exitExecutionTime = System.currentTimeMillis();
				if (lastThreadCompleteTime < exitExecutionTime) {
					lastThreadCompleteTime = exitExecutionTime;
				}
			}
		}
	}
	
	private void executeTasks(List<Task.Instance> taskInstances, String context) {
		if (taskInstances != null) {
			List<Call.Instance> roots = linkGraph(taskInstances);
			if (LOG.isDebugEnabled()) {
				LOG.debug("firing {} with {} tasks / {} roots\n{}",context,allTasks.size(),roots.size(),getGraphState());
			}
			fireRoots(roots);
		}
	}

	
	/**
	 * Thread which calls begin()
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
	 * @throws InvalidGraph if any task is uncallable, and if so include list of all such tasks (if more than one)
	 */
	private List<Call.Instance> linkGraph(List<Task.Instance> taskInstances) {
		// First establish all references from the orchestrator to taskInstances.
		// Later steps depend on these references having been set.
		for (Task.Instance taskInstance: taskInstances) {
			addActual(taskInstance);
		}
		List<Call.Instance> roots = null;
		// Now the taskInstances themselves can be linked
		for (Task.Instance taskInstance: taskInstances) {
			Call.Instance root = null;
			if (taskInstance.calls.size()==0) {
				// If no calls at all, the task is trivially available to all who depend on it.
				// We create a dummy call so the task can then be processed like any other root.
				root = taskInstance.genNoCall();
			}
			else {
				root = linkAll(taskInstance);
			}
			if (root != null) {
				if (roots == null) {
					roots = new ArrayList<>();
				}
				roots.add(root);
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
		recordCallsAndParameterInstances(taskInstance);
		Task task = taskInstance.getTask();
		List<Task.Instance> instances = taskMapByType.get(task);
		if (instances==null) {
			instances = new ArrayList<>();
			taskMapByType.put(task,instances);
		}
		taskInstance.setIndexInType(instances.size());
		instances.add(taskInstance);
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
	 * @return a no-arg call if there is one, else null
	 */
	private Call.Instance linkAll(Task.Instance taskInstance) {
		Call.Instance root = null;
		int matchableCallCount = 0;
		for (Call.Instance callInstance: taskInstance.calls) {
			if (callInstance.paramInstances.length == 0) {
				root = callInstance;
			}
			boolean match = true;
			for (Call.Param.Instance param: callInstance.paramInstances) {
				Task task = param.getTask();
				List<Task.Instance> instances = taskMapByType.get(task);
				if (instances==null) {
					match = false;
				}
				else {
					for (Task.Instance supplier: instances) {
						backLink(supplier,param);
					}
				}
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
		for (Param backParam: taskInstance.getTask().backList) {
			List<Param.Instance> paramInstances = paramMap.get(backParam);
			if (paramInstances != null) {
				// It will be null if it links e.g. to a passthru method but was added as a work task 
				for (Param.Instance nextParamInstance: paramInstances) {
					backLink(taskInstance,nextParamInstance);
				}
			}
		}
		return root;
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
				for (Call.Param.Instance nextParam: nextCall.paramInstances) {
					int pc = 0;
					if (nextParam.getParam().isList) {
						pc = 1;
					}
					else {
						Task task = nextParam.getTask();
						List<Task.Instance> tis = taskMapByType.get(task);
						if (tis != null) {
							for (Task.Instance nextTaskInstance: tis) {
								if (base==nextTaskInstance) {
									throw new InvalidGraph.Circular("Circular reference " + taskInstance.getName() + " and " + base.getName());
								}
								pc += computeThreshold(base,level,nextTaskInstance);
							}
						}
					}
					cc *= pc;
				}
				nextCall.setCompletionThreshold(cc);
				tc += cc;
			}
			taskInstance.setCompletionThreshold(tc);
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
				Invocation inv = invocationPickup;
				invocationPickup = null;
				invokeAndFinish(inv,"redirect");
			}
			synchronized (this) {
				do {
					int wfc = waitForTasks.size();
					LOG.debug("Waiting on {} tasks",wfc);
					if (wfc==0) {
						break outer;
					}
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
			LOG.debug("Pushing to main thread: {}",inv);
			invocationPickup = inv;
			notify();
			return true;
		}
		return false;
	}
	
	/**
	 * Returns a newline-separated list of all calls of all tasks.
	 * @return
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
				sb.append(callInstance.formatState(null));
			}
		}
		return sb.toString();
	}

	private void fireRoots(List<Call.Instance> roots) {
		Invocation inv = spawnAllButOne(roots,-1,null,null);
		
		if (inv != null) {
			invokeAndFinish(inv,"own-root");
		}
	}
	
	/**
	 * Invokes a test method then follows up with any on-completion bookkeeping
	 * which may include recursively invoking (or spawning new threads for) tasks 
	 * that become ready as a result of the incoming call having completed.
	 * @param inv
	 * @param context descriptive term for log messages
	 */
	void invokeAndFinish(Invocation inv, String context) {	
		Call.Instance completedCall = inv.getCallInstance();
		Task.Instance taskOfCompletedCall = completedCall.getTaskInstance();
		taskOfCompletedCall.startOneCall();
		boolean complete = false;
		boolean fire = inv.invoke(this,context);
		synchronized (this) {
			if (taskOfCompletedCall.completeOneCall()) {
				complete = true;
				waitForTasks.remove(taskOfCompletedCall);
			}
			List<Task.Instance> newTaskInstances = nestedAdds.remove(Thread.currentThread());
			if (newTaskInstances != null) {
				executeTasks(newTaskInstances,"nested");
			}
		}
		LOG.debug("Evaluating {} exit on {} {} backLinks",fire,completedCall,taskOfCompletedCall.backList.size());
		if (fire) {
			finishWork(inv,fire);
		} 
		else if (complete) {
			//LOG.debug("Completed task \"{}\" checking {} back params",completedCall.taskInstance.getName(),back.size());
			//Call.Instance callInstance = inv.getCallInstance();
			//taskOfCompletedCall.propagateComplete(callInstance);
			taskOfCompletedCall.propagateComplete();
			//propagateComplete(inv.getCallInstance());
		}
		Object[] followArgs = completedCall.popSequential();
		if (followArgs != null) {
			inv = new Invocation(completedCall,followArgs);
			invokeAndFinish(inv,"follow");
		}
	}
	
	void removeFromWaitingList(Task.Instance taskInstance) {
		waitForTasks.remove(taskInstance);
	}

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
						invokeAndFinish(invocation,"spawned");
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
								lock.lastThreadCompleteTime = System.currentTimeMillis();
							}
							lock.notify();
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
	 * After a task method has been invoked, look for more work, 
	 * spawning more threads if there is more than one ready-to-go
	 * non-light method (execute the light methods directly by this thread).
	 * @param completed
	 */
	private void finishWork(Invocation mine, boolean fire) {
		Call.Instance completedCall = mine.getCallInstance();
		Task.Instance taskInstance = completedCall.taskInstance;
		List<Call.Param.Instance> backList = taskInstance.backList;
		String compName = taskInstance.getName();
		LOG.debug("After firing task \"{}\" checking {} back params",compName,backList.size());
		mine = continueOrSpawn(taskInstance,"back",null);
		if (mine!=null && mine.ready()) {
			invokeAndFinish(mine,"on-finish");
		}
	}

	private synchronized Invocation continueOrSpawn(Task.Instance taskInstance, String context, Invocation inv) {
		List<Call.Param.Instance> backList = taskInstance.backList;
		for (Call.Param.Instance nextBackParam: backList) {
			Call.Instance nextCallInstance = nextBackParam.callInstance;
			int pos = nextBackParam.getParam().paramaterPosition;
			Object actualParam = taskInstance.targetPojo;
			inv = nextCallInstance.bind(this,context,actualParam,pos,inv);
		}
		return inv;
	}

	private Invocation spawnAllButOne(List<Call.Instance> cis, int pos, Object actualParamValue, Invocation inv) {
		for (Call.Instance nextBackCallInstance: cis) {
			inv = nextBackCallInstance.bind(this, "TBD", actualParamValue, pos, inv);
		}
		return inv;
	}
}

