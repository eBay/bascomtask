package com.ebay.bascomtask.main;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ebay.bascomtask.annotations.Scope;

/**
 * A wrapper for a Java method on a task POJO, including parameter wrappers for each parameter.
 * @author brendanmccarthy
 */
class Call {
	
	static final Logger LOG = LoggerFactory.getLogger(Call.class);

	/**
	 * A call has exactly one task
	 */
	private final Task task;
	
	/**
	 * Corresponding method on user POJO. This may be null, but only when this call is used as
	 * a dummy call for tasks that have no task methods.
	 */
	private final Method method;
	
	/**
	 * Marks a task method that should be fast so should be executed directly 
	 * when encountered rather than spawning a new thread for it.
	 */
	private final boolean light;
	
	/**
	 * Governs behavior when invoked multiple times.
	 */
	private final Scope scope;
	
	/**
	 * Formal parameters.
	 */
	private List<Param> params = new ArrayList<>();
	
	Task getTask() {
		return task;
	}
	
	int getNumberOfParams() {
		return params.size();
	}
	
	String getMethodName() {
		return method.getName();
	}
	
	void add(Param param) {
		params.add(param);
	}
	
	Instance genInstance(Task.Instance taskInstance) {
		return new Instance(taskInstance);
	}
	
	private static final int[] EMPTY_FREEZE = new int[0]; 
		
	/**
	 * A representation of a {@link Call} for a Task.Instance. Collects parameters
	 * in preparation for invoking the Call method. Note that multiple method invocations 
	 * may be made through given Call.Instance, potentially at overlapping times, so
	 * it must be threadsafe.
	 * @author brendanmccarthy
	 */
	class Instance extends Completable {
		
		/**
		 * Owner of this call 
		 */
		final Task.Instance taskInstance;
		
		/**
		 * Parameter instances that correspond to param list in outer call
		 */
		final Param.Instance[] paramInstances = new Param.Instance[params.size()];
		
		/**
		 * The positions of active parameters at the time of creation of this instance. This allows for task 
		 * instances that have already fired to be replayed for this task when added dynamically (i.e. other
		 * tasks were already started and may therefore have already fired prior to this instance being
		 * added or even created).
		 */
		final int[] startingFreeze = new int[params.size()];
		
		/**
		 * Non-null if Scope.Sequential has been set, and if so this accumulates calls while a task is active 
		 */
		private final ConcurrentLinkedDeque<Object[]> followCallArgs;
		
		/**
		 * True iff a @Work/@Passthru method has been entered and not exited. Only
		 * used for Scope.Sequential case, thus only one thread can set it.
		 */
		private boolean reserved = false;

		Instance(Task.Instance taskInstance) {
			this.taskInstance = taskInstance;
			final int sz = params.size();
			for (int i=0; i<sz; i++) {
				Param next = params.get(i);
				paramInstances[i] = next.new Instance(this);
			}
			if (scope == Scope.SEQUENTIAL) {
				followCallArgs = new ConcurrentLinkedDeque<>();
			}
			else {
				followCallArgs = null;
			}
		}
		
		@Override
		public String toString() {
			return "Call.Instance " + formatState();
		}
		
		Call getCall() {
			return Call.this;
		}
		
		Task.Instance getTaskInstance() {
			return taskInstance;
		}
		
		@Override
		Completable containingCompletable() {
			return taskInstance;
		}
		
		boolean isNoWait() {
			return !taskInstance.wait;
		}
		
		String formatState() {
			return formatState(null);
		}

		private String formatState(Param.Instance p) {
			StringBuilder sb = new StringBuilder();
			sb.append(taskInstance.getName());
			sb.append('.');
			sb.append(format(method));
			sb.append('(');
			boolean first = true;
			for (Param.Instance next: paramInstances) {
				if (!first) sb.append(',');
				first = false;
				sb.append(next.toString());
			}
			sb.append(") ");
			sb.append(completionSay());
			return sb.toString();
		}

