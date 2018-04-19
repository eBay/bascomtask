package com.ebay.bascomtask.main;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ebay.bascomtask.annotations.Scope;
import com.ebay.bascomtask.config.BascomConfigFactory;
import com.ebay.bascomtask.config.IBascomConfig;
import com.ebay.bascomtask.exceptions.InvalidGraph;
import com.ebay.bascomtask.exceptions.InvalidTask;
import com.ebay.bascomtask.exceptions.RuntimeGraphError;

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
public class Orchestrator {
	
	static final Logger LOG = LoggerFactory.getLogger(Orchestrator.class);

	private final Map<Task,List<Task.Instance>> taskMapByType = new HashMap<>();
	private final List<Task.Instance> allTasks = new ArrayList<>();

	/**
	 * To check for name conflicts
	 */
	private final Map<String,Task.Instance> taskMapByName = new HashMap<>();
	
	/**
	 * A Call, in the context of an orchestrator can have many instances,
	 * one for each instance of the owning task.
	 */
	private final Map<Call,List<Call.Instance>> callMap = new HashMap<>();
	
	/**
	 * Calls that have no arguments. 
	 */
	private final List<Call.Instance> roots = new ArrayList<>();
	
	/**
	 * Tasks that have no methods -- these are immediately available to other
	 * calls as arguments without any method execution on those classes.
	 */
	private final List<Task.Instance> dolts = new ArrayList<>();
	
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
	private final List<Task.Instance> waitForTasks = new ArrayList<>();
	
	/**
	 * Pending call set by a different thread, waiting for main thread to pick it up
	 */
	private Invocation invocationPickup = null;
	
	/**
	 * How a non-main thread knows the main thread's invocationPickup can be set
	 */
	private boolean waiting = false;
	
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
		sb.append(",#roots=");
		sb.append(roots.size());
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
	 * Adds a task whose {@literal @}Work method(s) will be invoked, and which will in turn be supplied as an actual 
	 * parameter to other tasks with {@literal @}Work methods that match that parameter type. Note that:
	 * <ul>
	 * <li> A POJO can have no {@literal @}Work methods in which case it will instantly be available to other
	 * tasks. 
	 * <li> Any {@literal @}Work method with no arguments will be started immediately in its own thread (which
	 * might be the calling thread of the orchestrator if it is not busy with other work).
	 * <li> Any {@literal @}Work method with arguments will only be invoked when its task parameters are
	 * ready and available.
	 * <li> Each {@literal @}@Work method will in fact be invoked with the cross-product of all available matching
	 * instances.
	 * </ul>
	 * <p>
	 * Regarding the last point, although it is common to add just one instance of a given POJO type,
	 * it is safe to add any number. The default behavior of {@literal @}Work methods receiving argument sequences
	 * with multiple instances is that each is executed potentially in parallel. Different options can be set through
	 * {@literal @}Work.scope, see {@link com.ebay.bascomtask.annotations.Scope}. Alternatively, a {@literal @}Work
	 * parameter can simply be a list of tasks in which case the entire set of matching instances will
	 * be made available once all instances are available.
	 * 
	 * @param any java object
	 */
	public ITask addWork(Object task) {
		return add(task,true);
	}
	

	/**
	 * Adds a task whose {@literal @}PassThru methods (rather than its {@literal @}@Work methods) will 
 	 * be invoked. Otherwise behaves the same as {@link #addWork(Object)}.
	 * @param any java object
	 */
	public ITask addPassThru(Object task) {
		return add(task,false);
	}

