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

import com.ebay.bascomtask.exceptions.InvalidTask;

/**
 * Shadow object for POJO tasks added to an orchestrator.
 * @author brendanmccarthy
 */
public interface ITask {
    
    /**
     * Returns the orchestrator which created and owns this task. An ITask is created 
     * by an orchestrator e.g. when POJO tasks are added to it.
     * @return owning orchestrator
     * @see com.ebay.bascomtask.main.Orchestrator#addWork(Object)
     * @see com.ebay.bascomtask.main.Orchestrator#addPassThru(Object)
     * @see com.ebay.bascomtask.main.Orchestrator#addConditionally(Object, boolean)
     */
    public Orchestrator getOrchestrator();
    
	/**
	 * The name of this pojoCalled instance in an orchestrator, guaranteed to always be unique
	 * that orchestrator.
	 * @return the user-supplied or auto-generated name, will never be null
	 */
	public String getName();
	
	/**
	 * Each task has a name that defaults to its type name followed by a unique integer 
	 * for that type, unless overridden here.
	 * @param name new name for task, any previous name
	 * @return task name
	 * @throws InvalidTask.NameConflict if a task with this name already exists
	 */
	public ITask name(String name);
	
	/**
	 * Should the orchestrator wait for this task to finish before completing
	 * an invocation of the {@link Orchestrator#execute()} method?
	 * @return true iff this task should be treated as a 'nowait' task
	 */
	public boolean isWait();
	
	
	/**
	 * Specifies whether the orchestrator should wait for this instance to finish.
	 * The default is true. Even if this value is set to false, the orchestrator
	 * may still wait for another task that is dependent on this task, and that
	 * task has {@link #isWait()}==true.
	 * @param wait behavior on orchestrator execute() exit
	 * @see Orchestrator#execute()
	 * @return this task
	 */
	public ITask wait(boolean wait);
	
	/**
	 * Convenience method for setWait(false);
	 * @return this task
	 */
	public ITask noWait();
	
	/**
	 * Indicates that it is ok if the task has more than one method which can fire.
	 * By default, this would be flagged as an error.
	 * @return this task
	 */
	public ITask multiMethodOk();
	
	/**
	 * Should an exception be thrown when a task has more than one method each
	 * of which has all parameters added to the graph?
	 * @return true iff this task should <i>not</i> reject multiple matching methods
	 */
	public boolean isMultiMethodOk();
	
	/**
	 * Prevents this task from being run by the calling thread, spawning a new thread if
	 * needed. Usually only needed if there is work needed to be done by the calling thread
	 * and it is desirable to get to that work without waiting for this task to complete.
	 * @return this task
	 */
	public ITask fork();
	
	/**
	 * Should this task not be run in the calling thread?
	 * @return true iff this task should never be run in the main orchestrator thread
	 */
	public boolean isFork();
	
	/**
	 * Ensures this task completes before the given task. Useful when BascomTask's autowiring needs fine
	 * tuning, for example when there is a hidden dependency between tasks that is not otherwise exposed
	 * through the task dependency tree. If applied between two tasks that already have an autowired
	 * dependency, will limit the cardinality of the dependency to only those exposed explicitly in this
	 * way. Consider this case: if B depends on A and there are two instances of A, then by default autowiring B
	 * would depend on both instances of A. If on the other hand an explicitly wired dependency was set
	 * up along the lines of <code>a1.before(b)</code>, then the B instance b would only depend on that
	 * singular A instances a1.
	 * <p>
	 * This method can safely be called before <code>pojoTask</code> is added to the graph, but will
	 * result in an error on the next {@link com.ebay.bascomtask.main.Orchestrator#execute()} if <code>pojoTask</code> has not been added
	 * by that time.
	 * @param pojoTask or ITask which should run before this one is started
	 * @return this task
	 */
	public ITask before(Object pojoTask);
	
	/**
	 * The inverse of {@link #before(Object)}, achieving the same effect while allowing the arguments to
	 * be reversed.
	 * @param pojoTask or ITask which should only run after this one
	 * @return this task
	 */
	public ITask after(Object pojoTask);
	
	/**
	 * Indicates that a task will add to the current orchestrator a pojo task instance of the given class.
	 * This is necessary only when (a) a task is dynamically adding to the orchestrator and (b) that task
	 * is adding an instance upon which tasks at the outer level depend. Without invoking this method,
	 * {@link com.ebay.bascomtask.main.Orchestrator#execute()} will fail at the outer level, asserting that
	 * the graph is incomplete -- the orchestrator has no way to peer inside the inner task code to 
	 * determine when/if an instance will be added.
	 * <p>
	 * If the nested task does not in fact add the promised instance, an exception will be throw when
	 * it terminates.
	 * @param pojoTaskClass for task that will be provided when this task is invoked
	 * @return this task
	 */
	public ITask provides(Class<?> pojoTaskClass);
}
