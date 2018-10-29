package com.ebay.bascomtask.main;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TaskStat {
    
    public List<Graph> graphs = new ArrayList<>();
    public List<Segment> tasks = new ArrayList<>();
    public int nonReportableOrchestrations;
    
    @Override
    public boolean equals(Object o) {
        if (this==o) return true;
        if (o instanceof TaskStat) {
            TaskStat that = (TaskStat)o;
            if (Objects.equals(this.graphs,that.graphs)) return false;
            return true;
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(graphs,tasks,nonReportableOrchestrations);
    }
    
    public static abstract class Timing {
        public int called;
        public long aggregateTime;
        public long minTime;
        public long avgTime;
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
        public Timing avg(long avg) {
            this.avgTime = avg;
            return this;
        }
        public Timing max(long max) {
            this.maxTime = max;
            return this;
        }
        
        void setFrom(Timing timing) {
            this.called = timing.called;
            this.aggregateTime = timing.aggregateTime;
            this.minTime = timing.minTime;
            this.avgTime = timing.avgTime;
            this.maxTime = timing.maxTime;
        }
        
        /* 
         * Uses non-strict comparison for time-related values.
         */
        @Override
        public boolean equals(Object o) {
            if (this==o) return true;
            if (o instanceof Timing) {
                Timing that = (Timing)o;
                if (this.called!=that.called) return false;
                if (!inRange(this.aggregateTime,that.aggregateTime)) return false;
                if (!inRange(this.minTime,that.minTime)) return false;
                if (!inRange(this.avgTime,that.avgTime)) return false;
                if (!inRange(this.maxTime,that.maxTime)) return false;
                return true;
            }
            return false;
        }
        
        private boolean inRange(long v1, long v2) {
            return Math.abs(v1-v2) < 20;
        }

        @Override
        public int hashCode() {
            return Objects.hash(called,aggregateTime,minTime,avgTime,maxTime);
        }
        
        void update(long duration) {
            called++;
            aggregateTime += duration;
            if (duration < minTime) {
                minTime = duration;
            }
            else if (duration > maxTime) {
                maxTime = duration;
            }
        }
    }
    
    static public class Graph {
        public String name;
        private Map<String,Path> paths = new HashMap<>();
        
        public Collection<Path> getPaths() {
            return paths.values();
        }
        
        public void setPaths(Collection<Path> paths) {
            if (paths==null) {
                this.paths.clear();
            }
            else {
                for (Path next: paths) {
                    this.paths.put(next.name,next);
                }
            }
        }
        
        Path ensurePath(String name) {
            Path path = paths.get(name);
            if (path == null) {
                path = new Path();
                path.name = name;
                paths.put(name,path);
            }
            return path;
        }
        
        public Path path(String name) {
            Path path = new Path();
            path.name = name;
            paths.put(name,path);
            return path;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this==o) return true;
            if (o instanceof Graph) {
                Graph that = (Graph)o;
                if (!Objects.equals(this.name,that.name)) return false;
                if (!Objects.equals(this.paths,that.paths)) return false;
                return true;
            }
            return false;
        }
        @Override
        public int hashCode() {
            return Objects.hash(name,paths);
        }

        public Graph copy() {
            Graph graph = new Graph();
            graph.name = this.name;
            graph.paths = new HashMap<>();
            for (Path next: this.paths.values()) {
                graph.paths.put(next.name,next.copy());
            }
            return graph;
        }
    }
    
    static public class Path extends Timing {
        public String name;
        public List<Segment> segments = new ArrayList<>();
        
        public Segment segment(String task) {
            Segment segment = new Segment();
            segment.task = task;
            segments.add(segment);
            return segment;
        }
        
        public Path copy() {
            Path path = new Path();
            path.name = this.name;
            path.setFrom(this);
            path.segments = new ArrayList<>();
            for (Segment next: segments) {
                path.segments.add(next.copy());
            }
            return path;
        }

        @Override
        public boolean equals(Object o) {
            if (this==o) return true;
            if (!super.equals(o)) return false;
            if (o instanceof Path) {
                Path that = (Path)o;
                if (!Objects.equals(this.name,that.name)) return false;
                if (!Objects.equals(this.segments,that.segments)) return false;
                return true;
            }
            return false;
        }
        @Override
        public int hashCode() {
            return Objects.hash(name,segments);
        }
    }
    
    static public class Segment extends Timing {
        public String task;
        
        @Override
        public boolean equals(Object o) {
            if (this==o) return true;
            if (!super.equals(o)) return false;
            if (o instanceof Segment) {
                Segment that = (Segment)o;
                if (!Objects.equals(this.task,that.task)) return false;
                return true;
            }
            return false;
        }
        public Segment copy() {
            Segment segment  = new Segment();
            segment.task = task;
            segment.setFrom(this);
            return segment;
        }
        @Override
        public int hashCode() {
            return Objects.hash(task);
        }
    }
    
    public Graph graph(String name) {
        Graph graph = new Graph();
        graph.name = name;
        graphs.add(graph);
        return graph;
    }
}
