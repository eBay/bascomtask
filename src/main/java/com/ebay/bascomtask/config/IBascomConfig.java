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
	 * @return service
	 */
	ExecutorService getExecutor();
	
	/**
	 * Called on shutdown (when it is possible to do so, typically in a stand-alone
	 * program only).
	 */
	void notifyTerminate();
	
	/**
	 * Provides an opportunity for bookkeeping after a thread is drawn from a pool.
	 * @param orc Orchestrator associated with threads
	 * @param parent thread which identified opportunity for parallel call
	 * @param child thread drawn from pool
	 */
	void linkParentChildThread(Orchestrator orc, Thread parent, Thread child);
	
	/**
	 * Provides an opportunity for bookkeeping before thread is returned to pool.
	 * @param orc Orchestrator associated with threads
	 * @param parent thread which identified opportunity for parallel call
	 * @param child thread drawn from pool
	 */
	void unlinkParentChildThread(Orchestrator orc, Thread parent, Thread child);
}
