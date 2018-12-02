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

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.ebay.bascomtask.annotations.Work;

/**
 * Test PathTree.
 * 
 * @author brendanmccarthy
 */
public class PathTreeTest {
    
    class A {@Work void exec() {}}
    class B {@Work void exec() {}}
    class C {@Work void exec() {}}
    class D {@Work void exec() {}}

    private final Call.Instance a;
    private final Call.Instance b;
    private final Call.Instance c;
    private final Call.Instance d;
    
    public PathTreeTest() {
        Orchestrator orc = Orchestrator.create();
        a = callOf(orc,new A());
        b = callOf(orc,new B());
        c = callOf(orc,new C());
        d = callOf(orc,new D());
    }
    
    private Call.Instance callOf(Orchestrator orc, Object pojo) {
        Task.Instance taskInstance = (Task.Instance)orc.addWork(pojo);
        for (Call.Instance callInstance: taskInstance.calls) {
            Call call = callInstance.getCall();
            String nm = call.getMethodName();
            if ("exec".equals(nm)) {
                return callInstance;
            }
        }
        throw new RuntimeException("Unexpected fail");
    }
    
    private static final Object[] EMPTY_ARGS = {};

    TestMethodClosure create(Call.Instance ci, final long duration, TestMethodClosure parent) {
        TestMethodClosure closure = new TestMethodClosure();
        closure.setDurationMs(duration);
        closure.initCall(ci,EMPTY_ARGS,parent);
        return closure;
    }    
    
    class TestMethodClosure extends TaskMethodClosure {
        
        TestMethodClosure child(Call.Instance ci, long duration) {
            return create(ci,duration,this);
        }
        
    };
    
    TestMethodClosure root(Call.Instance ci, final long duration) {
        return create(ci,duration,null);
    }

    private void check(PathTree tree, int len, int agg, int called) {
        TaskStat.Path path = tree.getPath();
        assertEquals(len,path.segments.size());
        assertEquals(agg,path.aggregateTime);
        assertEquals(called,path.called);
        
        assertTrue(tree.toString().length() > 0);
        
        TaskStat.Graph graph = new TaskStat.Graph("doesn't-matter");
        tree.getRoot().populate(graph);
    }

    @Before
    public void init() {
        base = new PathTree(null,null);
    }
    
    private PathTree base;
    
    @Test
    public void testOne() {
        PathTree got = base.record(root(a,100));
        check(got,1,100,1);
    }
    
    @Test
    public void testTwo() {
        PathTree got = base.record(root(a,100).child(b,100));
        check(got,2,200,1);
    }
    
    @Test
    public void testThreeTwice() {
        PathTree got = base.record(root(a,100).child(b,100).child(c,100));
        got = base.record(root(a,100).child(b,100).child(c,100));
        check(got,3,600,2);
    }
    
    @Test
    public void testTwoRoots() {
        PathTree got = base.record(root(a,100).child(c,100));
        check(got,2,200,1);
        got = base.record(root(b,100).child(b,100));
        check(got,2,200,1);
    }
    
    @Test
    public void testTwoEnds() {
        PathTree got = base.record(root(a,100).child(b,100));
        check(got,2,200,1);
        got = base.record(root(a,100).child(c,100));
        check(got,2,200,1);
    }

    @Test
    public void testDiamondRight() {
        PathTree got = base.record(root(a,100).child(b,100).child(d,100));
        check(got,3,300,1);
        got = base.record(root(a,100).child(c,200).child(d,100));
        check(got,3,400,1);
    }
    
    @Test
    public void testDiamondLeft() {
        PathTree got = base.record(root(a,100).child(b,100).child(d,200));
        check(got,3,400,1);
        got = base.record(root(a,100).child(c,100).child(d,100));
        check(got,3,300,1);
    }
    
    @Test
    public void testWide() {
        PathTree got = base.record(root(a,100));
        got = base.record(root(b,100));
        got = base.record(root(c,100));        
        got = base.record(root(d,100));        
        check(got,1,100,1);
    }
}

