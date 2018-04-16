package com.ebay.bascomtask.config;

import java.util.concurrent.ExecutorService;

import com.ebay.bascomtask.main.Orchestrator;

/**
 * BascomTask configuration. A standard {@link DefaultBascomConfig} implementation
 * is provided, which may be overridden in {@link BascomConfigFactory}.
 * @author brendanmccarthy
 */
public interface IBascomConfig {
	
	/**
	 * The pool from which threads are drawn as needed for parallel execution.
	 * @return
	 */
	ExecutorService getExecutor();
	
	/**
	 * Called on shutdown (when it is possible to do so, typically in a stand-alone
	 * program only).
	 */
	void notifyTerminate();
	
	/**
	 * Provides an opportunity for bookkeeping after a thread is drawn from a pool.
	 * @param orc
	 * @param parent thread which identified opportunity for parallel call
	 * @param child thread drawn from pool
	 */
	void linkParentChildThread(Orchestrator orc, Thread parent, Thread child);
	
	/**
	 * Provides an opportunity for bookkeeping before thread is returned to pool.
	 * @param orc
	 * @param parent thread which identified opportunity for parallel call
	 * @param child thread drawn from pool
	 */
	void unlinkParentChildThread(Orchestrator orc, Thread parent, Thread child);
}
