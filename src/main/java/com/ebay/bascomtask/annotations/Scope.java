package com.ebay.bascomtask.annotations;

/**
 * Specifies desired behavior when a task is fired more than once.
 * @author brendanmccarthy
 */
public enum Scope {
	/**
	 * Calls are processed in parallel without constraint; assumes the task/call
	 * is stateless or can manage multiple threads.
	 */
	FREE,
	
	/**
	 * Calls are delayed and processed on after the other; the task/call need not
	 * be threadsafe. 
	 */
	SEQUENTIAL
}
