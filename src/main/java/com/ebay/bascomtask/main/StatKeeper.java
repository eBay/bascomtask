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

public class StatKeeper {
    private Map<String,PathTree> trackers = new HashMap<>();

    public synchronized void reset() {
        trackers.clear();
    }

    public synchronized TaskStat getStats() {
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
    
    synchronized void record(Orchestrator orc, TaskMethodClosure closure) {
        System.out.println("Record " + orc.getName());
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
