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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		
		/**
		 * All explicitly-added tasks that must complete before this one
		 */
		private List<Instance> explicitBeforeDependencies = null;
		private List<Object> pendingBeforeDependencies = null;
		private List<Object> pendingAfterDependencies = null;
		
		/**
		 * Any exception that occurs when a targetPojo task method is invoked is recorded here
		 */
		private List<Exception> executionExceptions = null;
		
		/**
		 * Accumulates classes added through {@link #provides(Class)}, prior to graph resolution.
		 */
		private List<Class<?>> providing = null;
		
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
		
		/**
		 * The before/after methods accept ITasks as well as POJOs,
		 * ensure here that we're always operating on the POJO
		 * @param x
		 * @return
		 */
		private Object pojoFrom(Object x) {
		    if (x instanceof Instance) {
		        Instance task = (Instance)x;
		        return task.targetPojo;
		    }
		    return x;
		}
		
        @Override
        public ITask before(Object pojoTask) {
            if (pendingAfterDependencies==null) {
                pendingAfterDependencies = new ArrayList<>();
            }
            pendingAfterDependencies.add(pojoFrom(pojoTask));
            return this;
        }

        @Override
        public ITask after(Object pojoTask) {
            if (pendingBeforeDependencies==null) {
                pendingBeforeDependencies = new ArrayList<>();
            }
            pendingBeforeDependencies.add(pojoFrom(pojoTask));
            return this;
        }

		public Task getTask() {
			return Task.this;
		}
		
        @Override
        public ITask provides(Class<?> pojoTaskClass) {
            if (providing==null) {
                providing = new ArrayList<>();
            }
            providing.add(pojoTaskClass);
            return this;
        }
        
        List<Class<?>> getProvides() {
            return providing;
        }
		
		Call.Instance genNoCall() {
			return no_call.genInstance(this);
		}

        void updateExplicitDependencies(Map<Object,Instance> pojoMap) {
            if (pendingBeforeDependencies != null) {
                for (Object next: pendingBeforeDependencies) {
                    Instance matchingInstance = pojoMap.get(next);
                    addExplicitDependency(matchingInstance);;
                }
            }
            if (pendingAfterDependencies != null) {
                for (Object next: pendingAfterDependencies) {
                    Instance matchingInstance = pojoMap.get(next);
                    matchingInstance.addExplicitDependency(this);
                }
            }
        }
        
        void addExplicitDependency(Instance other) {
            if (explicitBeforeDependencies == null) {
                explicitBeforeDependencies = new ArrayList<>();
            }
            explicitBeforeDependencies.add(other);
            for (Call.Instance nextCall: this.calls) {
                for (Param.Instance nextParam: nextCall.paramInstances) {
                    if (nextParam.getTask() == other.getTask()) {
                        nextParam.setExplicitlyWired();
                    }
                }
            }
        }

        Map<Task,List<Instance>> groupBeforeDependencies() {
            if (explicitBeforeDependencies == null) {
                return null;
            }
            else {
                Map<Task,List<Instance>> map = new HashMap<>();
                for (Instance taskBeforeInstance: explicitBeforeDependencies) {
                    Task task = taskBeforeInstance.getTask();
                    List<Instance> instances = map.get(task);
                    if (instances == null) {
                        instances = new ArrayList<>();
                        map.put(task,instances);
                    }
                    instances.add(taskBeforeInstance);    
                }
                return map;
            }
        }
        
        void clearExplicits() {
            explicitBeforeDependencies = null;
        }

        @Override
        public Orchestrator getOrchestrator() {
            return orc;
        }

        void addException(Exception err) {
            if (executionExceptions == null) {
                executionExceptions = new ArrayList<>();
            }
            executionExceptions.add(err);
        }
        
        List<Exception> getExecutionExceptions() {
            return executionExceptions;
        }
	}
	
	/**
	 * Name anonymous classes with a unique integer because an empty string
	 * might result in name conflicts.
	 */
	private static int anon_counter = 0;
	private static final Map<Class<?>,String> ANON_MAP = new HashMap<>();
	
	Task(Class<?> clazz) {
		this.taskClass = clazz;
		String nm = clazz.getSimpleName();
		if ("".equals(nm)) {
		    synchronized (ANON_MAP) {
		        nm = ANON_MAP.get(clazz);
		        if (nm == null) {
		            nm = String.valueOf(anon_counter++);
		            ANON_MAP.put(clazz,nm);
		        }
		    }
		}
		this.taskName = nm;
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
