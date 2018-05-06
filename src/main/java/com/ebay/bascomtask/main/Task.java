package com.ebay.bascomtask.main;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ebay.bascomtask.main.Call.Param;
import com.ebay.bascomtask.annotations.Scope;

/**
 * A wrapper for a user task class. An instance is created for each unique class
 * of POJO added to any orchestrator.
 * @author brendanmccarthy
 */
class Task {
	
	static final Logger LOG = LoggerFactory.getLogger(Orchestrator.class);

	/**
	 * Class of POJO.
	 */
	final Class<?> taskClass;
	
	/**
	 * For logging and debugging.
	 */
	private final String taskName;

	/**
	 * All the @Work methods in the task. 
	 */
	List<Call> workCalls = new ArrayList<>();
	
	/**
	 * All the @PassThru methods in the task. 
	 */
	List<Call> passThruCalls = new ArrayList<>();
	
	/**
	 * Used when a task has no task methods; not used if a task has task methods.
	 */
	final Call no_call = new Call(this,null,Scope.FREE,true); 
	
	/**
	 * Links to parameters of calls that expect this task as a parameter.
	 * This list is used to drive the dataflow forward.
	 * graph to completion.
	 */
	List<Call.Param> backList = new ArrayList<>();

	/**
	 * Which set of task methods should be considered?
	 */
	enum TaskMethodBehavior {
		WORK,
		PASSTHRU,
		NONE
	}	
	
	private static List<Call> EMPTY_CALLS = new ArrayList<>();

	/**
	 * From a user perspective, a 'task' is what they add @Work methods to; 
	 * BascomTask shadows that task with a Task.Instance. A Task.Instance is created
	 * for each added user task, even if multiple tasks instances are added that 
	 * are the same Java type.
	 */
	class Instance extends Completable implements ITask {
		
		final Orchestrator orc;
		
		/**
		 * The POJO task which was added to the orchestrator
		 */
		final Object targetPojo;

		/**
		 * What task methods will be processed?
		 */
		final TaskMethodBehavior taskMethodBehavior;
		
		/**
		 * Should orchestrator wait for task to finish?
		 */
		boolean wait = true;
		
		/**
		 * For logging/debugging; never null
		 */
		private String instanceName;
		
		/**
		 * True when user has set instanceName, which therefore won't be auto-generated
		 */
		private boolean userSuppliedName = false;
		
		/**
		 * Multiple matching methods ok?
		 */
		private boolean multiMethodOk = false;
		
		/**
		 * Should never be executed by calling thread?
		 */
		private boolean fork = false;

		/**
		 * One for each @Work method (or each @PassThru method if added as passthru)
		 */
		final List<Call.Instance> calls = new ArrayList<>();
		
		/**
		 * All parameters of all calls that have the type of our targetTask 
		 */
		final List<Param.Instance> backList = new ArrayList<>();
		
		Instance(Orchestrator orc, Object targetTask, TaskMethodBehavior taskMethodBehavior) {
			this.orc = orc;
			this.targetPojo = targetTask;
			this.taskMethodBehavior = taskMethodBehavior;
			List<Call> targetCalls = getCandidateCalls();
			for (Call call: targetCalls) {
				calls.add(call.new Instance(this));
			}
			// Will be reset later, but assign here so that getName() will never return null
			this.instanceName = Task.this.getName() + "-???";
		}
		
		@Override
		public String toString() {
			return getName() + '(' + taskMethodBehavior + ") ==> " + targetPojo.toString();
		}
		
		List<Call> getCandidateCalls() {
			switch (taskMethodBehavior) {
				case WORK: return workCalls;
				case PASSTHRU: return passThruCalls;
				case NONE: return EMPTY_CALLS;
			}
			throw new RuntimeException("Unexpected fall-thru");
		}
		
		synchronized void setIndexInType(int indexInType) {
			if (!userSuppliedName) {
				// No conflict check here, since this should be unique and anyway would later be caught
				this.instanceName = Task.this.taskName + "-" + indexInType;
			}
		}
		
		@Override
		public String getName() {
			return instanceName;
		}
		
		@Override
		public synchronized ITask name(String name) {
			if (name==null) {
				throw new RuntimeException("Task instanceName must not be null");
			}
			// Expect exception thrown if instanceName conflict
			orc.checkPendingTaskInstanceInstanceName(name);
			this.instanceName = name;
			this.userSuppliedName = true;
			return this;
		}
		
		@Override
		public boolean isWait() {
			return wait;
		}

		@Override
		public ITask wait(boolean wait) {
			this.wait = wait;
			orc.notifyWaitStatusChange(this,wait);
			return this;
		}
		
		@Override
		public ITask noWait() {
			return wait(false);
		}

		@Override
		public ITask multiMethodOk() {
			multiMethodOk = true;
			return this;
		}

		@Override
		public boolean isMultiMethodOk() {
			return multiMethodOk;
		}
		
		@Override
		public ITask fork() {
			fork = true;
			return this;
		}

		@Override
		public boolean isFork() {
			return fork;
		}

		

		public Task getTask() {
			return Task.this;
		}
		
		Call.Instance genNoCall() {
			return no_call.genInstance(this);
		}
	}	
	
	Task(Class<?> clazz) {
		this.taskClass = clazz;
		this.taskName = clazz.getSimpleName();
	}

	String getName() {
		return taskName;
	}
	
	@Override
	public String toString() {
		return "Task:" + getName();
	}
	
	void backLink(Param param) {
		backList.add(param);
	}
}
