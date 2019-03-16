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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.ebay.bascomtask.annotations.Count;
import com.ebay.bascomtask.annotations.Ordered;
import com.ebay.bascomtask.annotations.PassThru;
import com.ebay.bascomtask.annotations.Scope;
import com.ebay.bascomtask.annotations.Work;
import com.ebay.bascomtask.exceptions.InvalidGraph;
import com.ebay.bascomtask.exceptions.InvalidTask;
import com.ebay.bascomtask.exceptions.RuntimeGraphError;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Tests for normal and exceptional behaviors on many graph variants.
 * 
 * @author brendanmccarthy
 */
@SuppressWarnings("unused")
@SuppressFBWarnings("UMAC_UNCALLABLE_METHOD_OF_ANONYMOUS_CLASS") // Simplistic
                                                                 // rule misses
                                                                 // valid usage
public class OrchestrationTest extends PathTaskTestBase {

    @Test
    public void testEmpty() {
        verify(0);
    }

    @Test
    public void testNoMethods() {
        class A extends PathTask {
        }
        PathTask a = track.work(new A());
        verify(0);
    }

    @Test(expected = InvalidTask.NameConflict.class)
    public void testNameConflict() {
        class A extends PathTask {
        }
        track.work(new A()).name("foo");
        track.work(new A()).name("foo");
    }

    @Test
    public void testTaskName() {
        class A {}
        Orchestrator orc = Orchestrator.create();
        ITask task = orc.addWork(new A());
        String nm = "foobarbaz";
        assertSame(task.name(nm),task);
        assertEquals(nm,task.getName());
        assertThat(task.toString(),containsString(nm));
        nm = "bazbarfoo";
        assertSame(task.name(nm),task);
        assertEquals(nm,task.getName());
        assertThat(task.toString(),containsString(nm));
    }