		/**
		 * Adds a binding for a parameter, possibly resulting in POJO task methods being invoked if all
		 * parameters for any dependent method are available. The sequencing here is non-trivial due to
		 * allowance for multiple instance matching for each parameter.
		 * <p>
		 * Although all tasks could be spawned as necessary, one task Invocation is returned for the calling 
		 * thread since it will be available to do work and it would be inefficient to spawn a new thread 
		 * and then have the calling thread just wait(). The caller may already have such a pending invocation
		 * which they can provide as the last argument, which is either returned as-is or a replacement is
		 * returned; if the latter then the input Invocation is spawned in a new thread.
		 * @param orc
		 * @param context
		 * @param userTaskInstance instance of user's task
		 * @param parameterIndex index of parameter that is being bound
		 * @param inv current invocation, null if none
		 * @return same or possibly new invocation for the calling thread to invoke
		 */
		Invocation bind(Orchestrator orc, String context, Object userTaskInstance, int parameterIndex, Invocation inv) {
			int[] freeze;
			int ordinalOfFiringParameter;
			if (parameterIndex < 0) {  // A root task call, i.e. one with no task parameters? 
				freeze = EMPTY_FREEZE;
				ordinalOfFiringParameter = -1;
			}
			else {
				final Param.Instance firingParemeter = paramInstances[parameterIndex];
				// Obtain a unique set of parameter indexes within the synchronized block such that no
				// other thread would get the same set. Once these are set, the actual execution can 
				// safely proceed outside the synchronized block.
				synchronized (this) {
					ordinalOfFiringParameter = firingParemeter.bindings.size();
					firingParemeter.bindings.add(userTaskInstance);
					System.out.println("BIND " + userTaskInstance.getClass().getName() + " for " + firingParemeter);
					for (Param.Instance next: paramInstances) {
						if (!next.ready()) {
							return inv; // If not all parameters have at least one binding, not ready to execute call
						}
					}
					freeze = new int[paramInstances.length];
					for (int i=0; i< paramInstances.length; i++) {
						freeze[i] = paramInstances[i].bindings.size();
					}
				}
			}

			return crossInvoke(inv,freeze,parameterIndex,ordinalOfFiringParameter,orc,context);
		}
		
		Invocation crossInvoke(Invocation inv, int[] freeze, int firingParameterIndex, int ordinalOfFiringParameter, Orchestrator orc, String context) {
			Object[] args = new Object[paramInstances.length];			
			return crossInvoke(0,args,inv,freeze,firingParameterIndex,ordinalOfFiringParameter,orc,context);
		}

		/**
		 * Invokes our task method 1 or more times with the cross-product off all parameters within the 'startingFreeze' range,
		 * *except* for the the firing parameter for which we don't include any of its sibling parameters.
		 * @param px the looping parameter index (incremented recursively)
		 * @param args array accumulating args for the next call
		 * @param inv the current invocation to be returned to the calling thread, null if none
		 * @param startingFreeze the max ordinal position of each parameter to include
		 * @param firingParameterIndex which parameter fired that cause this method to be invoked
		 * @param ordinalOfFiringParameter the ordinal position of the firing parameter within its Param.Instance.bindings
		 * @param orc
		 * @param context
		 * @return inv or a replacement to be called by the invoking thread
		 */
		private Invocation crossInvoke(int px, Object[]args, Invocation inv, int[] freeze, int firingParameterIndex, int ordinalOfFiringParameter, Orchestrator orc, String context) {
			if (px == args.length) {
				Invocation newInvocation = new Invocation(this,args);  // makes a copy of args!
				if (light) {
					orc.invokeAndFinish(newInvocation,"light");
				}
				else if (isNoWait() && orc.isCallingThread()) {
					// Don't assign main thread with tasks it should not wait for.
					orc.spawn(newInvocation);
				}
				else if (!postPending(newInvocation)) {
					if (inv != null) {
						orc.spawn(inv);
					}
					return newInvocation;
				}
			}
			else {
				final Param.Instance paramAtIndex = paramInstances[px];
				if (px==firingParameterIndex) {
					if (paramAtIndex.getParam().isList) {
						if (!paramAtIndex.ready()) {
							return inv; // List arg not ready
						}
						args[px] = paramAtIndex.bindings;
					}
					else {
						args[px] = paramAtIndex.bindings.get(ordinalOfFiringParameter);
					}
					inv = crossInvoke(px+1,args,inv,freeze,firingParameterIndex,ordinalOfFiringParameter,orc,context);
				}
				else if (paramAtIndex.getParam().isList) {
					if (!paramAtIndex.ready()) {
						return inv;  // List arg not ready
					}
					args[px] = paramAtIndex.bindings;
					inv = crossInvoke(px+1,args,inv,freeze,firingParameterIndex,ordinalOfFiringParameter,orc,context);
				}
				else {
					for (int i=0; i<freeze[px]; i++) {
						args[px] = paramAtIndex.bindings.get(i);
						inv = crossInvoke(px+1,args,inv,freeze,firingParameterIndex,ordinalOfFiringParameter,orc,context);
					}
				}
			}
			return inv;
		}

