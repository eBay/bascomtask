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
package com.ebay.bascomtask.core;

import static org.junit.Assert.*;

import com.ebay.bascomtask.exceptions.InvalidTaskMethodException;
import com.ebay.bascomtask.exceptions.TaskNotStartedException;
import com.ebay.bascomtask.exceptions.TimeoutExceededException;
import com.ebay.bascomtask.util.CommonTestingUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.ebay.bascomtask.core.UberTask.*;
import static com.ebay.bascomtask.core.ExceptionTask.*;

/**
 * Core BascomTask execution tests. These should be runnable before anything test files.
 *
 * @author Brendan McCarthy
 */
@RunWith( Parameterized.class )
public class CoreTest extends BaseOrchestratorTest {

    @Parameterized.Parameters(name="{0}")
    public static Collection<Object[]> data(){
        return Arrays.asList(new Object[][]{
                {null},
                {SpawnMode.NEVER_SPAWN},
                {SpawnMode.DONT_SPAWN_UNLESS_EXPLICIT},
                {SpawnMode.WHEN_NEEDED},
                {SpawnMode.WHEN_NEEDED_NO_REUSE},
                {SpawnMode.NEVER_MAIN},
                {SpawnMode.ALWAYS_SPAWN},
        });
    }

    private final SpawnMode mode;

    public CoreTest(SpawnMode mode) {
        this.mode = mode;
    }

    private void checkSameThreads(UberTask t1, UberTask t2, boolean sameIfNormalMode, boolean sameUnlessExplicit, boolean ifNeverMain, boolean ifAlwaysSpawn) {
        boolean same = t1.ranInSameThread(t2);
        boolean cmp;
        SpawnMode mode = this.mode==null ? SpawnMode.WHEN_NEEDED : this.mode;
        switch (mode) {
            case NEVER_SPAWN: cmp = true; break;
            case NEVER_MAIN: cmp = ifNeverMain; break;
            case DONT_SPAWN_UNLESS_EXPLICIT: cmp = sameUnlessExplicit; break;
            case WHEN_NEEDED:
            case WHEN_NEEDED_NO_REUSE: cmp = sameIfNormalMode; break;
            case ALWAYS_SPAWN: cmp = ifAlwaysSpawn; break;
            default: throw new RuntimeException("Bad mode");
        }
        assertEquals(cmp,same);
    }

    private void checkInMainThread(UberTask t1, boolean sameIfNormalMode, boolean sameUnlessExplicit, boolean ifNeverMain, boolean ifAlwaysSpawn) {
        String tn = Thread.currentThread().getName();
        boolean same = tn.equals(t1.getThreadName());
        boolean cmp;
        SpawnMode mode = this.mode==null ? SpawnMode.WHEN_NEEDED : this.mode;
        switch (mode) {
            case NEVER_SPAWN: cmp = true; break;
            case ALWAYS_SPAWN: cmp = ifAlwaysSpawn; break;
            case NEVER_MAIN: cmp = ifNeverMain; break;
            case DONT_SPAWN_UNLESS_EXPLICIT: cmp = sameUnlessExplicit; break;
            case WHEN_NEEDED:
            case WHEN_NEEDED_NO_REUSE: cmp = sameIfNormalMode; break;
            default: throw new RuntimeException("Bad mode");
        }
        assertEquals("Task " + t1,cmp,same);
    }

    @Before
    public void before() {
        super.before();
        if (mode != null) {
            $.setSpawnMode(mode);
        }
    }

    @After
    public void after() {
        UberTask.UberTasker.clearAndVerify();
    }

    private void naming(NamingTask task) {
        NamingTask tw = $.task(task);
        assertEquals(task.getName(),tw.getName());
        String nm = "__!!__";
        tw.name(nm);
        assertEquals(nm,tw.getName());
    }

    @Test
    public void naming() {
        naming(new NamingTask.OverridesNothing());
        naming(new NamingTask.OverridesGet());
        naming(new NamingTask.OverridesGetAndSet());
    }

    @Test
    public void singleNoArgs() throws Exception {
        UberTasker task = task();
        UberTask top = $.task(task);
        CompletableFuture<Integer> cf = top.retValueOne();
        int got = cf.get();
        assertEquals(1,got);
    }

    @Test
    public void singleNoArgsOnWrapper() throws Exception {
        UberTasker task = task();
        UberTask top = $.task(task);
        CompletableFuture<Integer> cf = top.retValueOne();
        int got = cf.get();
        assertEquals(1,got);
    }

