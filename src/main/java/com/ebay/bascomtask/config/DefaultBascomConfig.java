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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.ebay.bascomtask.main.Orchestrator;

public class DefaultBascomConfig implements IBascomConfig {
	
	private ExecutorService pool = Executors.newFixedThreadPool(30);
	private ITaskInterceptor taskInterceptor = new DefaultTaskInterceptor();

	@Override
	public ExecutorService getExecutor() {
		return pool;
	}
	
	@Override
	public void notifyTerminate() {
		shutdownAndAwaitTermination(pool);
		pool = null;
	}

	/**
	 * Standard way to shutdown an executor as defined in 
	 * <a href="https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html">ExecutorService shutdown</a>,
	 * used by this class and also made public here for convenience.
	 * @param pool to shutdown
	 */
	public static void shutdownAndAwaitTermination(ExecutorService pool) {
		pool.shutdown(); // Disable new tasks from being submitted
		try {
			// Wait a while for existing tasks to terminate
			if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
				pool.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
					throw new RuntimeException("Pool did not terminate");
				}
			}
		} catch (InterruptedException ie) {
			// (Re-)Cancel if current thread also interrupted
			pool.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public void linkParentChildThread(Orchestrator orc, Thread parent, Thread child) {
		orc.setThreadId();
	}

	@Override
	public void unlinkParentChildThread(Orchestrator orc, Thread parent, Thread child) {

	}
	
    @Override
    public ITaskInterceptor getDefaultInterceptor() {
        return taskInterceptor;
    }
}