		/**
		 * If this is a Scope.SEQUENTIAL call and another thread is operating on our method, queue the invocation for 
		 * later execution. Also sets the proper state so that later invocations of this method will behave accordingly.
		 * @param inv to (possibly) queue
		 * @return true iff queued
		 */
		private boolean postPending(Invocation inv) {
			if (scope==Scope.SEQUENTIAL) {
				synchronized (this) {
					if (reserved) {
						followCallArgs.add(inv.copyArgs());
						return true;
					}
					else {
						reserved = true;
					}
				}
			}
			return false;
		}

		/**
		 * Invokes the Java method associated with this call.
		 * @param orc
		 * @param context string for debug messages
		 * @param args for target call
		 * @return false iff the java method returned a boolean false indicating that the method should not fire
		 */
		boolean invoke(Orchestrator orc, String context, Object[] args) {
			boolean result = true;
			String kind = taskInstance.workElsePassThru ? "@Work" :  "@PassThru";
			long start = 0;
			String msg = null;
			try {
				start = System.currentTimeMillis();
				if (LOG.isDebugEnabled()) {
					LOG.debug("Invoking {} task \"{}\" {} {}",context,taskInstance.getName(),kind,signature());
				}
				synchronized (this) {
					startOneCall();
				}
				if (method != null) {
					Object methodResult = method.invoke(taskInstance.targetPojo, (Object[])args);
					if (Boolean.FALSE.equals(methodResult)) {
						result = false;
					}
				}
				// For Scope.SEQUENTIAL, only one thread will be active at a time, so it is safe
				// for all threads to just reset this to false.
				reserved = false;
			} 
			catch (InvocationTargetException e) {
				throw new RuntimeException(e.getTargetException());
			}
			catch (Exception e) {
				msg = "Could not invoke " + context + " task " + kind + " " + signature() + " : " + e.getMessage();
				throw new RuntimeException(msg);
			}
			finally {
				long end = System.currentTimeMillis();
				long dur = end - start;
				if (LOG.isDebugEnabled()) {
					String rez = msg==null ? "success" : msg;
					LOG.debug("Completed {} task \"{}\" {} {} in {}ms result: {}",
							context,taskInstance.getName(),kind,signature(),dur,rez);
				}
			}
			return result;
		}
		
		Object[] popSequential() {
			if (followCallArgs != null) {
				return followCallArgs.pollFirst();
			}
			return null;
		}
	}
	
	Call(Task task, Method method, Scope scope, boolean light) {
		this.task = task;
		this.method = method;
		this.light = light;
		this.scope = scope;
	}
	
	@Override
	public String toString() {
		String sig = signature();
		return "Call " + sig;
	}
	
	private String format(Method method) {
		return method==null ? "<<no-method>>" : method.getName();
	}
	
	String signature() {
		StringBuilder sb = new StringBuilder();
		sb.append(task.getName());
		sb.append('.');
		sb.append(format(method));
		sb.append('(');
		boolean first = true;
		for (Param next: params) {
			if (!first) sb.append(',');
			first = false;
			sb.append(next.getTypeName());
		}
		sb.append(')');
		return sb.toString();
	}
	
	/**
	 * A parameter of a POJO task method.
	 * @author brendanmccarthy
	 */
	class Param {

		final Task taskParam;
		/**
		 * Ordinal position of this parameter
		 */

		final int paramaterPosition;
		/**
		 * true iff List<X> rather than X 
		 */
		final boolean isList;
		
		class Instance {
			/**
			 * The actual arguments in proper order, all of which will be POJOs added 
			 * to the orchestrator as tasks
			 */
			final List<Object> bindings = new ArrayList<>();
			
			/**
			 * The call which contains this parameter 
			 */
			final Call.Instance callInstance;
			
			/**
			 * How we know, for list arguments, when all parameters are ready 
			 */
			private int threshold = 0;
			
			Instance(Call.Instance callInstance) {
				this.callInstance = callInstance;
			}
			
			@Override
			public String toString() {
				return taskParam.taskClass.getSimpleName() + ':' + bindings.size() + '/' + threshold;
			}
			
			boolean ready() {
				int bc = bindings.size();
				return isList ? bc >= threshold : bc > 0;
			}
			
			void bumpThreshold() {
				this.threshold += 1;
			}
			
			int getThreshold() {
				return threshold;
			}

			Task getTask() {return taskParam;}
			Call.Instance getCall() {return callInstance;}
			Param getParam() {return Param.this;}
			String getTypeName() {return Param.this.getTypeName();}
		}

		Param(Task task, int paramterPosition, boolean isList) {
			this.taskParam = task;
			this.paramaterPosition = paramterPosition;
			this.isList = isList;
		}
		
		Call getCall() {return Call.this;}
		String getTypeName() {return taskParam.taskClass.getSimpleName();}
		
		@Override
		public String toString() {
			return "Param(" + taskParam.taskClass.getSimpleName() + ')';
		}
	}
}