    @Test
    public void singleRet() throws Exception {
        UberTasker task = task();
        UberTask top = $.task(task);
        CompletableFuture<Integer> cf = top.ret(1);
        int got = cf.get();
        assertEquals(1,got);
    }

    @Test(expected = InvalidTaskMethodException.class)
    public void doubleTask() {
        $.task($.task(task(0))).ret(1);
    }

    @Test
    public void singleRetNamed() throws Exception {
        String name = "foobar";
        int got = $.task(task()).name(name).ret(1).get();
        assertEquals(1,got);
    }

    @Test
    public void doubleIncrement() throws Exception {
        UberTask top = $.task(task());
        CompletableFuture<Integer> tf = top.ret(1);
        UberTask mid = $.task(task());
        CompletableFuture<Integer> mf = mid.inc(tf);
        int got = mf.get();
        assertEquals(2,got);

        boolean sameThread = top.ranInSameThread(mid);
        if (mode==SpawnMode.ALWAYS_SPAWN) {
            sameThread = !sameThread;
        }
        assertTrue(sameThread);
    }

    private void vAdd(Weight leftWeight, Weight rightWeight, boolean sameIfNormalMode, boolean ifNeverMain, boolean ifAlwaysSpawn) throws Exception {
        UberTask left = $.task(task());
        UberTask right = $.task(task());
        CompletableFuture<Integer> lv = leftWeight.ret(left,1);
        CompletableFuture<Integer> rv = rightWeight.ret(right,5);
        CompletableFuture<Integer> v = $.task(task()).add(lv,rv);
        int got = v.get();
        assertEquals(6,got);

        checkSameThreads(left,right,sameIfNormalMode,true,ifNeverMain,ifAlwaysSpawn);
    }

    @Test
    public void vAdd() throws Exception {
        vAdd(Weight.LIGHT,Weight.LIGHT,true,true,true);
        vAdd(Weight.LIGHT,Weight.HEAVY,true,false,false);
        vAdd(Weight.HEAVY,Weight.LIGHT,true,false,false);
        vAdd(Weight.HEAVY,Weight.HEAVY,false,false,false);
    }

    private void diamond(Weight leftWeight, Weight rightWeight, boolean sameIfNormalMode, boolean ifAlwaysSpawn) throws Exception {
        UberTask top = $.task(task());
        UberTask left = $.task(task());
        UberTask right = $.task(task());
        UberTask bottom = $.task(task());
        CompletableFuture<Integer> tf = top.ret(1);
        CompletableFuture<Integer> lf = leftWeight.inc(left,tf);
        CompletableFuture<Integer> rf = rightWeight.inc(right,tf);
        CompletableFuture<Integer> bf = bottom.add(lf,rf);
        int got = bf.get();
        assertEquals(4,got);

        checkSameThreads(left,right,sameIfNormalMode,true, sameIfNormalMode,ifAlwaysSpawn);
    }

    @Test
    public void diamond() throws Exception {
        diamond(Weight.LIGHT,Weight.LIGHT,true,true);
        diamond(Weight.LIGHT,Weight.HEAVY,true,false);
        diamond(Weight.HEAVY,Weight.LIGHT,true,false);
        diamond(Weight.HEAVY,Weight.HEAVY,false,false);
    }

    @Test
    public void singleFutureArg() throws Exception {
        CompletableFuture<Integer> cf = CompletableFuture.supplyAsync(()->{sleep(100);return 3;});
        CompletableFuture<Integer> cp = $.task(task()).inc(cf);
        int got = cp.get();
        assertEquals(4,got);
    }

    @Test
    public void add3() throws Exception {
        UberTask task = task(5);
        CompletableFuture<Integer> top = $.task(task).ret(1);
        CompletableFuture<Integer> left = $.task(task).inc(top);
        CompletableFuture<Integer> rcf = CompletableFuture.supplyAsync(()->{sleep(300);return 3;});
        CompletableFuture<Integer> right = $.task(task).add((top),rcf);
        CompletableFuture<Integer> middle = $.task(task).add(left,(right));
        CompletableFuture<Integer> cp = $.task(task).add(left,middle,right);
        int got = cp.get();
        assertEquals(12,got);
    }

