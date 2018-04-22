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
	private final String name;

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
		 * Each instance has a unique index with respect to its type, which
		 * should be set by Orchestrator.
		 */
		private int indexInType = -1;
		
		/**
		 * Was this task added through addWork() or addPassThru()?
		 */
		final boolean workElsePassThru;
		
		/**
		 * Should orchestrator wait for task to finish?
		 */
		boolean wait = true;
		
		/**
		 * For logging/debugging
		 */
		private String name = null;
		
		/**
		 * True when user has set name, which therefore won't be auto-generated
		 */
		private boolean userSuppliedName = false;
		
		/**
		 * Multiple matching methods ok?
		 */
		private boolean multiMethodOk = false;

		/**
		 * One for each @Work method (or each @PassThru method if added as passthru)
		 */
		final List<Call.Instance> calls = new ArrayList<>();
		
		/**
		 * All parameters of all calls that have the type of our targetTask 
		 */
		final List<Param.Instance> backList = new ArrayList<>();
		
		Instance(Orchestrator orc, Object targetTask, boolean workElsePassThru) {
			this.orc = orc;
			this.targetPojo = targetTask;
			this.workElsePassThru = workElsePassThru;
			List<Call> targetCalls = workElsePassThru ? workCalls : passThruCalls;
			for (Call call: targetCalls) {
				calls.add(call.new Instance(this));
			}			
		}
		
		@Override
		public String toString() {
			String mode = workElsePassThru ? "work" : "passthru";
			return getName() + '(' + mode + ") ==> " + targetPojo.toString();
		}

		void propagateComplete() {
			// TBD/TODO
			/*
			for (Call.Param.Instance nextBackParam: backList) {
				Call.Instance nextCallInstance = nextBackParam.getCall();
				if (!nextCallInstance.mightStillBeCalled()) {
					System.out.println("TBD...");
					//propagateComplete(nextCallInstance);
				}
			}
			*/
		}
		
		List<Call> getCandidateCalls() {
			return workElsePassThru ? workCalls : passThruCalls;
		}

		@Override
		public String getName() {
			String nm = name;
			if (nm==null) {
				nm = Task.this.name + "-" + indexInType;
				this.name = nm; // Avoid recomputing every time (also threadsafe)
			}
			return nm;
		}
		
		void setIndexInType(int indexInType) {
			this.indexInType = indexInType;
			if (!userSuppliedName) {
				this.name = null;
			}
		}
		
		@Override
		public ITask name(String name) {
			// Expect exception throw if name conflict
			orc.setUniqueTaskInstanceName(this,name);
			this.name = name;
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

		public Task getTask() {
			return Task.this;
		}
		
		Call.Instance genNoCall() {
			return no_call.genInstance(this);
		}
	}	
	
	Task(Class<?> clazz) {
		this.taskClass = clazz;
		this.name = clazz.getSimpleName();
	}

	String getName() {
		return name;
	}
	
	@Override
	public String toString() {
		return "Task:" + getName();
	}
	
	void backLink(Param param) {
		backList.add(param);
	}
}
