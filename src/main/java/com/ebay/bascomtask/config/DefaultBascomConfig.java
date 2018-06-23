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
import com.ebay.bascomtask.main.TaskThreadStat;

public class DefaultBascomConfig implements IBascomConfig {
    
    static final String THREAD_ID_PREFIX = "BT:";
	
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
    public ITaskInterceptor getDefaultInterceptor() {
        return taskInterceptor;
    }
    
    /**
     * Indicates the width max for thread names such that when exceeded a '..' string will be provided in place
     * of any more predecessors. This is not an absolute width since the thread id and name is always included
     * as well as the thread local index. It just prevents arbitrarily long thread names for very heavily nested
     * thread spawn chains.
     * @return
     */
    public int getThreadNameWidthMax() {
        return 20;
    }

    /**
     * Sets the thread name.
     * @param threadStat of the thread which is being started
     * @see #setThreadName(TaskThreadStat)
     */
    @Override
    public void notifyThreadStart(TaskThreadStat threadStat) {
        setThreadName(threadStat);
    }
    
    /** 
     * Sets the name of given thread like so:
     * <p><code>
     *   BT:ID:NAME#..11.12.13.14
     * <p></code>
     * Where:
     * <ul>
     * <li> "BT:" is a standard prefix
     * <li> ID is the orchestrator id
     * <li> The orchestrator NAME is only included if not null</li>
     * <li> The last value (14 above) is the local thread index in the orchestrator</li>
     * <li> Any values preceding the last are the threads which spawned it</li>
     * <li> A '..' indicates there are more predecessors which are not included</li>
     * </ul>
     * @param threadStat of the thread which is being started
     */
    protected void setThreadName(TaskThreadStat threadStat) {
        Orchestrator orc = threadStat.getOrchestrator();
        String id = String.valueOf(orc.getId());
        String name = orc.getName();
        String threadId = constructThreadId(threadStat,id,name,getThreadNameWidthMax());
        Thread.currentThread().setName(threadId);
    }
    
    protected static String constructThreadId(TaskThreadStat threadStat, String id, String name, int max) {
        StringBuilder sb = new StringBuilder();
        sb.append(THREAD_ID_PREFIX);
        sb.append(id);
        if (name != null) {
            sb.append(':');
            sb.append(name);
        }
        sb.append('#');
        fill(threadStat,sb,true,max);
        return sb.toString();
    }

    private static void fill(TaskThreadStat threadStat, StringBuilder sb, boolean first, int max) {
        if (threadStat != null) {
            final TaskThreadStat root = threadStat.getRoot();
            // If root is null then threadStat is the initial execute() callingThread, 
            // no need to add its index
            if (root != null) {
                final String ix = String.valueOf(threadStat.getLocalIndex());
                final int lenBefore = sb.length();
                if (first || lenBefore + ix.length() < max) {
                    fill(root,sb,false,max);
                    if (lenBefore != sb.length()) {
                        sb.append('.');
                    }
                    sb.append(ix);
                }
                else {
                    sb.append(".");
                }
            }
        }
    }

    @Override
    public void notifyThreadEnd(TaskThreadStat threadStat) {
        
    }
}