    @Test
    public void forceRunSpawned() throws Exception {
        UberTask task = task();
        int got = $.task(task).runSpawned().ret(1).get();
        assertEquals(1,got);
        checkInMainThread(task,false,false,false,false);
    }

    @Test
    public void forceWeightV() throws Exception {
        UberTask task1 = task();
        UberTask task2 = task();
        UberTask task3 = task();
        CompletableFuture<Integer> f1 = $.task(task1).runSpawned().ret(1);
        CompletableFuture<Integer> f2 = $.task(task2).light().ret(2);
        CompletableFuture<Integer> f3 = $.task(task3).runSpawned().ret(3);
        CompletableFuture<Integer> added = $.task(task()).add(f1,f2,f3);
        int got = added.get();
        assertEquals(6,got);

        checkInMainThread(task1,false,false,false, false);
        checkInMainThread(task2,true,true,true, true);
        checkInMainThread(task3,false,false,false, false);
    }

    @Test
    public void forceWeight3V() throws Exception {
        UberTask baseTask = task();
        UberTask task1 = task();
        UberTask task2 = task();
        UberTask task3 = task();
        CompletableFuture<Integer> base = $.task(baseTask).name("base").light().ret(1);
        CompletableFuture<Integer> f1 = $.task(task1).name("inc1").runSpawned().inc(base);
        CompletableFuture<Integer> f2 = $.task(task2).name("light").light().inc(base);
        CompletableFuture<Integer> f3 = $.task(task3).name("inc2").runSpawned().inc(base);
        CompletableFuture<Integer> added = $.task(task()).add(f1,f2,f3);
        int got = added.get();
        assertEquals(6,got);

        checkInMainThread(baseTask,true,true,true, true);
        checkInMainThread(task1,false,false,false, false);
        checkInMainThread(task2,true,true,true, true);
        checkInMainThread(task3,false,false,false, false);
    }

    @Test
    public void onlyActivatedTasksRun() throws Exception {
        task(0);
        UberTask shouldRun = task(2);

        CompletableFuture<Integer> base = $.task(shouldRun).ret(1);
        CompletableFuture<Integer> left = $.task(shouldRun).inc(base);

        int got = left.get();
        assertEquals(2,got);
    }

    @Test
    public void multipleReturns() throws Exception {
        Orchestrator $ = new Engine();

        int delay = 20; // Enough to ensure that they both have while other may have started and is delayed
        CompletableFuture<Integer> t1 = $.task(task().delayFor(delay)).ret(1);
        CompletableFuture<Integer> t2 = $.task(task().delayFor(delay)).ret(1);

        $.execute(t1,t2);

        // These ensure we don't test endingTime before it is actually set, since at this point the final
        // bookkeeping/processing on t1 and t2 might not have completed (very small window)
        assertNotNull(t1.get());
        assertNotNull(t2.get());

        TaskMeta m1 = $.getTaskMeta(t1);
        TaskMeta m2 = $.getTaskMeta(t2);
        CommonTestingUtils.validateTimes(m1);
        CommonTestingUtils.validateTimes(m2);
        assertTrue(m1.overlapped(m2));
        assertTrue(m1.getStartedAt() > 0);
    }

    @Test
    public void voidNoArg() {
        $.task(task()).voidConsume();
        CompletableFuture<Void> vf = $.task(task().delayFor(0)).consume();
        $.execute(vf);
        sleep(5); // Give tasks chance to complete
    }

    @Test
    public void voidOneArg() {
        CompletableFuture<Void> t1 = $.task(task().delayFor(0)).consume();
        CompletableFuture<Void> t2 = $.task(task().delayFor(0)).consume(t1);
        $.task(task().delayFor(0)).voidConsume(t1);

        $.execute(t2);
        TaskMeta m1 = $.getTaskMeta(t1);
        TaskMeta m2 = $.getTaskMeta(t2);
        assertTrue(m1.completedBefore(m2));
        sleep(5); // Give tasks chance to complete
    }

    @Test
    public void nonFutureReturn() {
        CompletableFuture<Integer> base = $.task(task()).ret(1);
        int got = $.task(task()).nonFutureRet(base);
        assertEquals(1,got);
    }

    @Test
    public void baseLight() throws Exception {
        CompletableFuture<Integer> tf = $.task(task()).light().ret(1);
        CompletableFuture<Integer> lf = $.task(task()).inc(tf);
        CompletableFuture<Integer> rf = $.task(task()).inc(tf);
        CompletableFuture<Integer> bf = $.task(task()).add(lf,rf);
        int got = bf.get();
        assertEquals(4, got);
    }

