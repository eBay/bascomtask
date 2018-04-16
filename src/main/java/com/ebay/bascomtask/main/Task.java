package com.ebay.bascomtask.main;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ebay.bascomtask.main.Call.Param;

/**
 * A wrapper for a user task class.
 * @author brendanmccarthy
 */
class Task {
	
	static final Logger LOG = LoggerFactory.getLogger(Orchestrator.class);

	final Class<?> taskClass;
	
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
	 * Links to parameters of calls that might be invoked when one of our 
	 * task calls 'fires' (completes). This list is used to drive the dataflow
	 * graph to completion.
	 */
	private List<Call.Param> backList = new ArrayList<>();
	
	/**
	 * From a user perspective, a 'task' is what they add @Work methods to; 
	 * here we shadow that task with a Task.Instance. A Task.Instance is created
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
		 * Each instance has a unique index with respect to its type
		 */
		final int indexInType;
		
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
		
		Instance(Orchestrator orc, Object targetTask, boolean workElsePassThru, int indexInType) {
			this.orc = orc;
			this.targetPojo = targetTask;
			this.workElsePassThru = workElsePassThru;
			this.indexInType = indexInType;
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
		
		@Override
		public ITask name(String name) {
			// Expect exception throw if name conflict
			orc.setUniqueTaskInstanceName(this,name);
			this.name = name;
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