    @Test
    public void test1SimpleActive() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        A a = new A();
        PathTask taskA = track.work(a);
        verify(0);
    }

    @Test
    public void test1SimplePassive() {
        class A extends PathTask {
            @PassThru
            public void exec() {
                got();
            }
        }
        A a = new A();
        PathTask taskA = track.passThru(a);
        assertNotNull(taskA);
        verify(0);
    }

    @Test
    public void testPassThruNoWork() {
        class A extends PathTask {
            boolean work = false;
            boolean passThru = false;
            @Work public void work() {
                work = true;
            }
            @PassThru public void pass() {
                passThru = true;
            }
        }
        A a = new A();
        PathTask taskA = track.passThru(a);
        verify(0);
        assertFalse(a.work);
        assertTrue(a.passThru);
    }

    @Test(expected = InvalidTask.AlreadyAdded.class)
    public void testAddBothActiveAndPasive() {
        class A extends PathTask {
            @PassThru
            public void exec() {
                got();
            }
        }
        A a = new A();
        track.work(a);
        track.work(a);
        verify(0);
    }

    @Test
    public void testNonPublicMethod() {
        class Holder {
            boolean hit = false;
        }
        final Holder holder = new Holder();
        class A extends PathTask {
            @Work
            /* not public */ void exec() {
                got();
                holder.hit = true;
            }
        }
        A a = new A();
        PathTask taskA = track.work(a);
        verify(0);
        assertTrue(holder.hit);
    }

    /*
    @Test(expected = InvalidTask.BadReturn.class)
    public void testBadReturnType() {
        class A extends PathTask {
            @Work
            public int exec() {
                got();
                return 0;
            }
        }
        A a = new A();
        PathTask taskA = track.work(a);
        verify(0);
    }
    */

    /**
     * Can't do anything with primitives as parameters
     */
    @Test(expected = InvalidTask.BadParam.class)
    public void testPrimitiveTaskParam() {
        class A extends PathTask {
            @Work
            public void exec(int x) {
                got();
            }
        }
        A a = new A();
        PathTask taskA = track.work(a);
        verify(0);
    }

    /**
     * Any object can be added which is trivially made available to other task
     * methods as a parameter
     */
    @Test
    public void testNonTaskParam() {
        class A {
        }
        class Holder {
            A a;
        }
        final Holder holder = new Holder();
        class B extends PathTask {
            @Work
            public void exec(A a) {
                got();
                holder.a = a;
            }
        }
        A a = new A();
        B b = new B();
        track.orc.addWork(a);
        PathTask taskB = track.work(b);
        verify(0);
        assertSame(a,holder.a);
    }

    @Test(expected = InvalidGraph.MissingDependents.class)
    public void testMissingDependencies() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec(A a) {
                got(a);
            }
        }
        B b = new B();
        PathTask taskB = track.work(b);
        verify(0);
    }

    @Test(expected = InvalidGraph.MissingDependents.class)
    public void testMultiMissingDependencies() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class C extends PathTask {
            @Work
            public void exec(A a, B b) {
                got(a,b);
            }
        }
        B b = new B();
        C c = new C();
        PathTask taskB = track.work(b);
        PathTask taskC = track.work(c);
        verify(0);
    }

    @Test
    public void testJustOneMethodMissingDependenciesOk() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class C extends PathTask {
            @Work
            public void exec(A a) {
                got(a);
            }

            @Work
            public void exec(B b) {
                got(b);
            }
        }
        B b = new B();
        C c = new C();
        PathTask taskB = track.work(b);
        PathTask taskC = track.work(c).exp(b);
        verify(0);
    }

    @Test
    public void test2Linear() {
        class A extends PathTask {
            @Work
            public void exec() {
                System.out.println("GOT-A");
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec(A a) {
                System.out.println("GOT-B " + a);
                got(a);
            }
        }
        A a = new A();
        B b = new B();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b).exp(a);
        verify(0);
    }

    @Test
    public void test3Linear() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec(A a) {
                got(a);
            }
        }
        class C extends PathTask {
            @Work
            public void exec(B b) {
                got(b);
            }
        }

        A a = new A();
        B b = new B();
        C c = new C();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b).exp(a);
        PathTask taskC = track.work(c).exp(b);
        verify(0);
    }

    @Test
    public void test2Indepdendent() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        A a = new A();
        B b = new B();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b);
        verify(1);
    }

    @Test
    public void test2Dependents() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class C extends PathTask {
            @Work
            public void exec(A a, B b) {
                got(a,b);
            }
        }

        A a = new A();
        B b = new B();
        C c = new C();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b);
        PathTask taskC = track.work(c).exp(a,b);
        verify(1);
    }

    @Test
    public void testOneException() {
        class A extends PathTask {
            @Work
            public void exec() {
                throw new OnlyATestException("foo");
            }
        }
        A a = new A();
        PathTask taskA = track.work(a);
        try {
            verify(0);
        }
        catch (OnlyATestException e) {
            String gs = track.orc.getGraphState();
            assertTrue(gs.contains(OnlyATestException.class.getSimpleName()));
        }
    }
    
    @Test(expected=OnlyATestException.class)
    public void testExceptionStopsFiring() {
        class A extends PathTask {
            @Work void exec() {
                throw new OnlyATestException("foo");
            }
        }
        class B extends PathTask {
            boolean hit = false;
            @Work void exec(A a) {
                hit = true;
            }
        }
        A a = new A();
        B b = new B();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b);
        try {
            verify(0);
        }
        finally {
            assertFalse(b.hit);
        }
    }
    
    @Test
    public void testTwoExceptions() {
        class A extends PathTask {
            @Work
            public void exec() {
                // If one task runs too fast, orch may see the exception and prevent the other from running,
                // but the point of this test is to run both tasks and generate an exception from each
                sleep(10);  
                throw new OnlyATestException();
            }
        }
        A a1 = new A();
        A a2 = new A();
        PathTask taskA1 = track.work(a1).fork();
        PathTask taskA2 = track.work(a2).fork();
        try {
            verify(0);
        }
        catch (RuntimeGraphError.Multi e) {
            assertEquals(2,e.getExceptions().size());
            String gs = track.orc.getGraphState();
            assertTrue(gs.contains(OnlyATestException.class.getSimpleName()));
        }
    }

    private void test1ParException(boolean forkAElseForkB) {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec() {
                throw new OnlyATestException();
            }
        }
        A a = new A();
        B b = new B();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b);
        PathTask taskToFork = forkAElseForkB ? taskA : taskB;
        taskToFork.fork();
        verify(1);
    }

    @Test(expected = OnlyATestException.class)
    public void test1ParExceptionForkThrower() {
        test1ParException(false);
    }

    @Test(expected = OnlyATestException.class)
    public void test1ParExceptionForkNonThrower() {
        test1ParException(true);
    }

    private void testMultiException(boolean forkA1, boolean forkA2, boolean forkB) {
        class A extends PathTask {
            @Work
            public void exec() {
                sleep(10);
                throw new OnlyATestException();
            }
        }
        class B extends PathTask {
            @Work
            public void exec() {
                got();
                sleep(50);
            }
        }
        A a1 = new A();
        A a2 = new A();
        B b = new B();
        PathTask taskA1 = track.work(a1);
        PathTask taskA2 = track.work(a2);
        PathTask taskB = track.work(b);
        if (forkA1)
            taskA1.fork();
        if (forkA2)
            taskA2.fork();
        if (forkB)
            taskB.fork();
        verify(3);
    }

    @Test(expected = RuntimeGraphError.Multi.class)
    public void testMultiExceptionKeepA1() {
        testMultiException(false,true,true);
    }

    @Test(expected = RuntimeGraphError.Multi.class)
    public void testMultiExceptionKeepB() {
        testMultiException(true,true,false);
    }

    @Test(expected = RuntimeGraphError.Multi.class)
    public void testMultiExceptionKeepNone() {
        testMultiException(true,true,true);
    }
    
    /**
     * Ensure exception in main thread behaves the same as exceptions in spawned threads
     */
    @Test(expected = OnlyATestException.class)
    public void testMainThreadExceptionWaits() {
        class A extends PathTask {
            @Work
            public void exec() {
                sleep(10); // Ensure enough time for B to run, because the exception we throw might prevent that
                throw new OnlyATestException("tmtew");
            }
        }
        class B extends PathTask {
            boolean hit = false;
            @Work
            public void exec() {
                sleep(50);
                got();
                hit = true;
            }
        }
        A a = new A();
        B b = new B();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b).fork();
        try {
            verify(1);
        }
        finally {
            assertTrue(b.hit);
        }
    }

    @Test
    public void testExceptionHalt() {
        class A extends PathTask {
            @Work
            public void exec() {
                sleep(20);
                throw new OnlyATestException();
            }
        }
        class B extends PathTask {
            @Work
            public void exec() {
                sleep(40);
                got();
            }
        }
        class C extends PathTask {
            boolean hit = false;

            @Work
            public void exec(B b) {
                got(b);
                hit = true;
            }
        }
        A a = new A();
        B b = new B();
        C c = new C();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b);
        PathTask taskC = track.work(c);

        try {
            verify(1);
        }
        catch (OnlyATestException e) {
            sleep(80);
            assertFalse(c.hit);
        }
    }

    @Test
    public void test3LinearDup() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec(A a) {
                got(a);
            }
        }
        class C extends PathTask {
            @Work
            public void exec(A a, B b) {
                got(a,b);
            }
        }

        A a = new A();
        B b = new B();
        C c = new C();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b).exp(a);
        PathTask taskC = track.work(c).exp(a,b);
        verify(0);
    }

    @Test
    public void testIgnoreTaskMethods() {
        class A extends PathTask {
            boolean hit = false;

            @Work
            public void exec() {
                hit = true;
            }
        }
        A a = new A();
        PathTask taskA = track.ignoreTaskMethods(a);
        assertNotNull(taskA);
        verify(0);
        assertFalse(a.hit);
    }

    @Test
    public void testIgnoreTaskMethodsWithDependency() {
        class A extends PathTask {
            boolean hit = false;

            @Work
            public void exec() {
                hit = true;
            }
        }
        class B extends PathTask {
            @Work
            public void exec(A a) {
                got(a);
            }
        }
        A a = new A();
        B b = new B();
        PathTask taskA = track.ignoreTaskMethods(a);
        PathTask taskB = track.work(b).exp(a);
        verify(0);
        assertFalse(a.hit);
    }

    @Test
    public void test2Dependents1Light() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work(light = true)
            public void exec() {
                got();
            }
        }
        class C extends PathTask {
            @Work
            public void exec(A a, B b) {
                got(a,b);
            }
        }

        A a = new A();
        B b = new B();
        C c = new C();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b);
        PathTask taskC = track.work(c).exp(a,b);
        verify(0);
    }

    class Top extends PathTask {
        @Work
        public void exec() {
            got();
        }
    }

    class LeftFromTop extends PathTask {
        @Work
        public void exec(Top top) {
            got(top);
        }
    }

    class RightFromTop extends PathTask {
        @Work
        public void exec(Top top) {
            got(top);
        }
    }

    class Bottom extends PathTask {
        @Work
        public void exec(LeftFromTop left, RightFromTop right) {
            got(left,right);
        }
    }

    private void testDiamondWithDelays(int td, int ld, int rd, int bd) {
        PathTask top = track.work(new Top()).sleepFor(td);
        PathTask left = track.work(new LeftFromTop()).sleepFor(ld).exp(top);
        PathTask right = track.work(new RightFromTop()).sleepFor(rd).exp(top);
        PathTask bottom = track.work(new Bottom()).sleepFor(bd).exp(left,right);
        verify(1);
    }

    @Test
    public void testDiamond0000() {
        testDiamondWithDelays(0,0,0,0);
    }

    @Test
    public void testDiamond0x00() {
        testDiamondWithDelays(0,50,0,0);
    }

    @Test
    public void testDiamond00x0() {
        testDiamondWithDelays(0,0,50,0);
    }

    @Test(expected = InvalidGraph.Circular.class)
    public void testSelfRefFail() {
        class A extends PathTask {
            @Work
            public void exec(A a) {
            }
        }
        A a = new A();
        PathTask taskA = track.work(a);
        verify(0);
    }

    // These defined outside of testCircular3Fail because forward ref
    // would otherwise cause compiler to complain
    class CA extends PathTask {
        @Work
        public void exec(CC c) {
        }
    }

    class CB extends PathTask {
        @Work
        public void exec(CA a) {
        }
    }

    class CC extends PathTask {
        @Work
        public void exec(CB b) {
        }
    }

    @Test(expected = InvalidGraph.Circular.class)
    public void testCircular3Fail() {
        CA a = new CA();
        CB b = new CB();
        CC c = new CC();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b);
        PathTask taskC = track.work(c);
        verify(0);
    }

    @Test
    public void testPassThruNotCalledWhenNotPassive() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @PassThru
            public void exec(A a) {
                throw new RuntimeException("Didn't expect to be called");
            }

            @Work
            public void exec() {
                got();
            }
        }

        A a = new A();
        B b = new B();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b); // Not passive
        verify(1);
    }

    @Test
    public void test3LinearOnePassThru() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @PassThru
            public void exec(A a) {
                got(a);
            }

            @Work
            public void exec() {
                got();
            } // Shouldn't get called
        }
        class C extends PathTask {
            @Work
            public void exec(B b) {
                got(b);
            }
        }

        A a = new A();
        B b = new B();
        C c = new C();
        PathTask taskA = track.work(a);
        PathTask taskB = track.passThru(b).exp(a);
        PathTask taskC = track.work(c).exp(b);
        verify(0);
    }

    @Test
    public void testDependentHasNoExecutableMethod() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec(A a) {
                got(a);
            }
        }

        A a = new A();
        B b = new B();
        PathTask taskA = track.passThru(a); // No @PassThru on A, but it should
                                            // be exposed to B
        PathTask taskB = track.work(b).exp(a);
        verify(0);
    }
    
    @Test
    public void testSetWait() {
        class A {}
        Orchestrator orc = Orchestrator.create();
        ITask task = orc.addWork(new A());
        assertTrue(task.isWait());
        assertSame(task.noWait(),task);
        assertFalse(task.isWait());
        assertSame(task.wait(true),task);
        assertTrue(task.isWait());
    }

    @Test
    public void testParexStats() {
        final int SLEEP_MAX = 100;
        class A extends PathTask {
            @Work
            public void exec() {
                got();
                sleep(50);
            }
        }
        class B extends PathTask {
            @Work
            public void exec() {
                got();
                sleep(50);
            }
        }
        class C extends PathTask {
            @Work
            public void exec(A a, B b) {
                got(a,b);
                sleep(SLEEP_MAX);
            } // nowait below
        }
        class D extends PathTask {
            @Work
            public void exec(A a, B b) {
                got(a,b);
                sleep(50);
            }
        }

        A a = new A();
        B b = new B();
        C c = new C();
        D d = new D();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b);
        PathTask taskC = track.work(c).exp(a,b).noWait().fork();
        PathTask taskD = track.work(d).exp(a,b);
        verify(1,2,false);
        Orchestrator.ExecutionStats stats = track.orc.getStats();
        Orchestrator.ExecutionStats noWaitStats = track.orc.getNoWaitStats();
        assertEquals(3,stats.getNumberOfTasksExecuted());
        assertEquals(stats,noWaitStats);
        long sav = stats.getParallelizationSaving();
        assertTrue("got-stats-before " + sav,sav > 20 && sav < 80);
        sleep(SLEEP_MAX);
        stats = track.orc.getStats();
        noWaitStats = track.orc.getNoWaitStats();
        assertEquals(3,stats.getNumberOfTasksExecuted());
        assertEquals(4,noWaitStats.getNumberOfTasksExecuted());
        assertTrue("got-stats-after " + sav,sav > 20 && sav < 80);
        long nws = noWaitStats.getParallelizationSaving();
        assertTrue(noWaitStats.getParallelizationSaving() > stats.getParallelizationSaving());
        assertTrue(noWaitStats.getExecutionTime() > stats.getExecutionTime());
    }

    @Test
    public void testStatsEqualityAgainstAddMethods() {
        Orchestrator orc = Orchestrator.create();
        assertNotNull(orc.toString());
        Orchestrator.ExecutionStats stats1 = orc.getStats();
        assertEquals(stats1,stats1);
        Orchestrator.ExecutionStats stats2 = orc.getStats();
        orc.addWork(new Object() {
            @Work
            public void exec() {
            }
        });
        orc.execute();
        Orchestrator.ExecutionStats stats3 = orc.getStats();
        assertNotEquals(stats1,stats3);
        orc.addPassThru(new Object() {
            @Work
            public void exec() {
            }
        });
        orc.execute();
        Orchestrator.ExecutionStats stats4 = orc.getStats();
        assertNotEquals(stats1,stats4);
        orc.addIgnoreTaskMethods(new Object() {
            @Work
            public void exec() {
            }
        });
        orc.execute();
        Orchestrator.ExecutionStats stats5 = orc.getStats();
        assertNotEquals(stats1,stats5);
        System.out.println("Final stats=" + stats5);
    }

    @Test
    public void testComplex() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @PassThru
            public void exec(A a) {
                got(a);
            }
        }
        class C extends PathTask {
            @Work
            public void exec(B b) {
                got(b);
            }
        }
        class D extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class E extends PathTask {
            @Work
            public void exec(C c, D d) {
                got(c,d);
            }
        }
        class F extends PathTask {
            @Work
            public void exec(D d) {
                got(d);
            }
        }
        class G extends PathTask {
            @Work
            public void exec(C c, F f) {
                got(c,f);
            }
        }
        class H extends PathTask {
            @Work
            public void exec(A a, G g) {
                got(a,g);
            }
        }

        A a = new A();
        B b = new B();
        C c = new C();
        D d = new D();
        E e = new E();
        F f = new F();
        G g = new G();
        H h = new H();
        PathTask taskA = track.work(a);
        PathTask taskB = track.passThru(b).exp(a);
        PathTask taskC = track.work(c).exp(b);
        PathTask taskD = track.work(d);
        PathTask taskE = track.work(e).exp(c,d);
        PathTask taskF = track.work(f).exp(d);
        PathTask taskG = track.work(g).exp(c,f);
        PathTask taskH = track.work(h).exp(a,g);
        verify(1,2);
    }

    /* boolean result no longer applies
    private void testPathExecutedConditionally(final boolean bReturnsTrue) {
        class Root extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class Leftward extends PathTask {
            @Work
            public boolean exec(Root root) {
                got(root);
                return bReturnsTrue;
            }
        }
        class Rightward extends PathTask {
            @Work
            public void exec(Root root) {
                got(root);
            }
        }
        class LeftOrRight extends PathTask {
            @Work
            public void exec(Leftward left) {
                got(left);
            }

            @Work
            public void exec(Rightward right) {
                got(right);
            }
        }

        Root root = new Root();
        Leftward left = new Leftward();
        Rightward right = new Rightward();
        LeftOrRight lor = new LeftOrRight();
        PathTask taskA = track.work(root);
        PathTask taskB = track.work(left).exp(root);
        PathTask taskC = track.work(right).exp(root);
        PathTask taskD = track.work(lor).exp(right).multiMethodOk();
        if (bReturnsTrue) {
            taskD = taskD.exp(left);
        }
        verify(1);
    }

    @Test
    public void testOnlyOnePathExecuted() {
        testPathExecutedConditionally(false);
    }

    @Test
    public void testBothPathsExecuted() {
        testPathExecutedConditionally(true);
    }
    */

    @Test(expected=RuntimeGraphError.Timeout.class)
    public void testTimeout() {
        class A extends PathTask {
            @Work void exec() {
                sleep(20);
                got();
            }
        }
        class B extends PathTask {
            boolean hit = false;
            @Work void exec(A a) {
                hit = true;
                got();
            }
        }
        A a = new A();
        B b = new B();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b);
        try {
            track.orc.execute(5L);
        }
        finally {
            assertFalse(b.hit);
        }
    }

    private void testReturn(final boolean which) {
        class A extends PathTask {
            @Work
            public boolean exec() {
                got();
                return which;
            }
        }
        A a = new A();
        PathTask taskA = track.work(a);
        verify(0);
    }

    @Test
    public void testReturnTrue() {
        testReturn(true);
    }

    @Test
    public void testReturnFalse() {
        testReturn(false);
    }

    /* boolean result no longer applies
    private void testReturnTwoDeep(final boolean which) {
        class A extends PathTask {
            @Work
            public boolean exec() {
                got();
                return which;
            }
        }
        class B extends PathTask {
            boolean exec = false;

            @Work
            public void exec(A a) {
                got(a);
                exec = true;
            }
        }
        A a = new A();
        B b = new B();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b);
        if (which) {
            taskB = taskB.exp(a);
        }
        verify(0);
        assertEquals(which,b.exec); // Graph completes in either case, but if
                                    // A.exec returns false then B.exec should
                                    // not execute
    }

    @Test
    public void testReturnTwoDeepTrue() {
        testReturnTwoDeep(true);
    }

    @Test
    public void testReturnTwoDeepFalse() {
        testReturnTwoDeep(false);
    }
    */

    // @Test
    public void testReturnMixedIncoming() {
        class A extends PathTask {
            final boolean which;

            A(boolean which) {
                this.which = which;
            }

            @Work
            public boolean exec() {
                got();
                return which;
            }
        }
        class B extends PathTask {
            AtomicInteger count = new AtomicInteger(0);

            @Work
            public void exec(A a) {
                got(a);
                count.incrementAndGet();
            }
        }
        A a1 = new A(true);
        A a2 = new A(false);
        B b = new B();
        PathTask taskA1 = track.work(a1);
        PathTask taskA2 = track.work(a2);
        PathTask taskB = track.work(b);
        verify(0);
        assertEquals(1,b.count.get()); // Graph completes , but B.exec should
                                       // exec only a1
    }

    private void multiMethodResponse(boolean allow) {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class C extends PathTask {
            @Work
            public void exec(A a) {
                got(a);
            }

            @Work
            public void exec(B b) {
                got(b);
            }
        }

        A a = new A();
        B b = new B();
        C c = new C();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b);
        PathTask taskC = track.work(c).exp(a).exp(b);
        if (allow) {
            taskC.multiMethodOk();
        }
        verify(1);
    }

    @Test
    public void testMultiMethodAllowed() {
        multiMethodResponse(true);
    }

    @Test(expected = InvalidGraph.MultiMethod.class)
    public void testMultiMethodRejected() {
        multiMethodResponse(false);
    }

    class ParTask extends PathTask {
        int nesting = 0;
        int maxNesting = 0;

        void glob() {
            if (++nesting > maxNesting)
                maxNesting = nesting;
            sleep(100);
            nesting--;
        }
    }

    @Test
    public void testMulti2() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends ParTask {
            @Work
            public void exec(@Count A a) {
                got(a);
                glob();
            }
        }

        A a1 = new A();
        A a2 = new A();
        B b = new B();
        PathTask taskA1 = track.work(a1);
        PathTask taskA2 = track.work(a2);
        PathTask taskB = track.work(b).exp(a1).exp(a2);
        // Because ParTask sleeps, both A threads should have fired and executed
        // at same time
        verify(1);
        assertEquals(2,b.maxNesting);
    }
    
    abstract class RootSleeper extends PathTask {
        final int sleep;
        private final int pos;
        RootSleeper(int sleep, int pos) {this.sleep = sleep;this.pos = pos;}
        @Override public String toString() {return getClass().getName() + "("+sleep+','+pos+')';}
        @Work
        public void exec() {
            sleep(sleep);
            got();
        }
    }

    /**
     * Runs with a root instance for each parameter which sleeps for the specified value,
     * and ensures that they all arrive in insertion order independently of how long each
     * slept for.
     * 
     * @param sleepValues
     */
    private void runOrderedParameter(int...sleepValues) {
        init();
        class A extends RootSleeper {
            A(int sleep, int pos) {super(sleep,pos);}
        }
        class B extends ParTask {
            final List<A> as = new ArrayList<>();
            @Work
            public synchronized void exec(@Ordered A a) {
                got(a);
                as.add(a);
            }
        }
        
        B b = new B();
        PathTask taskB = track.work(b);
        
        final int nv = sleepValues.length;
        final Object[] as = new Object[nv];

        for (int i=0; i<nv; i++) {
            A a = new A(sleepValues[i],i);
            as[i] = a;
            PathTask task = track.work(a).name(a.toString());
            taskB = taskB.exp(a);
        }

        verify(nv-1,nv);

        for (int i=0; i<nv; i++) {
            assertSame(as[i],b.as.get(i));
        }
    }
    
    /**
     * Tests that a task method with an @Ordered parameter always preserves insertion order. 
     */
    @Test
    public void testOrderedParamater() {
        runOrderedParameter(100);
        runOrderedParameter(100,0);
        runOrderedParameter(100,50,0);
        runOrderedParameter(0,50,100);
        runOrderedParameter(0,100,50);
        runOrderedParameter(50,0,100,75);
        runOrderedParameter(50,25,100,75,0);
    }
    
    /**
     * Tests ordering on multiple @Ordered parameters
     */
    //@Test  // XXX TBD
    public void testMulipleOrderedParameters() {
        class FirstOrdered extends RootSleeper {
            FirstOrdered(int sleep, int pos) {super(sleep,pos);}
        }
        class SecondOrdered extends RootSleeper {
            SecondOrdered(int sleep, int pos) {super(sleep,pos);}
        }
        class NotOrdered extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class Combiner extends ParTask {
            List<RootSleeper[]> gots = new ArrayList<>();
            @Work
            public synchronized void exec(@Ordered FirstOrdered first, NotOrdered no, @Ordered SecondOrdered second) {
                System.out.println("Got first="+first+", second="+second);
                gots.add(new RootSleeper[] {first,second});
                got(first,no,second);
            }
        }
        
        FirstOrdered firstHigh = new FirstOrdered(100,0);
        FirstOrdered firstLow = new FirstOrdered(0,1);
        
        SecondOrdered secondHigh = new SecondOrdered(100,0);
        SecondOrdered secondMid = new SecondOrdered(50,0);
        SecondOrdered secondLow = new SecondOrdered(0,0);
        
        PathTask taskAHigh = track.work(firstHigh).name("firstHigh");
        PathTask taskALow = track.work(firstLow).name("firstLow");

        PathTask taskCHigh = track.work(secondHigh).name("secondHigh");
        PathTask taskCMid = track.work(secondMid).name("secondMid");        
        PathTask taskCLow = track.work(secondLow).name("secondLow");
        
        PathTask noTask = track.work(new NotOrdered());
        Combiner combiner = new Combiner();
        PathTask taskB = track.work(combiner)
                .exp(firstHigh,noTask,secondHigh)
                .exp(firstHigh,noTask,secondMid)
                .exp(firstHigh,noTask,secondLow)
                .exp(firstLow,noTask,secondHigh)
                .exp(firstLow,noTask,secondMid)
                .exp(firstLow,noTask,secondLow);

        verify(4,6);
        
        RootSleeper[] initial = combiner.gots.get(0);
        assertSame(initial[0],firstHigh);
        assertSame(initial[1],secondHigh);
        
        RootSleeper[] last = combiner.gots.get(combiner.gots.size()-1);
        assertSame(last[0],firstLow);
        assertSame(last[1],secondLow);
    }

    @Test
    public void testOrderedListParameter() {
        class A extends RootSleeper {
            A(int sleep, int pos) {super(sleep,pos);}
        }
        class B {
            List<A> as = null;
            @Work
            public synchronized void exec(@Ordered List<A> as) {
                this.as = as;
            }
        }
        
        A aMid = new A(50,0);
        A aHigh = new A(100,1);
        A aLow = new A(0,2);

        Orchestrator orc = Orchestrator.create();
        orc.addWork(aMid).name("aMid");
        orc.addWork(aHigh).name("aHigh");
        orc.addWork(aLow).name("aLow");
        
        B b = new B();
        orc.addWork(b);
        
        orc.execute();

        assertSame(aMid,b.as.get(0));
        assertSame(aHigh,b.as.get(1));
        assertSame(aLow,b.as.get(2));
    }
    
    @Test
    public void testArgRepeat() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec(A a1, A a2) {
                got(a1,a2);
            }
        }
        A a1 = new A();
        A a2 = new A();
        B b = new B();
        PathTask taskA1 = track.work(a1).name("a1");
        PathTask taskA2 = track.work(a2).name("a2");
        PathTask taskB = track.work(b).exp(a1,a2);

        verify(1);
    }
    
    private void runArgBaseSub(boolean inOrder) {
        class Base extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class Sub extends Base {}
        class Receive extends PathTask {
            @Work
            public void exec(Base base, Sub sub) {
                got(base,sub);
            }
        }
        Base base = new Base();
        Sub sub = new Sub();
        Receive r = new Receive(); 
        PathTask taskBase;
        PathTask taskSub;
        if (inOrder) {
            taskBase = track.work(base);
            taskSub = track.work(sub);
        }
        else {
            taskSub = track.work(sub);
            taskBase = track.work(base);
        }
        PathTask taskReceive = track.work(r).exp(base,sub);
        verify(1);
    }
    
    /**
     * TBD move
     * By default, arguments of the same type are presented to the task method in the order added to the orchestrator. 
     * This applies to all formats where multiple args of the same type are allowed, e.g.:
     * <code>
     *   void exec(Foo foo1, Foo foo2)
     *   void exec(List<Foo> foos)   
     *   void exec(@Count(2) Foo foo)
     * </code>
     * Where such ordering is not necessary, the task writer can mark arguments as unordered, which allows them
     * to be provided as they become available and thus can increase parallelism. This would only have effect on
     * the third case:
     * <code>
     *   void exec(@Count(2,ordered=false) Foo foo)
     * </code>
     */
    @Test
    public void testArgBaseSubInOrder() {
        runArgBaseSub(true);
    }
    
    @Test(expected=InvalidGraph.MissingDependents.class)
    public void testArgBaseSubOutOfOrder() {
        runArgBaseSub(false);
    }
    
    @Test
    public void testMulti2Sequence() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends ParTask {
            @Work(scope = Scope.SEQUENTIAL)
            public void exec(@Count A a) {
                got(a);
                glob();
            }
        }
        A a1 = new A();
        A a2 = new A();
        B b = new B();
        PathTask taskA1 = track.work(a1).name("a1");
        PathTask taskA2 = track.work(a2).name("a2");
        PathTask taskB = track.work(b).exp(a1).exp(a2);

        verify(1);
        assertEquals(1,b.maxNesting);
    }

    @Test
    public void testMulti3() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec(@Count A a) {
                got(a);
            }
        }
        class C extends PathTask {
            @Work
            public void exec(@Count B b) {
                got(b);
            }
        }

        A a1 = new A();
        A a2 = new A();
        B b1 = new B();
        B b2 = new B();
        C c = new C();
        PathTask taskA1 = track.work(a1).name("a1");
        PathTask taskA2 = track.work(a2).name("a2");
        PathTask taskB1 = track.work(b1).name("b1").exp(a1).exp(a2);
        PathTask taskB2 = track.work(b2).name("b2").exp(a1).exp(a2);
        PathTask taskC = track.work(c).exp(b1).exp(b1).exp(b2).exp(b2);
        verify(2,3);
    }

    @Test
    public void testMulti2With2Params() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class C extends PathTask {
            @Work
            public void exec(@Count A a, @Count B b) {
                got(a,b);
            }
        }

        A a1 = new A();
        A a2 = new A();
        B b1 = new B();
        B b2 = new B();
        C c = new C();
        PathTask taskA1 = track.work(a1);
        PathTask taskA2 = track.work(a2);
        PathTask taskB1 = track.work(b1).name("b1");
        PathTask taskB2 = track.work(b2).name("b2");
        PathTask taskC = track.work(c).exp(a1,b1).exp(a1,b2).exp(a2,b1).exp(a2,b2);
        verify(3,5);
    }

    /**
     * Forces, in order to test the flow path, the 'push' of a task to the
     * waiting main thread -- unfortunately relies on internal knowledge about
     * which task a thread (main in this case) will keep for itself to execute.
     * This works but if that order is innocuously changed in the future then
     * this test may break.
     */
    @Test
    public void testMainThreadFollow() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
                sleep(100);
            }
        }
        class B extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class C extends PathTask {
            @Work
            public void exec(A a, B b) {
                got(a,b);
            }
        }
        class D extends PathTask {
            @Work
            public void exec(C c) {
                got(c);
            }
        }
        class E extends PathTask {
            @Work
            public void exec(C c) {
                got(c);
            }
        }

        A a = new A();
        B b = new B();
        C c = new C();
        D d = new D();
        E e = new E();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b);
        PathTask taskC = track.work(c).exp(a,b);
        PathTask taskD = track.work(d).exp(c);
        PathTask taskE = track.work(e).exp(c);
        verify(1);
    }

    @Test
    public void testFireNoWait() {
        final int DELAY = 100;
        class A extends PathTask {
            boolean done = false;

            @Work
            public void exec() {
                sleep(DELAY);
                done = true;
            }
        }
        A a = new A();
        PathTask taskA = track.work(a).noWait(); // Shouldn't be done until
                                                 // after DELAY
        verify(1,false);
        assertFalse(a.done);
        sleep(DELAY + 10);
        assertTrue(a.done);
    }

    @Test
    public void testMixedFireNoWait() {
        final int DELAY = 100;
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            boolean done = false;

            @Work
            public void exec() {
                got();
                sleep(DELAY);
                done = true;
            }
        }
        class C extends PathTask {
            @Work
            public void exec(A a, B b) {
                got(a,b);
            }
        }
        A a = new A();
        B b = new B();
        C c = new C();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b).noWait();
        PathTask taskC = track.work(c).exp(a,b);

        // Orchestrator on its own wouldn't wait for B, but since it waits for C
        // it must by implication wait for B
        verify(1);
    }

    @Test
    public void testSimpleList() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec(List<A> as) {
                System.out.println("Got: " + as);
                got(list(as));
            }
        }

        A a1 = new A();
        A a2 = new A();
        B b = new B();
        PathTask taskA1 = track.work(a1);
        PathTask taskA2 = track.work(a2);
        PathTask taskB = track.work(b).exp(list(a1,a2));
        verify(1);
    }

    @Test
    public void testListPlusNonListArg() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class C extends PathTask {
            @Work
            public void exec(List<A> as, B b) {
                got(list(as),b);
            }
        }

        A a1 = new A();
        A a2 = new A();
        B b = new B();
        C c = new C();
        PathTask taskA1 = track.work(a1);
        PathTask taskA2 = track.work(a2);
        PathTask taskB = track.work(b);
        PathTask taskC = track.work(c).exp(list(a1,a2),b);
        verify(2,3);
    }

    @Test
    public void testListArgReceivedOnceDuringMultipleCalls() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class C extends PathTask {
            @Work
            public void exec(List<A> as, @Count B b) {
                got(list(as),b);
            }
        }

        A a1 = new A();
        A a2 = new A();
        B b1 = new B();
        B b2 = new B();
        C c = new C();
        PathTask taskA1 = track.work(a1).name("a1");
        PathTask taskA2 = track.work(a2).name("a2");
        PathTask taskB1 = track.work(b1).name("b1");
        PathTask taskB2 = track.work(b2).name("b2");
        PathTask taskC = track.work(c).exp(list(a1,a2),b1).exp(list(a1,a2),b2).name("C");
        verify(2,4);
    }

    @Test
    public void testExplicitDependency() {
        class RootAFollowsB extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class RootBFollowsC extends PathTask {
            @Work
            public void exec() {
                got();
                sleep(25);
            }
        }
        class RootC extends PathTask {
            @Work
            public void exec() {
                got();
                sleep(50);
            }
        }

        RootAFollowsB a = new RootAFollowsB();
        RootBFollowsC b = new RootBFollowsC();
        RootC c = new RootC();
        PathTask taskA = track.work(a).after(b);
        PathTask taskB = track.work(b).after(c);
        PathTask taskC = track.work(c);
        verify(0);
        assertTrue(taskA.followed(taskB));
        assertTrue(taskA.followed(taskC));
        assertTrue(taskB.followed(taskC));
    }

    @Test
    public void testExplicitSubsetDependency() {

        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec() {
                sleep(50);
                got();
            }
        }

        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        B b = new B();
        PathTask taskA1 = track.work(a1).name("a1").after(b);
        PathTask taskA2 = track.work(a2).name("a2");
        PathTask taskA3 = track.work(a3).name("a3");
        PathTask taskB = track.work(b).before(a3);
        verify(2);
        assertTrue(taskA1.followed(taskB));
        assertTrue(taskB.followed(taskA2));
        assertTrue(taskA3.followed(taskB));
    }
    
    // TODO!!!
    //@Test
    public void testExplicitOverAuto() {

        class A extends PathTask {
            boolean hit = false;

            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec(A a) {
                got(a);
                a.hit = true;
            }
        }

        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        B b = new B();
        PathTask taskA1 = track.work(a1).name("a1").before(b);
        PathTask taskA2 = track.work(a2).name("a2");
        PathTask taskA3 = track.work(a3).name("a3");
        PathTask taskB = track.work(b).after(a3).exp(a1).exp(a3);
        verify(2);
        assertTrue(a1.hit);
        assertFalse(a2.hit);
        assertTrue(a3.hit);
    }

    @Test
    public void testExplicitBeforeRoot() {

        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }

        A a = new A();
        B b = new B();
        PathTask taskA = track.work(a).after(b);
        PathTask taskB = track.work(b);
        verify(0);
        assertTrue(taskA.followed(taskB));
    }

    @Test(expected = InvalidGraph.Circular.class)
    public void testExplicitCircular() {

        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec(A a) {
                got(a);
            }
        }

        class C extends PathTask {
            @Work
            public void exec(B b) {
                got(b);
            }
        }

        A a = new A();
        B b = new B();
        C c = new C();
        PathTask taskA = track.work(a).after(c);
        PathTask taskB = track.work(b).exp(a);
        PathTask taskC = track.work(c).exp(b);
        verify(0);
    }
    
    static interface Nuthin {}
    
    @Test
    public void testExplicitMultIfaceImpls() {
        
        class A extends PathTask implements Nuthin {
            @Work
            public void exec() {
                got();
            }
        }
        // B also implements Nuthin, the .before() call below should
        // prevent B being dependent on itself (which would call a 
        // circular reference violation).
        class B extends PathTask implements Nuthin {
            @Work
            public void exec(Nuthin a) {
                got((A)a);
            }
        }
        A a = new A();
        B b = new B();
        
        PathTask taskB = track.work(a);
        PathTask taskC = track.work(b).exp(a);
        
        track.orc.asAdded(a).before(b);
        
        verify(0);
    }
    
    @Test
    public void testDoubleRoot() {
        class Root1 extends PathTask {
        }
        class Root2 extends PathTask {
        }
        class RootEater extends PathTask {
            @Work
            public void exec(Root1 root1, Root2 root2) {
                got(root1,root2);
            }
        }
        Root1 root1 = new Root1();
        Root2 root2 = new Root2();
        RootEater re = new RootEater();
        PathTask t1 = track.work(root1);
        PathTask t2 = track.work(root2);
        PathTask te = track.work(re).exp(root1,root2);
        verify(0);
    }

    @Test
    public void testTaskInjection() {

        class Holder {
            ITask aTask;
            ITask bTask;
            ITask cTask;
        }

        final Holder holder = new Holder();

        class A extends PathTask {
            @Work
            public void exec(ITask task) {
                got();
                holder.aTask = task;
            }
        }
        class B extends PathTask {
            @Work
            public void exec(A a, ITask task) {
                got(a);
                holder.bTask = task;
            }
        }
        class C extends PathTask {
            @Work
            public void exec(ITask task, A a) {
                got(a);
                holder.cTask = task;
            }
        }

        A a = new A();
        B b = new B();
        C c = new C();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b).exp(a);
        PathTask taskC = track.work(c).exp(a);
        verify(1);
        assertSame(taskA.taskInstance,holder.aTask);
        assertSame(taskB.taskInstance,holder.bTask);
        assertSame(taskC.taskInstance,holder.cTask);
    }

    @Test
    public void testOneSubclassOfTask() {

        final AtomicInteger count = new AtomicInteger(0);

        class A extends PathTask {
            @Work
            public void exec() {
                got();
                count.incrementAndGet();
            }
        }
        class B extends A {
        }
        class C extends PathTask {
            @Work
            public void exec(A a) {
                got(a);
            }
        }
        class D extends PathTask {
            @Work
            public void exec(B b) {
                got(b);
            }
        }

        B b = new B();
        A a = b;
        C c = new C();
        D d = new D();
        PathTask taskB = track.work(b);
        PathTask taskC = track.work(c).exp(a);
        PathTask taskD = track.work(d).exp(b);
        verify(1);
        assertEquals(1,count.get());
    }

    @Test
    public void testTwoSubclassesOfTask() {
        final AtomicInteger count = new AtomicInteger(0);

        class A extends PathTask {
            @Work
            public void exec() {
                got();
                count.incrementAndGet();
            }
        }
        class B extends A {
        }
        class C extends A {
        }
        class D extends PathTask {
            @Work
            public void exec(@Count A a) {
                got(a);
            }
        }

        B b = new B();
        C c = new C();
        D d = new D();
        PathTask taskB = track.work(b);
        PathTask taskC = track.work(c);
        PathTask taskD = track.work(d).exp(b).exp(c);
        verify(1);
        assertEquals(2,count.get());
    }

    @Test
    public void testMethodOnSubclass() {

        final AtomicInteger count = new AtomicInteger(0);

        class A extends PathTask {

        }
        class B extends A {
            @Work
            public void exec() {
                got();
                count.incrementAndGet();
            }
        }
        class C extends PathTask {
            @Work
            public void exec(A a) {
                got(a);
            }
        }

        A a = new B();
        C c = new C();
        PathTask taskB = track.work(a);
        PathTask taskC = track.work(c).exp(a);
        verify(0);
        assertEquals(1,count.get());
    }
    
    interface I {}    
    
    @Test
    public void testMethodOnImpl() {

        class B implements I {
            boolean hit = false;
            @Work
            public void exec() {
                hit = true;
            }
        }
        class C {
            boolean hit = false;
            @Work public void exec(I i) {
                hit = true;
            }
        }

        B b = new B();
        C c = new C();
        
        Orchestrator orc = Orchestrator.create();
        orc.addWork(b);
        orc.addWork(c);
        orc.execute();
        assertTrue(b.hit);
        assertTrue(c.hit);
    }
}
