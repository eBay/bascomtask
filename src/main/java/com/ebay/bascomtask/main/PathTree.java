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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps TaskMethodClosures to paths used for profiling, where each such closure
 * traces a path through a graph representing the longest sequence through and
 * including that closure. All orchestrators with the same name feed into a 
 * single PathTree instance.
 * 
 * @author bremccarthy
 */
class PathTree {
    /**
     * Each node is associate with a particular task method on a user POJO. 
     */
    private final Call call;
    
    /**
     * Our predecessor 
     */
    private final PathTree root;
    
    /**
     * Each node can have ann number of children, however we optimize since most
     * nodes will only have one or a few children. 
     */
    private Call firstMethod= null;
    private PathTree firstTree = null;
    private Call secondMethod = null;
    private PathTree secondTree = null;
    
    /**
     * Stores n>=3 children if needed
     */
    private Map<Call,PathTree> map = null;
    
    /**
     * The path at this point in the tree. Not all nodes will have one because
     * only terminating closures (those with no outgoing dependencies) are
     * recorded.  
     */
    private TaskStat.Path path = null;
    
    PathTree(Call call, PathTree root) {
        this.call = call;
        this.root = root;
    }
    
    @Override
    public String toString() {
        if (call==null) return "PATH";
        // Not efficient, but only expecting to be called during debugging
        return root.toString() + ">" + call.signature();
    }
    
    /**
     * Finds existing path else creates one reflecting the closure path.
     * @param closure
     * @return a never-null valid PathTree node
     */
    PathTree find(TaskMethodClosure closure) {
        PathTree tree = this;
        TaskMethodClosure pred = closure.getLongestIncoming();
        if (pred != null) {
            tree = find(pred);
        }
        return tree.more(closure);
    }
    
    private PathTree more(TaskMethodClosure closure) {
        PathTree tree;
        Call call = closure.getCallInstance().getCall();        
        if (firstMethod==null) {
            firstMethod = call;
            tree = firstTree = new PathTree(call,this);
        }
        else if (firstMethod == call) {
            tree = firstTree;
        }
        else if (secondMethod==null) {
            secondMethod = call;
            tree = secondTree = new PathTree(call,this);
        }
        else if (secondMethod == call) {
            tree = secondTree;
        }
        else {
            if (map==null) {
                map = new HashMap<>();
            }
            tree = map.get(call);
            if (tree==null) {
                tree = new PathTree(call,this);
                map.put(call,tree);
            }
        }
        return tree;
    }
    
    /**
     * Record the execution timing of given closure, which is assumed to be the last closure 
     * in its execution path.
     * @param closure to record
     * @return
     */
    synchronized PathTree record(TaskMethodClosure closure) {
        PathTree tree = find(closure);
        TaskStat.Path path = tree.path;
        if (path==null) {
            path = tree.path = new TaskStat.Path();
        }
        path.update(closure.getLongestDuration());
        tree.walk(closure,tree,0);
        return tree;
    }
    
    private int walk(TaskMethodClosure closure, PathTree leaf, int depth) {
        int pos = 0;
        TaskMethodClosure pred = closure.getLongestIncoming();
        if (pred == null) {
            if (leaf.path.segments == null) {
                leaf.path.segments = new ArrayList<>(depth);
            }
        }
        else {
            pos = root.walk(pred,leaf,depth+1);
        }
        TaskStat.Segment segment;
        if (leaf.path.segments.size() <= pos) {
            String mfs = closure.getMethodFormalSignature();
            segment = new TaskStat.Segment(mfs);
            leaf.path.segments.add(segment);
        }
        else {
            segment = leaf.path.segments.get(pos);
        }
        long duration = closure.getDurationMs();
        segment.update(duration);
        return pos+1;
    }
    
    synchronized TaskStat.Graph stat() {
        return null;
    }
    
    private static Comparator<TaskStat.Path> ascendingAvgTimecomparator = new Comparator<TaskStat.Path>() {
        @Override
		public int compare(TaskStat.Path path1, TaskStat.Path path2) {
			long t1 = path1.getAvgTime();
			long t2 = path2.getAvgTime();
			return t1==t2 ? 0 : t1>t2 ? -1 : 1;
		}        
    };
    
    void populate(TaskStat.Graph graph) {
        List<TaskStat.Path> paths = graph.getPaths();
        extractPaths(paths);
        Collections.sort(paths,ascendingAvgTimecomparator);
        // TODO limit
    }
    
    private void extractPaths(List<TaskStat.Path> paths) {
        if (path != null) {
            paths.add(path);
        }
        if (firstTree != null) {
            firstTree.extractPaths(paths);
            if (secondTree != null) {
                secondTree.extractPaths(paths);
                if (map != null) {
                    for (PathTree next: map.values()) {
                        next.extractPaths(paths);
                    }
                }
            }
        }
    }

    /**
     * 
     * @return possibly null path result
     */
    TaskStat.Path getPath() {
        return path;
    }

    int getNumberOfDirectChildren() {
        if (firstMethod == null) return 0;
        if (secondMethod == null) return 1;
        if (map == null) return 2;
        return map.size() + 2;
    }
}