    @Test
    public void externals() throws Exception {
        CompletableFuture<Integer> e1 = CompletableFuture.supplyAsync(()->sleepThen(15,1));
        CompletableFuture<Integer> e2 = CompletableFuture.supplyAsync(()->sleepThen(5,2));
        CompletableFuture<Integer> e3 = CompletableFuture.supplyAsync(()->sleepThen(25,3));
        CompletableFuture<Integer> add = $.task(task()).add(e1,e2,e3);
        int got = add.get();
        assertEquals(6,got);
    }

    private int getFromPoolsOfSize(CompletableFuture<Integer> cf, int size) throws Exception {
        ExecutorService svc = Executors.newFixedThreadPool(size);
        try {
            GlobalConfig.INSTANCE.setExecutorService(svc);
            return cf.get();
        } finally {
            GlobalConfig.INSTANCE.restoreDefaultExecutorService();
        }
    }

    @Test
    public void poolSizeExceeded() throws Exception {
        UberTask task = task(3).delayFor(100);
        CompletableFuture<Integer> t1 = $.task(task).ret(1);
        CompletableFuture<Integer> t2 = $.task(task).ret(2);
        CompletableFuture<Integer> t3 = $.task(task).ret(4);
        CompletableFuture<Integer> add = $.task(task().delayFor(0)).add(t1,t2,t3);
        int got = getFromPoolsOfSize(add,1);
        assertEquals(7,got);
    }

    @Test
    public void poolSizeExceededInSpawnedThread() throws Exception {
        CompletableFuture<Integer> t1 = $.task(task()).runSpawned().ret(1);
        CompletableFuture<Integer> t2 = $.task(task()).runSpawned().inc(t1);
        int got = getFromPoolsOfSize(t2,1);
        assertEquals(2,got);
    }

    @Test
    public void reuseMainThread() throws Exception {
        if ($.getEffectiveSpawnMode()==SpawnMode.WHEN_NEEDED) {
            UberTask tfast = task().delayFor(1);
            CompletableFuture<Integer> fast = $.task(tfast).name("fastl").ret(5);

            UberTask tspawned = task();

            CompletableFuture<Integer> top = $.task(task()).name("top").runSpawned().ret(1);
            CompletableFuture<Integer> left = $.task(task().delayFor(50)).inc(top);
            CompletableFuture<Integer> right = $.task(tspawned).runSpawned().name("reuse").inc(top);

            CompletableFuture<Integer> add3 = $.task(task()).add(fast, (left), right);
            int got = add3.get();
            assertEquals(9, got);

            assertEquals(tfast.getThreadName(), tspawned.getThreadName());
        }
    }

    @Test
    public void neverMainThread() throws Exception {
        UberTask task = task();
        $.task(task).ret(1).get();
        checkInMainThread(task,true,true,false, false);

        task = task();
        $.task(task).light().ret(1).get();
        checkInMainThread(task,true,true,true, true);
    }

    @Test
    public void noWaiting() throws Exception {
        int wait = 25;
        UberTasker task = task().delayFor(wait);
        CompletableFuture<Integer> t1 = $.task(task).name("delayed").runSpawned().ret(1);
        CompletableFuture<Integer> t2 = $.task(task().delayFor(0)).name("fast").ret(2);
        $.execute(t1,t2);
        int got = t2.get();
        int exp = mode==SpawnMode.NEVER_SPAWN ? 1 : 0;
        assertEquals(exp,task.getActualCount());
        assertEquals(2,got);
        sleep(wait+5);
        assertEquals(1,task.getActualCount());
    }

    @Test
    public void executeAndWait() throws Exception {
        final int wait = 20;
        UberTasker task1 = task().delayFor(wait);
        UberTasker task2 = task().delayFor(wait);
        UberTasker task3 = task().delayFor(wait);
        UberTasker task4 = task().delayFor(wait);
        CompletableFuture<Integer> t1 = $.task(task1).name("task1").runSpawned().ret(1);
        CompletableFuture<Integer> t2 = $.task(task2).name("task2").runSpawned().ret(2);
        CompletableFuture<Integer> t3 = $.task(task3).name("task3").runSpawned().ret(3);
        CompletableFuture<Integer> t4 = $.task(task4).name("task4").runSpawned().ret(4);

        $.executeAndWait(t1,t2,t3,t4);

        assertEquals(1,task1.getActualCount());
        assertEquals(1,task2.getActualCount());
        assertEquals(1,task3.getActualCount());
        assertEquals(1,task4.getActualCount());

        assertEquals(1,(int)t1.get());
        assertEquals(2,(int)t2.get());
        assertEquals(3,(int)t3.get());
        assertEquals(4,(int)t4.get());
    }

