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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ebay.bascomtask.main.TaskStat.*;

public class StatKeeper {
    private Map<String,Graph> trackers = new HashMap<>();

    public synchronized void reset() {
        trackers.clear();
    }

    /*
    private Comparator<TaskMethodClosure> comparator = new Comparator<TaskMethodClosure>() {
        @Override
		public int compare(TaskMethodClosure c1, TaskMethodClosure c2) {
			long t1 = c1.getDurationMs();
			long t2 = c2.getDurationMs();
			return t1==t2 ? 0 : t1<t2 ? -1 : 1;
		}        
    };
    */
    
    public TaskStat getStats() {
        TaskStat stat = new TaskStat();
        stat.graphs = new ArrayList<>();
        for (Graph next: trackers.values()) {
            stat.graphs.add(next.copy());
        }
        return stat;
    }
    
    synchronized void record(Orchestrator orc, TaskMethodClosure closure) {
        String name = orc.getName();
        if (name != null) {
            Graph graph = trackers.get(name);
            if (graph == null) {
                graph = new Graph();
                graph.name = name;
                trackers.put(name,graph);
            }

            String pathName = closure.getLongestIncomingPath();
            TaskStat.Path path = graph.ensurePath(pathName);
            if (path.segments.isEmpty()) {
                populate(path.segments,closure);
            }
            else {
                apply(path.segments,0,closure);    
            }
            
            long duration = closure.getLongestDuration();
            path.update(duration);
        }
    }

    private void populate(List<TaskStat.Segment> segments, TaskMethodClosure closure) {
        if (closure != null) {
            TaskStat.Segment segment = new TaskStat.Segment();
            segment.task = closure.getMethodFormalSignature(); 
            populate(segments,closure.getLongestIncoming());
            segments.add(segment);
            segment.update(closure.getDurationMs());
        }
    }

    private void apply(List<Segment> segments, int pos, TaskMethodClosure closure) {
        if (closure != null) {
            apply(segments,pos+1,closure.getLongestIncoming());
            TaskStat.Segment segment = segments.get(pos);
            segment.update(closure.getDurationMs());
        }
    }
}
