package com.ebay.bascomtask.main;

import com.ebay.bascomtask.exceptions.InvalidTask;

/**
 * Shadow object for POJOs added to an orchestrator.
 * @author brendanmccarthy
 */
public interface ITask {
	/**
	 * The name of this pojoCalled instance in an orchestrator, guaranteed to always be unique
	 * that orchestrator.
	 * @return the user-supplied or auto-generated name, will never be null
	 */
	public String getName();
	
	/**
	 * Each task has a name that defaults to its type name followed by a unique integer 
	 * for that type, unless overridden here.
	 * @return task name
	 * @throws InvalidTask.NameConflict if a task with this name already exists
	 */
	public ITask name(String name);
	
	/**
	 * Should the orchestrator wait for this task to finish before completing
	 * an invocation of the {@link Orchestrator.execute()} method?
	 * @return
	 */
	public boolean isWait();
	
	
	/**
	 * Specifies whether the orchestrator should wait for this instance to finish.
	 * The default is true. Even if this value is set to false, the orchestrator
	 * may still wait for another task that is dependent on this task, and that
	 * task has {@link #isWait()}==true.
	 * @param wait
	 * @return
	 */
	public ITask wait(boolean wait);
	
	/**
	 * Convenience method for setWait(false);
	 * @return
	 */
	public ITask noWait();
	
	/**
	 * Indicates that it is ok if the task has more than one method which can fire.
	 * By default, this would be flagged as an error.
	 * @return
	 */
	public ITask multiMethodOk();
	
	/**
	 * Should an exception be thrown when a task has more than one method each
	 * of which has all parameters added to the graph?
	 * @return
	 */
	public boolean isMultiMethodOk();
	
	/**
	 * Prevents this task from being run by the calling thread, spawning a new thread if
	 * needed. Usually only needed if there is work needed to be done by the calling thread
	 * and it is desirable to get to that work without waiting for this task to complete.
	 * @return
	 */
	public ITask fork();
	
	/**
	 * Should this task not be run in the calling thread?
	 * @return
	 */
	public boolean isFork();
}