    @Test(expected= TimeoutExceededException.class)
    public void timeoutOrchestrator() throws Exception {
        $.setTimeout(5, TimeUnit.MILLISECONDS);
        $.task(task(0).delayFor(20)).ret(1).get();
    }

    @Test(expected= TimeoutExceededException.class)
    public void timeoutGlobal() throws Exception {
        GlobalConfig.INSTANCE.setTimeout(5, TimeUnit.MILLISECONDS);
        $.task(task(0).delayFor(20)).ret(1).get();
    }

    @Test(expected= TimeoutExceededException.class)
    public void timeoutGlobalSpawned() throws Exception {
        GlobalConfig.INSTANCE.setTimeout(5, TimeUnit.MILLISECONDS);
        $.task(task(0).delayFor(20)).runSpawned().ret(1).get();
    }

    @Test
    public void nestedFault() throws Exception {
        CompletableFuture<Integer> cf = $.task(task()).faultRecover($,3);
        int got = cf.get();
        assertEquals(3,got);
    }

    @Test
    public void faultPath2() throws Exception {
        String msg = "fault_path";
        int RV = 9;
        CompletableFuture<Integer> f1 = $.task(task()).ret(RV);
        CompletableFuture<Integer> f2 = $.task(new Faulty<Integer>()).faultAfter(f1,5,msg);
        try {
            f2.get();
        } catch (FaultHappened e) {
            int gotF1 = f1.get();
            assertEquals(RV,gotF1);
            return;
        }
        fail("No fault");
    }

    @Test
    public void faultSep() throws Exception {
        String msg = "fault_path";
        int RV = 9;
        CompletableFuture<Integer> l1 = $.task(task().delayFor(30)).name("left1").ret(RV);
        CompletableFuture<Integer> r1 = $.task(task().delayFor(5)).runSpawned().name("right1").ret(RV);
        CompletableFuture<Integer> r2 = $.task(new Faulty<Integer>()).name("right2-faulter").faultAfter(r1,1,msg);
        CompletableFuture<Integer> bottom = $.task(task(0)).name("bottom").add(l1,r2);
        try {
            bottom.get();
        } catch (FaultHappened e) {
            int gotF1 = l1.get();
            assertEquals(RV,gotF1);
            return;
        }
        fail("No fault");
    }

    @Test
    public void faultPath3() throws Exception {
        String msg = "fault_path";
        int RV = 9;
        CompletableFuture<Integer> f0 = $.task(task()).name("f0").ret(RV);
        CompletableFuture<Integer> f1 = $.task(task()).name("f1").ret(RV);
        CompletableFuture<Integer> f2 = $.task(new Faulty<Integer>()).name("f2").faultAfter(f1,5,msg);
        CompletableFuture<Integer> f3 = $.task(task(0)).name("f3").inc(f2);
        CompletableFuture<Integer> fadd = $.task(task(0)).add(f0,f3);
        try {
            fadd.get();
        } catch (FaultHappened e1) {
            assertEquals(msg,e1.getMessage());
            assertEquals(RV,(int)f0.get());
            assertEquals(RV,(int)f1.get());
            try {
                f3.get();
            } catch (FaultHappened e2) {
                return;
            }
            fail("No inner fault");
        }
        fail("No outer fault");
    }

    @Test
    public void doubleFault() throws Exception {
        String msg = "fault_path";
        CompletableFuture<Integer> fLeft = $.task(new Faulty<Integer>()).name("left").faultAfter(1,msg);
        CompletableFuture<Integer> fRight = $.task(new Faulty<Integer>()).name("right").faultAfter(1,msg);
        CompletableFuture<Integer> bottom = $.task(task(0)).name("bottom").add(fLeft,(fRight));
        try {
            bottom.get();
        } catch (FaultHappened e) {
            assertEquals(msg,e.getMessage());
            return;
        }
        fail("No fault");
    }

    void checkExceptionIsExpectedType(CompletableFuture<?> cf, Class<? extends Exception> ec) {
        try {
            cf.get();
        } catch (Exception e) {
            assertEquals(ec,e.getClass());
            return;
        }
        fail("Exception not thrown");
    }

