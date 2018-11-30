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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ebay.bascomtask.flexeq.FlexEq;

/**
 * Expresses timing results from profiling.
 * 
 * @author bremccarthy
 */
public class TaskStat {
    
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
    
    public List<Graph> graphs = new ArrayList<>();
    public List<Segment> tasks = new ArrayList<>();
    public int nonReportableOrchestrations;

    public static abstract class Timing {
        public int called = 0;
        
        @FlexEq.LongInRange(40)
        public long aggregateTime;
        
        @FlexEq.LongInRange(40)
        public long minTime = -1; // >=0 only after at least one run
        
        @FlexEq.LongInRange(40)
        public long maxTime;
        
        public Timing called(int called) {
            this.called = called;
            return this;
        }
        public Timing agg(long agg) {
            this.aggregateTime = agg;
            return this;
        }
        public Timing min(long min) {
            this.minTime = min;
            return this;
        }
        /*
        public Timing avg(long avg) {
            this.avgTime = avg;
            return this;
        }
        */
        public Timing max(long max) {
            this.maxTime = max;
            return this;
        }
        
        public long getAvgTime() {
            return aggregateTime / called;
        }
        
        void setFrom(Timing timing) {
            this.called = timing.called;
            this.aggregateTime = timing.aggregateTime;
            this.minTime = timing.minTime;
            //this.avgTime = timing.avgTime;
            this.maxTime = timing.maxTime;
        }
        
        void update(long duration) {
            called++;
            aggregateTime += duration;
            if (minTime < 0 || duration < minTime) {
                minTime = duration;
            }
            if (duration > maxTime) {
                maxTime = duration;
            }
        }
        
        Segment segmentFrom(String task, Timing timing) {
            Segment segment = new Segment(task);
            //segment.task = task;
            segment.setFrom(timing);
            return segment;
        }
    }
    
    /**
     * The combined results from operations on orchestrators with the same name.
     */
    static public class Graph {
        public final String name;
        private final List<Path> paths = new ArrayList<>();
        
        Graph(String name) {
            this.name = name;
        }
        
        public List<Path> getPaths() {
            //return paths.values();
            return paths;
        }
        
        public void setPaths(Collection<Path> paths) {
            if (paths==null) {
                this.paths.clear();
            }
            else {
                for (Path next: paths) {
                    //this.paths.put(next.name,next);
                    this.paths.add(next);
                }
            }
        }
        
        /*
        Path ensurePath(String name) {
            Path path = paths.get(name);
            if (path == null) {
                path = new Path();
                path.name = name;
                paths.put(name,path);
            }
            return path;
        }
        */
        
        public Path path() {
            Path path = new Path();
            paths.add(path);
            return path;
        }
        
        /*
        public Path path(String name) {
            Path path = new Path();
            path.name = name;
            paths.put(name,path);
            return path;
        }
        */
        /*
        public Graph copy() {
            Graph graph = new Graph(this.name);
            graph.paths = new ArrayList<>(this.paths);
            /*
            graph.paths = new HashMap<>();
            for (Path next: this.paths.values()) {
                graph.paths.put(next.name,next.copy());
            }
             *\/
            return graph;
        }
    */
    }
    
    /**
     * An ordered list of task segments within an orchestrator graph each connected by dependency.
     * Every node in a graph constitutes at least one path, and if it has inputs it will be the
     * endpoint in multiple paths. For example, a diamond would have 5 paths:
     * <ul>
     * <li> Top
     * <li> Top-Left
     * <li> Top-Right
     * <li> Top-Left-Bottom
     * <li> Top-Right-Bottom
     * </ul>
     */
    static public class Path extends Timing {
        //public String name;
        public List<Segment> segments = null; // = new ArrayList<>();
        
        public Segment segment(String task) {
            Segment segment = new Segment(task);
            //segment.task = task;
            if (segments==null) {
                segments = new ArrayList<>();
            }
            segments.add(segment);
            return segment;
        }
        
        public Path copy() {
            Path path = new Path();
            //path.name = this.name;
            path.setFrom(this);
            path.segments = new ArrayList<>();
            for (Segment next: segments) {
                path.segments.add(next.copy());
            }
            return path;
        }
    }
    
    /**
     * Timings for a task in a graph. Every path has at least one segment. 
     * Only root tasks will have only one segment.
     */
    static public class Segment extends Timing {
        public final String task;
        
        Segment(String task) {
            this.task = task;
        }
        
        public Segment copy() {
            Segment segment  = new Segment(this.task);
            //segment.task = task;
            segment.setFrom(this);
            return segment;
        }
    }
    
    /**
     * Convenience method for tests.
     * @param name of graph
     * @return existing or newly-created graph
     */
    Graph graph(String name) {
        for (Graph next: graphs) {
            if (next.name.equals(name)) {
                return next;
            }
        }
        Graph graph = new Graph(name);
        graphs.add(graph);
        return graph;
    }
}
