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
import com.ebay.bascomtask.flexeq.FlexEq;
import com.ebay.bascomtask.flexeq.FlexEq.Output;
import com.ebay.bascomtask.main.TaskStat.Graph;


/**
 * Tests related to TaskStat profile.
 * 
 * @author brendanmccarthy
 */
public class StatTest {

    @Before
    public void before() {
        Orchestrator.stat().reset();
    }

    /**
     * Common task superclass that provides a delay method since the primary things tested in
     * this class involve relative timings.
     */
    private class Sleeper {
        public void sleep(long delay) {
            try {
                Thread.sleep(delay);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    private class PathTimer {
        private final TaskStat.Path path;
        PathTimer(TaskStat.Path path) {
            this.path = path;
        }
        PathTimer step(String segmentName, long time) {
            path.segment(segmentName).update(time);
            return this;
        }
    }
    
    /**
     * Utility to add a path with a return result that enables segments to be added fluent style 
     * @param graph
     * @param name
     * @param time
     * @return temp object on which step() can be called
     */
    private PathTimer pathTime(Graph graph, String name, long time) {
        TaskStat.Path path = graph.path(name);
        path.update(time);
        return new PathTimer(path);
    }
    
    private final static String SEP = ">";
    
    @Test
    public void testOne() {
        final int TIME = 100;
        class A extends Sleeper {
            final static String SEGMENT = "A.exec()";
            final static String PATH = SEGMENT;
            @Work public void exec() {
                System.out.println("HIT");
                sleep(TIME);
            }
        }
        final String ORC_NAME = "foo";
        Orchestrator orc = Orchestrator.create().name(ORC_NAME);
        orc.addWork(new A());
        orc.execute();

        TaskStat got = Orchestrator.stat().getStats();        
        
        TaskStat exp = new TaskStat();
        TaskStat.Graph graph = exp.graph(ORC_NAME);
        TaskStat.Path path = graph.path(A.PATH);
        
        TaskStat.Segment segment = path.segment(A.SEGMENT);
        path.called = segment.called = 1;
        path.aggregateTime = path.maxTime = segment.aggregateTime = segment.maxTime = TIME;

        FlexEq feq = new FlexEq();
        assertTrue(feq.apxOut(exp,got));
    }
    
    @Test
    public void testTwoSeparate() {
        final int TIME = 100;
        class A extends Sleeper {
            final static String SEGMENT = "A.exec()";
            final static String PATH = SEGMENT;
            @Work public void exec() {
                sleep(TIME);
            }
        }
        class B extends Sleeper {
            final static String SEGMENT = "B.exec()";
            final static String PATH = SEGMENT;
            @Work public void exec() {
                sleep(TIME);
            }
        }
        final String ORC_NAME = "foo";
        Orchestrator orc = Orchestrator.create().name(ORC_NAME);
        orc.addWork(new A());
        orc.addWork(new B());
        orc.execute();

        TaskStat got = Orchestrator.stat().getStats();        
        
        TaskStat exp = new TaskStat();
        TaskStat.Graph graph = exp.graph(ORC_NAME);
        
        pathTime(graph,A.PATH,TIME).step(A.SEGMENT,TIME);
        pathTime(graph,B.PATH,TIME).step(B.SEGMENT,TIME);
        
        FlexEq feq = new FlexEq();
        assertTrue(feq.apxOut(exp,got));
    }

    @Test
    public void testTwoPath() {
        final int TIME = 100;
        class A extends Sleeper {
            final static String SEGMENT = "A.exec()";
            final static String PATH = SEGMENT;
            @Work public void exec() {
                sleep(100);
            }
        }
        class B extends Sleeper {
            final static String SEGMENT = "B.exec(A)";
            final static String PATH = A.PATH + SEP + SEGMENT;
            @Work public void exec(A a) {
                sleep(100);
            }
        }
        final String ORC_NAME = "foo";
        Orchestrator orc = Orchestrator.create().name(ORC_NAME);
        orc.addWork(new A());
        orc.addWork(new B());
        orc.execute();

        TaskStat got = Orchestrator.stat().getStats();        
        
        TaskStat exp = new TaskStat();
        TaskStat.Graph graph = exp.graph(ORC_NAME);
        
        pathTime(graph,A.PATH,TIME).step(A.SEGMENT,TIME);
        pathTime(graph,B.PATH,TIME*2).step(A.SEGMENT,TIME).step(B.SEGMENT,TIME);
        
        FlexEq feq = new FlexEq();
        assertTrue(feq.apxOut(exp,got));
        
        Output z = feq.apx(exp,got);
        System.out.println(z.outline);
    }
    
    @Test
    public void testDiamond() {
        class A extends Sleeper {
            final static String SEGMENT = "A.exec()";
            final static String PATH = SEGMENT;
            final static long TIME = 0;
            @Work public void exec() {
            }
        }
        class B extends Sleeper {
            final static String SEGMENT = "B.exec(A)";
            final static String PATH = A.PATH + SEP + SEGMENT;
            final static long TIME = 200;
            @Work public void exec(A a) {
                System.out.println("This-B");
                sleep(TIME);
            }
        }
        class C extends Sleeper {
            final static String SEGMENT = "C.exec(A)";
            final static String PATH = A.PATH + SEP + SEGMENT;
            final static long TIME = 100;
            @Work public void exec(A a) {
                sleep(TIME);
            }
        }
        class D extends Sleeper {
            final static String SEGMENT = "D.exec(B,C)";
            final static String PATHB = B.PATH + SEP + SEGMENT;
            final static long TIME = 0;
            @Work public void exec(B b, C c) {
                System.out.println("This-D");
            }
        }
        final String ORC_NAME = "foo";
        Orchestrator orc = Orchestrator.create().name(ORC_NAME);
        orc.addWork(new A());
        orc.addWork(new B());
        orc.addWork(new C());
        orc.addWork(new D());
        orc.execute();

        TaskStat got = Orchestrator.stat().getStats();        
        
        TaskStat exp = new TaskStat();
        TaskStat.Graph graph = exp.graph(ORC_NAME);
        
        pathTime(graph,A.PATH,A.TIME).step(A.SEGMENT,A.TIME);
        pathTime(graph,B.PATH,A.TIME+B.TIME).step(A.SEGMENT,A.TIME).step(B.SEGMENT,B.TIME);
        pathTime(graph,C.PATH,A.TIME+C.TIME).step(A.SEGMENT,A.TIME).step(C.SEGMENT,C.TIME);
        pathTime(graph,D.PATHB,A.TIME+B.TIME+D.TIME).step(A.SEGMENT,A.TIME).step(B.SEGMENT,B.TIME).step(D.SEGMENT,D.TIME);
        
        FlexEq feq = new FlexEq();
        assertTrue(feq.apxOut(exp,got));
        
        System.out.println(feq.apx(exp,got).outline);
    }
}