    @Test
    public void splitSeparateFaultPaths() throws Exception {
        CompletableFuture<Integer> fLeftTop = $.task(task().delayFor(50)).name("leftTop").ret(1);
        CompletableFuture<Integer> fLeftMid = $.task(task(0)).name("leftMid").inc(fLeftTop);
        CompletableFuture<Integer> fRightTop = $.task(task().delayFor(0)).name("rightTop").ret(10);
        ExceptionTask<Integer> ft = faulty();
        CompletableFuture<Integer> fRightMid = $.task(ft).name("rightMid").faultAfter(fRightTop, 0,"msg");

        assertEquals(10,(int)fRightTop.get());
        assertEquals(1,(int)fLeftTop.get());
        checkExceptionIsExpectedType(fRightMid,ExceptionTask.FaultHappened.class);
        checkExceptionIsExpectedType(fLeftMid, TaskNotStartedException.class);
    }

    @Test
    public void splitFaultPathsMerge() throws Exception {
        CompletableFuture<Integer> fLeftTop = $.task(task().delayFor(50)).name("leftTop").ret(1);
        CompletableFuture<Integer> fLeftMid = $.task(task(0)).name("leftMid").inc(fLeftTop);
        CompletableFuture<Integer> fRightTop = $.task(task().delayFor(0)).name("rightTop").ret(10);
        ExceptionTask<Integer> ft = faulty();
        CompletableFuture<Integer> fRightMid = $.task(ft).name("rightMid").faultAfter(fRightTop, 0,"msg");

        CompletableFuture<Integer> fAdd = $.task(task(0)).name("add").add(fLeftMid,(fRightMid));

        assertEquals(10,(int)fRightTop.get());
        assertEquals(1,(int)fLeftTop.get());
        checkExceptionIsExpectedType(fRightMid,ExceptionTask.FaultHappened.class);
        checkExceptionIsExpectedType(fLeftMid, TaskNotStartedException.class);
        checkExceptionIsExpectedType(fAdd,ExceptionTask.FaultHappened.class);
    }


    ///
    /// Adding tasks from nested tasks
    ///
    private void addFromNested(boolean spawn) throws Exception {
        UberTask inner = new UberTasker();
        UberTask outer = new UberTasker() {
            @Override
            public CompletableFuture<Integer> ret(int x) {
                TaskInterface<UberTask> task = $.task(inner);
                if (spawn) {
                    task = task.runSpawned();
                }
                CompletableFuture<Integer> it = task.name("inner").ret(x+5);
                int n = get(it);
                return super.ret(x+n);
            }
        };
        TaskInterface<UberTask> outerTask = $.task(outer);
        if (spawn) {
            outerTask = outerTask.runSpawned();
        }
        int got = outerTask.name("outer").ret(1).get();
        assertEquals(7,got);
    }

    @Test
    public void addFromNested() throws Exception {
        addFromNested(false);
        addFromNested(true);
    }

    private void addFromNestedRefUnactivated(boolean spawn) throws Exception {
        Orchestrator $ = new Engine();
        CompletableFuture<Integer> slow = $.task(task().delayFor(50)).ret(5);
        AtomicReference<CompletableFuture<Integer>> it = new AtomicReference<>();
        UberTask outer = new UberTasker() {
            @Override
            public CompletableFuture<Integer> ret(int x) {
                TaskInterface<UberTask> task = $.task(task());
                if (spawn) {
                    task = task.runSpawned();
                }
                it.set( task.name("inner").inc(slow) );
                return super.ret(x);
            }
        };
        TaskInterface<UberTask> outerTask = $.task(outer);
        if (spawn) {
            outerTask = outerTask.runSpawned();
        }
        int got = outerTask.name("outer").ret(1).get();
        got += it.get().get();
        assertEquals(7,got);
    }

    @Test
    public void addFromNestedRefUnactivated() throws Exception {
        addFromNestedRefUnactivated(false);
        addFromNestedRefUnactivated(true);
    }

    @Test
    public void simpleCond() throws Exception {
        CompletableFuture<Boolean> c1 = $.task(task(1)).ret(true);
        CompletableFuture<Boolean> cond = $.cond(c1,c1,c1);
        boolean b = cond.get();
        assertTrue(b);
    }

