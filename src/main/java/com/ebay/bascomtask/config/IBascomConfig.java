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
import com.ebay.bascomtask.main.TaskMethodClosure;
import com.ebay.bascomtask.main.TaskThreadStat;

/**
 * BascomTask configuration. A standard {@link DefaultBascomConfig} implementation
 * is provided, which may be overridden and then set to replaced whatever config
 * is currently in place.
 * @author brendanmccarthy
 * @see com.ebay.bascomtask.config.BascomConfigFactory#setConfig(IBascomConfig)
 */
public interface IBascomConfig {
	
	/**
	 * The pool from which threads are drawn as needed for parallel execution.
	 * @return service
	 */
	ExecutorService getExecutor();
	
	/**
	 * Returns the default value for orchestrator timeout (when {@link com.ebay.bascomtask.main.Orchestrator#execute()} is called).
	 * Has no effect when {@link com.ebay.bascomtask.main.Orchestrator#execute(long)} is called, 
	 * as that method provides an explicit override of the default. 
	 * @return the default timeout in milliseconds
	 * @see com.ebay.bascomtask.config.DefaultBascomConfig#getDefaultOrchestratorTimeoutMs() for system default value
	 */
	long getDefaultOrchestratorTimeoutMs();
	
	/**
	 * Called on shutdown (when it is possible to do so, typically in a stand-alone
	 * program only).
	 */
	void notifyTerminate();
	
	/**
	 * Called after a thread is pulled from the pool and before it starts executing
	 * any task. Provides an opportunity to set the thread name, or any other related
	 * function.
	 * @param threadStat metadata for thread
	 */
	void notifyThreadStart(TaskThreadStat threadStat);
	
	/**
	 * Called after a thread is has finished executing a task and there are no other
	 * tasks ready to execute. Provides an opportunity to clear any thread state. 
	 * @param threadStat metadata for thread
	 */
	void notifyThreadEnd(TaskThreadStat threadStat);
	
	/**
	 * Returns a generator for use within an orchestration execution scope -- i.e. called once for each
	 * top-level invocation of {@link com.ebay.bascomtask.main.Orchestrator#execute(long, String)}.
	 * Subclasses can provide their own version instead of the default which generates
	 * {@link TaskMethodClosure} instances directly. The returned value can be a singleton or
	 * created for each call if the subclass needs to keep its own state.
	 * @param orc on which the execution was invoked
	 * @param pass provided as param to {@link com.ebay.bascomtask.main.Orchestrator#execute(long, String)}
	 * @return generator
	 */
	ITaskClosureGenerator getExecutionHook(Orchestrator orc, String pass); 
}



