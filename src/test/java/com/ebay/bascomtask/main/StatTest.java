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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.ebay.bascomtask.annotations.Work;


/**
 * Tests related to BascomTask configuration.
 * 
 * @author brendanmccarthy
 */
public class StatTest {

    @Before
    public void before() {
        Orchestrator.stat().reset();
    }

    class Sleeper {
        public void sleep(int delay) {
            try {
                Thread.sleep(delay);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    @Test
    public void testOne() {
        class A extends Sleeper {
            @Work public void exec() {
                sleep(100);
            }
        }
        final String ORC_NAME = "foo";
        Orchestrator orc = Orchestrator.create().name(ORC_NAME);
        A a = new A();
        ITask taskA = orc.addWork(a);
        orc.execute();

        TaskStat got = Orchestrator.stat().getStats();        
        
        TaskStat exp = new TaskStat();
        TaskStat.Graph graph = exp.graph(ORC_NAME);
        TaskStat.Path path = graph.path(taskA.getName());
        
        /*TaskStat.Segment segment = */path.segment(taskA.getName());

        assertEquals(exp,got);
    }
    
    @Test
    public void testTwoSeparate() {
        class A extends Sleeper {
            @Work public void exec() {
                sleep(100);
            }
        }
        class B extends Sleeper {
            @Work public void exec() {
                sleep(200);
            }
        }
        final String ORC_NAME = "foo";
        Orchestrator orc = Orchestrator.create().name(ORC_NAME);
        A a = new A();
        ITask taskA = orc.addWork(a);
        B b = new B();
        ITask taskB = orc.addWork(b);
        orc.execute();

        TaskStat got = Orchestrator.stat().getStats();        
        
        TaskStat exp = new TaskStat();
        TaskStat.Graph graph = exp.graph(ORC_NAME);
        graph.path(taskA.getName());
        TaskStat.Path pathB = graph.path(taskB.getName());
        
        pathB.segment(taskB.getName());

        assertEquals(exp,got);
    }

    @Test
    public void testTwoPath() {
        class A extends Sleeper {
            @Work public void exec() {
                sleep(100);
            }
        }
        class B extends Sleeper {
            @Work public void exec(A a) {
                sleep(200);
            }
        }
        final String ORC_NAME = "foo";
        Orchestrator orc = Orchestrator.create().name(ORC_NAME);
        A a = new A();
        ITask taskA = orc.addWork(a);
        B b = new B();
        ITask taskB = orc.addWork(b);
        orc.execute();

        TaskStat got = Orchestrator.stat().getStats();        
        
        TaskStat exp = new TaskStat();
        TaskStat.Graph graph = exp.graph(ORC_NAME);
        graph.path(taskA.getName());
        TaskStat.Path pathB = graph.path(taskB.getName());
        
        pathB.segment(taskB.getName());

        assertEquals(exp,got);
    }
    
    @Test
    public void testDiamond() {
        class A extends Sleeper {
            @Work public void exec() {
            }
        }
        class B extends Sleeper {
            @Work public void exec(A a) {
                sleep(200);
            }
        }
        class C extends Sleeper {
            @Work public void exec(A a) {
                sleep(100);
            }
        }
        class D extends Sleeper {
            @Work public void exec(B b, C c) {
            }
        }
        final String ORC_NAME = "foo";
        Orchestrator orc = Orchestrator.create().name(ORC_NAME);
        A a = new A();
        ITask taskA = orc.addWork(a);
        B b = new B();
        ITask taskB = orc.addWork(b);
        C c = new C();
        ITask taskC = orc.addWork(c);
        D d = new D();
        ITask taskD = orc.addWork(d);
        orc.execute();

        TaskStat got = Orchestrator.stat().getStats();        
        
        TaskStat exp = new TaskStat();
        TaskStat.Graph graph = exp.graph(ORC_NAME);
        graph.path(taskA.getName());
        TaskStat.Path pathB = graph.path(taskB.getName());
        
        pathB.segment(taskB.getName());

        assertEquals(exp,got);
    }
    
}