    private void cond(boolean cond, int thenCount, boolean thenActivate, int elseCount, boolean elseActivate) throws Exception {
        int exp = cond ? 1 : 2;
        CompletableFuture<Boolean> c1 = $.task(task(1)).name("chooser").ret(cond);
        CompletableFuture<Integer> p1 = $.task(task(thenCount)).name("then").ret(1);
        CompletableFuture<Integer> p2 = $.task(task(elseCount)).name("else").ret(2);
        CompletableFuture<Integer> r = $.cond(c1,p1,thenActivate,p2,elseActivate);
        int got = r.get();
        assertEquals(exp,got);

        sleep(25);  // Give tasks time to complete and update actualCount
    }

    @Test
    public void addCondFalseFalse() throws Exception {
        cond(true,1,false,0,false);
        cond(false,0,false,1,false);
    }

    @Test
    public void addCondTrueFalse() throws Exception {
        cond(true,1,true,0,false);
        cond(false,1,true,1,false);
    }

    @Test
    public void addCondFalseTrue() throws Exception {
        cond(true, 1, false, 1, true);
        cond(false, 0, false, 1, true);
    }

    @Test
    public void addCondTrueTrue() throws Exception {
        cond(true,1,true,1,true);
        cond(false,1,true,1,true);
    }



    ///
    /// Fault handling
    ///
    private static final String MSG1 = "msg1";

    private void fault(Supplier<CompletableFuture<?>>fn, String expMsg) throws Exception {
        faultGet(fn.get(),expMsg);
    }
    private void faultGet(CompletableFuture<?>cf, String expMsg) throws Exception {
        try {
            cf.get();
        } catch (ExceptionTask.FaultHappened e) {
            assertEquals(expMsg,e.getMessage());
            return;
        }
        fail("No exception thrown");
    }

    @Test
    public void faultOneImmediate() throws Exception {
        fault(()->$.task(faulty()).faultImmediate(MSG1),MSG1);
    }

    @Test
    public void faultOneDelay() throws Exception {
        fault(()->$.task(faulty()).faultAfter(0,MSG1),MSG1);
    }

    @Test
    public void faultOneDelayComplete() throws Exception {
        fault(()->new Engine().task(faulty()).faultWithCompletionAfter(0,MSG1),MSG1);
    }

    @Test
    public void faultOneSpawnImmediate() throws Exception {
        fault(()->$.task(faulty()).runSpawned().faultImmediate(MSG1),MSG1);
    }

    @Test
    public void faultOneSpawnDelay() throws Exception {
        fault(()->$.task(faulty()).runSpawned().faultAfter(0,MSG1),MSG1);
    }

    @Test
    public void faultOneSpawnDelayComplete() throws Exception {
        fault(()->new Engine().task(faulty()).runSpawned().faultWithCompletionAfter(0,MSG1),MSG1);
    }

    @Test
    public void faultOneImmediateComplete() throws Exception {
        fault(()->new Engine().task(faulty()).faultImmediateCompletion(0,MSG1),MSG1);
    }

    @Test
    public void ensureFaultStopsNewTasks() throws Exception {
        // Restrict this test because in these modes everything is in the same thread and the order
        // of operations is non-deterministic, so we cannot definitively test execution counts.
        if (mode != SpawnMode.NEVER_SPAWN && mode != SpawnMode.DONT_SPAWN_UNLESS_EXPLICIT) {
            final String msg = "Bad";
            // The fault below will reach and pass through bottom before left has a chance to finish,
            // so it won't register has having executed (expected count should be 0)
            UberTask leftUberTop = task(0).delayFor(50);
            UberTask leftUberMid = task(0); // By the time this ready to start, exception should have occurred
            UberTask rightUberTop = task().delayFor(25);
            Faulty<Integer> rightUberMid = faulty();
            CompletableFuture<Integer> leftTop = $.task(leftUberTop).name("leftTop").ret(1);
            CompletableFuture<Integer> leftMid = $.task(leftUberMid).name("leftMid").inc(leftTop);
            CompletableFuture<Integer> rightTop = $.task(rightUberTop).name("rightTop").ret(5);
            CompletableFuture<Integer> rightMid = $.task(rightUberMid).name("rightMid").faultAfter(rightTop, 1, msg);
            CompletableFuture<Integer> bottom = $.task(task(0)).name("bottom").add(leftMid, (rightMid));
            faultGet(bottom, msg);
        }
    }
}
