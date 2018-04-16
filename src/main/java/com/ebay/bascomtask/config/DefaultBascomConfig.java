package com.ebay.bascomtask.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.ebay.bascomtask.main.Orchestrator;

public class DefaultBascomConfig implements IBascomConfig {
	
	private ExecutorService pool = Executors.newFixedThreadPool(30); 

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
	 */
	public static void shutdownAndAwaitTermination(ExecutorService pool) {
		pool.shutdown(); // Disable new tasks from being submitted
		try {
			// Wait a while for existing tasks to terminate
			if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
				pool.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				if (!pool.awaitTermination(60, TimeUnit.SECONDS))
					System.err.println("Pool did not terminate");
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
}
