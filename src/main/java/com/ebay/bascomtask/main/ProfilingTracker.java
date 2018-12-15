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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Records path execution times and provides summary profiling data when profiler
 * is active (true by default; can be overridden by returning false from
 * {@link com.ebay.bascomtask.config.IBascomConfig#isProfilerActive}  
 * 
 * @author bremccarthy
 */
public class ProfilingTracker {
    private Map<String,PathTree> trackers = new HashMap<>();

    public synchronized void clear() {
        trackers.clear();
    }
    
    /**
     * Gets profiling stats.
     * @return stats since last clear
     */
    public synchronized TaskStat getStats() {
        return createStatsFrom(trackers);
    }

    /**
     * Gets profiling stats and clears current values.
     * Minimizes synchronization window.
     * @return stats since last clear
     */
    public TaskStat getThenClearStats() {
        Map<String,PathTree> copy;
        synchronized (this) {
            copy = trackers;
            trackers = new HashMap<>();
        }
        return createStatsFrom(copy);
    }
        
    private static TaskStat createStatsFrom(Map<String,PathTree> trackers) {
        TaskStat stat = new TaskStat();

        for (Entry<String,PathTree> next: trackers.entrySet()) {
            String orcName = next.getKey();
            TaskStat.Graph graph = new TaskStat.Graph(orcName);
            stat.graphs.add(graph);
            PathTree tree = next.getValue();
            tree.populate(graph);
        }
        
        return stat;
    }
    
    /**
     * Records one execution path traced from the given closure and its longest 
     * incoming closure, recursively.
     * @param orc
     * @param closure
     */
    synchronized void record(Orchestrator orc, TaskMethodClosure closure) {
        String name = orc.getName();
        if (name != null) {
            PathTree basePathTree = trackers.get(name);
            if (basePathTree == null) {
                basePathTree = new PathTree(null,null);
                trackers.put(name,basePathTree);
            }
            basePathTree.record(closure);
        }
    }
}
