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


/**
 * Tests TaskStat profiling
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
        void sleep(long delay) {
            try {
                Thread.sleep(delay);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private class SleepChild extends Sleeper {
        private final long time;
        SleepChild(long time) {
            this.time = time;
        }
        void sleep() {
            sleep(time);
        }
    }
    
    TaskStat.Path createExpectedPath(TaskStat.Graph graph, String...segments) {
        TaskStat.Path path = graph.path();
        for (String next: segments) {
            path.segment(next);
        }
        return path;
    }
    
    private void updateExpectedPath(TaskStat.Path path, long...vs) {
        for (int i=0; i< vs.length; i++) {
            path.segments.get(i).update(vs[i]);
        }
    }

    @Test
    public void testOne() {
        final int TIME = 100;
        class A extends Sleeper {
            final static String SEGMENT = "A.exec()";
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
        TaskStat.Path path = graph.path(); //A.PATH);
        
        TaskStat.Segment segment = path.segment(A.SEGMENT);
        path.called = segment.called = 1;
        path.minTime = segment.minTime = TIME;
        path.aggregateTime = path.maxTime = segment.aggregateTime = segment.maxTime = TIME;

        FlexEq feq = new FlexEq();
        assertTrue(feq.apxOut(exp,got));
    }

    class TwoSeparate {
        class A extends SleepChild {
            final static String SEGMENT = "A.exec()";
            A(long time) {super(time);}
            @Work public void exec() {
                sleep();
            }
        }
        class B extends SleepChild {
            final static String SEGMENT = "B.exec()";
            B(long time) {super(time);}
            @Work public void exec() {
                sleep();
            }
        }
        
        final String orcName;
        final TaskStat.Graph graph;
        
        // Possible paths in this graph
        final TaskStat.Path aPath;
        final TaskStat.Path bPath;
        
        TwoSeparate(TaskStat exp, String orcName) {
            this.orcName = orcName;
            graph = exp.graph(orcName);
            aPath = createExpectedPath(graph,A.SEGMENT);
            bPath = createExpectedPath(graph,B.SEGMENT);
        }
        
        void run(long aTime, long bTime) {
            Orchestrator orc = Orchestrator.create().name(orcName);
            orc.addWork(new A(aTime));
            orc.addWork(new B(bTime));
            orc.execute();
            updateExpectedPath(aPath,aTime);
            updateExpectedPath(bPath,bTime);
            aPath.update(aTime);
            bPath.update(bTime);
        }
        
        void setExpectAFirst() {
            graph.getPaths().set(0,aPath);
            graph.getPaths().set(1,bPath);
        }
        void setExpectBFirst() {
            graph.getPaths().set(0,bPath);
            graph.getPaths().set(1,aPath);
        }
    }
    
    @Test
    public void testTwoSeparateAFirst() {
        TaskStat exp = new TaskStat();
        TwoSeparate twoSep = new TwoSeparate(exp,"foo");
        twoSep.run(200,100);
        twoSep.setExpectAFirst();

        TaskStat got = Orchestrator.stat().getStats();

        FlexEq feq = new FlexEq();
        assertTrue(feq.apxOut(exp,got));
    }    

    @Test
    public void testTwoSeparateBFirst() {
        TaskStat exp = new TaskStat();
        TwoSeparate twoSep = new TwoSeparate(exp,"foo");
        twoSep.run(100,200);
        twoSep.setExpectBFirst();

        TaskStat got = Orchestrator.stat().getStats();

        FlexEq feq = new FlexEq();
        assertTrue(feq.apxOut(exp,got));
        System.out.println(feq.apx(exp,got).outline);
    }    

    class TwoPath {
        class A extends SleepChild {
            final static String SEGMENT = "A.exec()";
            A(long time) {super(time);}
            @Work public void exec() {
                sleep();
            }
        }
        class B extends SleepChild {
            final static String SEGMENT = "B.exec(A)";
            B(long time) {super(time);}
            @Work public void exec(A a) {
                sleep();
            }
        }
        
        final String orcName;
        final TaskStat.Graph graph;
        
        // Possible paths in this graph
        final TaskStat.Path only;
        
        TwoPath(TaskStat exp, String orcName) {
            this.orcName = orcName;
            graph = exp.graph(orcName);
            only = createExpectedPath(graph,A.SEGMENT,B.SEGMENT);
        }
        
        void run(TaskStat.Path path, long aTime, long bTime) {
            Orchestrator orc = Orchestrator.create().name(orcName);
            orc.addWork(new A(aTime));
            orc.addWork(new B(bTime));
            orc.execute();
            updateExpectedPath(path,aTime,bTime);
            path.update(aTime+bTime);
        }
    }

    @Test
    public void testTwoPathOnce() {
        TaskStat exp = new TaskStat();
        TwoPath twoPath = new TwoPath(exp,"foo");
        twoPath.run(twoPath.only,100,100);

        TaskStat got = Orchestrator.stat().getStats();

        FlexEq feq = new FlexEq();
        assertTrue(feq.apxOut(exp,got));
        
        System.out.println(feq.apx(exp,got).outline);
    }
    
    @Test
    public void testTwoPathMulti() {
        TaskStat exp = new TaskStat();
        TwoPath twoPath = new TwoPath(exp,"foo");
        twoPath.run(twoPath.only,100,100);
        twoPath.run(twoPath.only,100,100);
        twoPath.run(twoPath.only,100,100);

        TaskStat got = Orchestrator.stat().getStats();

        FlexEq feq = new FlexEq();
        assertTrue(feq.apxOut(exp,got));
        
        System.out.println(feq.apx(exp,got).outline);
    }
    
    @Test
    public void testDiamondLeft() {
        TaskStat exp = new TaskStat();
        Diamond d = new Diamond(exp,"foo");
        d.run(d.left,200,100);
        d.run(d.right,100,200);
        d.run(d.left,200,100);
        d.run(d.right,0,50);
        d.run(d.left,200,100);
        d.setExpectLeftFirst();

        TaskStat got = Orchestrator.stat().getStats();

        FlexEq feq = new FlexEq();
        assertTrue(feq.apxOut(exp,got));
        
        System.out.println(feq.apx(exp,got).outline);
    }
    
    @Test
    public void testDiamondRight() {
        TaskStat exp = new TaskStat();
        Diamond d = new Diamond(exp,"foo");
        d.run(d.left,200,100);
        d.run(d.right,100,200);
        d.run(d.right,200,400);
        d.setExpectRightFirst();

        TaskStat got = Orchestrator.stat().getStats();

        FlexEq feq = new FlexEq();
        assertTrue(feq.apxOut(exp,got));
        
        System.out.println(feq.apx(exp,got).outline);
    }
    
    class Diamond {
        class Top extends Sleeper {
            final static String SEGMENT = "Top.exec()";
            @Work public void exec() {
            }
        }
        class Left extends SleepChild {
            final static String SEGMENT = "Left.exec(Top)";
            Left(long time) {super(time);}
            @Work public void exec(Top top) {
                sleep();
            }
        }
        class Right extends SleepChild {
            final static String SEGMENT = "Right.exec(Top)";
            Right(long time) {super(time);}
            @Work public void exec(Top top) {
                sleep();
            }
        }
        class Bottom extends Sleeper {
            final static String SEGMENT = "Bottom.exec(Left,Right)";
            @Work public void exec(Left x, Right y) {
            }
        }
        
        final String orcName;
        final TaskStat.Graph graph;
        
        // Possible paths in this graph
        final TaskStat.Path right;
        final TaskStat.Path left;
        
        Diamond(TaskStat exp, String orcName) {
            this.orcName = orcName;
            graph = exp.graph(orcName);
            left = createExpectedPath(graph,Top.SEGMENT,Left.SEGMENT,Bottom.SEGMENT);
            right = createExpectedPath(graph,Top.SEGMENT,Right.SEGMENT,Bottom.SEGMENT);
        }
        
        void run(TaskStat.Path path, final long leftTime, final long rightTime) {
            Orchestrator orc = Orchestrator.create().name(orcName);
            orc.addWork(new Top());
            orc.addWork(new Left(leftTime));
            orc.addWork(new Right(rightTime));
            orc.addWork(new Bottom());
            orc.execute();
            long time = Math.max(leftTime,rightTime);
            updateExpectedPath(path,0,time,0);
            path.update(time);
        }
        
        void setExpectLeftFirst() {
            graph.getPaths().set(0,left);
            graph.getPaths().set(1,right);
        }
        void setExpectRightFirst() {
            graph.getPaths().set(0,right);
            graph.getPaths().set(1,left);
        }
    }
}