	private ITask add(Object targetTask, boolean activeElsePassive) {
		Class<?> targetClass = targetTask.getClass();
		Task task = TaskParser.parse(targetClass);
		List<Task.Instance> tasksForType = taskMapByType.get(task);
		if (tasksForType==null) {
			tasksForType = new ArrayList<>();
			taskMapByType.put(task,tasksForType);
		}
		int ixt = tasksForType.size();
		Task.Instance taskInstance = task.new Instance(this,targetTask,activeElsePassive,ixt);
		taskMapByName.put(taskInstance.getName(),taskInstance);
		tasksForType.add(taskInstance);
		allTasks.add(taskInstance);

		inflate(taskInstance,activeElsePassive ? task.workCalls : task.passThruCalls);
		
		// Exclude from the the waitlist tasks that have no methods because they would never be 
		// invoked. Note that this check is done *after* inflate() so that taskInstance.calls is populated. 
		if (taskInstance.calls.size() > 0) {
			waitForTasks.add(taskInstance);
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
	
	
	private void inflate(Task.Instance task, List<Call> calls) {
		if (calls.size()==0) {
			dolts.add(task);
		}
		for (Call call: calls) {
			Call.Instance ci = call.new Instance(task);
			task.calls.add(ci);
			List<Call.Instance> callInstances = callMap.get(call);
			if (callInstances==null) {
				callInstances = new ArrayList<>();
				callMap.put(call,callInstances);
			}
			callInstances.add(ci);
			if (call.getNumberOfParams()==0) {
				roots.add(ci);
			}
		}
	}
	
	/**
	 * Calls {@link Orchestrator#execute(long)} with a default of 10 minutes.
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
		this.maxExecutionTimeMillis = maxExecutionTimeMillis;
		this.maxWaitTime = Math.min(maxExecutionTimeMillis,500);
		startTime = System.currentTimeMillis();
		callingThread = Thread.currentThread();
		try {
			linkTasksBackToWaitingParams();
			if (LOG.isDebugEnabled()) {
				LOG.debug("firing with {}/{}/{} tasks\n{}",allTasks.size(),roots.size(),dolts.size(),getGraphState());
			}
			fireRoots();
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
	
	/**
	 * Thread which calls begin()
	 */
	private Thread callingThread = null;
	boolean isCallingThread() {
		return Thread.currentThread() == callingThread;
	}
	
	/**
	 * Links each parameter of each applicable (@Work or @PassThru) call with each instance of that parameter's 
	 * type. Also verifies that all tasks are callable, by ensuring that they have at least one such call that 
	 * has all parameters available.
	 * @throws InvalidGraph if any task is uncallable, and if so include list of all such tasks (if more than one)
	 */
	private void linkTasksBackToWaitingParams() {
		List<Call.Param.Instance> badParams = null;
		for (Task.Instance taskInstance: allTasks) {
			computeThreshold(taskInstance);
			if (taskInstance.calls.size() > 0) {  // Don't do the enclosed checks if there are no calls
				List<Call.Param.Instance> badCallParams = null;
				// If at least one call for a task has all parameters, the graph is resolvable.
				// If there is no such call, report all unbound parameters.
				int badCallCount = 0;
				for (Call.Instance callInstance: taskInstance.calls) {
					for (Call.Param.Instance param: callInstance.paramInstances) {
						Task task = param.getTask();
						List<Task.Instance> instances = taskMapByType.get(task);
						if (instances==null) {
							badCallCount++;
							if (badCallParams==null) {
								badCallParams = new ArrayList<>();
							}
							badCallParams.add(param);
						}
						else {
							int np = instances.size();
							param.setThreshold(np);
							for (Task.Instance supplier: instances) {
								supplier.backList.add(param);
							}
						}
					}
				}
				int goodCallCount = taskInstance.calls.size() - badCallCount;
				if (goodCallCount==0) {
					// badCallParams will be non-null
					if (badParams==null) {
						badParams = badCallParams;
					}
					else {
						badParams.addAll(badCallParams);
					}
				}
				if (goodCallCount > 1 && !taskInstance.isMultiMethodOk()) {
					String msg = "Task " + taskInstance.getName() + " has " + goodCallCount;
					msg += " matching calls, either remove the extra tasks or mark task as multiMethodOk()";
					throw new InvalidGraph.MultiMethod(msg);
				}
			}
		}
		// If there is any task that lacks at least one resolvable call, generate an error
		// and include all unresolvable parameters so the user can result them with a holistic
		// view rather having to fix/re-execute/find-problem/fix/etc.
		if (badParams != null) {
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
	}
	
	/**
	 * Computes the completion threshold for each task/call instance. Must be called 
	 * for each TaskInstance during initialization; although this method is invoked
	 * recursively that recursion calculation is only done once per task instance.
	 * @param taskInstance
	 * @return
	 */
	private int computeThreshold(Task.Instance taskInstance) {
		return computeThreshold(taskInstance, taskInstance);
	}
	private int computeThreshold(Task.Instance base, Task.Instance taskInstance) {
		if (!taskInstance.countHasBeenComputed()) {
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
								pc += computeThreshold(base,nextTaskInstance);
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
		return taskInstance.getCompletionThreshold();
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

	private void fireRoots() {
		Invocation inv = spawnAllButOne(roots,-1,null,null);
		
		for (Task.Instance nextTaskInstance: dolts) {
			inv = continueOrSpawn(nextTaskInstance, "dolt",inv);
		}
		
		if (inv != null) {
			invokeAndFinish(inv,"own-root");
		}
	}
	
	/**
	 * Invokes a test method then follows up with any on-completion bookkeeping
	 * which may include recursively invoking (or spawning new threads for) tasks 
	 * that become ready as a result of the incoming call completing.
	 * @param inv
	 * @param context
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

