/*-**********************************************************************
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
import com.ebay.bascomtask.exceptions.MisplacedTaskMethodException;
import com.ebay.bascomtask.exceptions.TaskNotStartedException;
import com.ebay.bascomtask.exceptions.TimeoutExceededException;
import com.ebay.bascomtask.util.CommonTestingUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.ebay.bascomtask.core.UberTask.*;
import static com.ebay.bascomtask.core.ExceptionTask.*;

/**
 * Core BascomTask execution tests. These should be runnable before anything test files.
 *
 * @author Brendan McCarthy
 */
@RunWith(Parameterized.class)
public class CoreTest extends BaseOrchestratorTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
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

    private SpawnMode getEffectiveMode() {
        return mode == null ? SpawnMode.WHEN_NEEDED : mode;
    }

    private void checkSameThreads(UberTask t1, UberTask t2, boolean sameIfNormalMode, boolean ifNeverMain, boolean ifAlwaysSpawn) {
        boolean same = t1.ranInSameThread(t2);
        boolean cmp;
        SpawnMode mode = getEffectiveMode();
        switch (mode) {
            case NEVER_SPAWN:
            case DONT_SPAWN_UNLESS_EXPLICIT:
                cmp = true;
                break;
            case NEVER_MAIN:
                cmp = ifNeverMain;
                break;
            case WHEN_NEEDED:
            case WHEN_NEEDED_NO_REUSE:
                cmp = sameIfNormalMode;
                break;
            case ALWAYS_SPAWN:
                cmp = ifAlwaysSpawn;
                break;
            default:
                throw new RuntimeException("Bad mode");
        }
        assertEquals(cmp, same);
    }

    private void checkInMainThread(UberTask t1, boolean sameIfNormalMode, boolean sameUnlessExplicit, boolean ifNeverMain, boolean ifAlwaysSpawn) {
        String tn = Thread.currentThread().getName();
        boolean same = tn.equals(t1.getThreadName());
        boolean cmp;
        SpawnMode mode = this.mode == null ? SpawnMode.WHEN_NEEDED : this.mode;
        switch (mode) {
            case NEVER_SPAWN:
                cmp = true;
                break;
            case ALWAYS_SPAWN:
                cmp = ifAlwaysSpawn;
                break;
            case NEVER_MAIN:
                cmp = ifNeverMain;
                break;
            case DONT_SPAWN_UNLESS_EXPLICIT:
                cmp = sameUnlessExplicit;
                break;
            case WHEN_NEEDED:
            case WHEN_NEEDED_NO_REUSE:
                cmp = sameIfNormalMode;
                break;
            default:
                throw new RuntimeException("Bad mode");
        }
        assertEquals("Task " + t1, cmp, same);
    }


    private void threadCheck(int whenNeeded, int whenNeededNoReuse, int neverMain, int alwaysSpawn, int dontSpawnUnlessExplicit) {
        final SpawnMode mode = getEffectiveMode();
        int expThreadCount;
        switch (mode) {
            case WHEN_NEEDED:
                expThreadCount = whenNeeded;
                break;
            case WHEN_NEEDED_NO_REUSE:
                expThreadCount = whenNeededNoReuse;
                break;
            case NEVER_MAIN:
                expThreadCount = neverMain;
                break;
            case ALWAYS_SPAWN:
                expThreadCount = alwaysSpawn;
                break;
            case NEVER_SPAWN:
                expThreadCount = 0;
                break;
            case DONT_SPAWN_UNLESS_EXPLICIT:
                expThreadCount = dontSpawnUnlessExplicit;
                break;
            default:
                throw new RuntimeException("Unexpected mode " + mode);
        }
        assertEquals("number of threads spawned", expThreadCount, $.getCountOfThreadsSpawned());
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
        assertEquals(task.getName(), tw.getName());
        String nm = "__!!__";
        tw.name(nm);
        assertEquals(nm, tw.getName());
    }

    @Test
    public void naming() {
        naming(new NamingTask.OverridesNothing());
        naming(new NamingTask.OverridesGet());
        naming(new NamingTask.OverridesGetAndSet());
    }

    @Test(expected = RuntimeException.class)
    public void nopsOnTaskInterface() {
        NamingTask task = new NamingTask.OverridesNothing();
        assertEquals(task, task.light());
        assertEquals(task, task.runSpawned());
        assertEquals(NamingTask.OverridesNothing.class.getSimpleName(), $.task(task).getName());
        task.name("etc");
    }

    @Test(expected = RuntimeException.class)
    public void faultOnGet() {
        CompletableFuture<Integer> cf = new CompletableFuture<>();
        cf.completeExceptionally(new RuntimeException());
        task(0).inc(cf);  // Should fault on get(cf)
    }

    @Test
    public void defaultLightFalse() {
        IThreadTask task = new IThreadTask.ThreadTask();
        assertEquals(Thread.currentThread(), $.task(task).computeAnnotated().join());
        if (mode==SpawnMode.NEVER_MAIN || mode==SpawnMode.ALWAYS_SPAWN) {
            assertNotEquals(Thread.currentThread(), $.task(task).computeNotAnnotated().join());
        }
    }

    @Test
    public void defaultLightTrue() {
        IThreadTask task = new IThreadTask.ThreadTask().light();
        assertEquals(Thread.currentThread(),$.task(task).computeAnnotated().join());
        if (mode==SpawnMode.NEVER_MAIN || mode==SpawnMode.ALWAYS_SPAWN) {
            assertEquals(Thread.currentThread(),$.task(task).computeNotAnnotated().join());
        }
    }

    @Test
    public void defaultLightFalseLightTrue() {
        IThreadTask task = new IThreadTask.ThreadTask();
        assertEquals(Thread.currentThread(), $.task(task).computeAnnotated().join());
        if (mode==SpawnMode.NEVER_MAIN || mode==SpawnMode.ALWAYS_SPAWN) {
            assertEquals(Thread.currentThread(), $.task(task).light().computeNotAnnotated().join());
        }
    }

    @Test
    public void defaultRunSpawned() {
        IThreadTask task = new IThreadTask.ThreadTask().runSpawned();

        if (mode==SpawnMode.NEVER_SPAWN) {
            assertEquals(Thread.currentThread(), $.task(task).computeAnnotated().join());
            assertEquals(Thread.currentThread(), $.task(task).computeNotAnnotated().join());
        } else {
            assertEquals(Thread.currentThread(), $.task(task).computeAnnotated().join());
            assertNotEquals(Thread.currentThread(), $.task(task).computeNotAnnotated().join());
            assertEquals(Thread.currentThread(), $.task(task).light().computeNotAnnotated().join());
        }
    }

    @Test
    public void defaultActivate() {
        IThreadTask.ThreadTask task = new IThreadTask.ThreadTask();
        $.task(task).activate().computeAnnotated();
        assertTrue(task.calledAnnotated);
    }

    @Test(expected = MisplacedTaskMethodException.class)
    public void activateNotOverridden() {
        new UberTask.UberTasker(0).activate();
    }

    @Test(expected = MisplacedTaskMethodException.class)
    public void lightNotOverridden() {
        new UberTask.UberTasker(0).light();
    }

    @Test
    public void singleNoArgs() throws Exception {
        UberTasker task = task();
        UberTask top = $.task(task);
        CompletableFuture<Integer> cf = top.retValueOne();
        int got = cf.get();
        assertEquals(1, got);
    }

    @Test
    public void singleNoArgsOnWrapper() throws Exception {
        UberTasker task = task();
        UberTask top = $.task(task);
        CompletableFuture<Integer> cf = top.retValueOne();
        int got = cf.get();
        assertEquals(1, got);
    }

    @Test
    public void singleRet() throws Exception {
        UberTasker task = task();
        UberTask top = $.task(task);
        CompletableFuture<Integer> cf = top.ret(1);
        int got = cf.get();
        assertEquals(1, got);
    }

    @Test(expected = InvalidTaskMethodException.class)
    public void doubleTask() {
        $.task($.task(task(0))).ret(1);
    }

    @Test
    public void singleRetNamed() throws Exception {
        String name = "foobar";
        int got = $.task(task()).name(name).ret(1).get();
        assertEquals(1, got);
    }

    @Test
    public void doubleIncrement() throws Exception {
        UberTask top = $.task(task());
        CompletableFuture<Integer> tf = top.ret(1);
        UberTask mid = $.task(task());
        CompletableFuture<Integer> mf = mid.inc(tf);
        int got = mf.get();
        assertEquals(2, got);

        boolean sameThread = top.ranInSameThread(mid);
        if (mode == SpawnMode.ALWAYS_SPAWN) {
            sameThread = !sameThread;
        }
        assertTrue(sameThread);
    }

    @Test
    public void simpleVWithNaming() throws Exception {
        $.setName("face");
        CompletableFuture<Integer> leftEar = $.task(task()).name("leftEar").ret(1);
        CompletableFuture<Integer> rightEar = $.task(task()).name("rightEar").ret(2);
        CompletableFuture<Integer> nose = $.task(task()).name("nose").add(leftEar, (rightEar));

        assertEquals(3, (int) nose.get());  // Execute first so all are executed and we can test threads below
        assertEquals(1, (int) leftEar.get());
        assertEquals(2, (int) rightEar.get());

        threadCheck(1, 1, 2, 3, 0);
    }

    @Test
    public void spawnedVWithNaming() throws Exception {
        $.setName("face");
        CompletableFuture<Integer> leftEar = $.task(task()).runSpawned().name("leftEar").ret(1);
        CompletableFuture<Integer> rightEar = $.task(task()).runSpawned().name("rightEar").ret(2);
        CompletableFuture<Integer> nose = $.task(task()).runSpawned().name("nose").add(leftEar, (rightEar));

        assertEquals(3, (int) nose.get());  // Execute first so all are executed and we can test threads below
        assertEquals(1, (int) leftEar.get());
        assertEquals(2, (int) rightEar.get());

        threadCheck(2, 3, 3, 3, 2);
    }

    private void vAdd(Weight leftWeight, Weight rightWeight, boolean sameIfNormalMode, boolean ifNeverMain, boolean ifAlwaysSpawn) throws Exception {
        UberTask left = $.task(task());
        UberTask right = $.task(task());
        CompletableFuture<Integer> lv = leftWeight.ret(left, 1);
        CompletableFuture<Integer> rv = rightWeight.ret(right, 5);
        CompletableFuture<Integer> v = $.task(task()).add(lv, rv);
        int got = v.get();
        assertEquals(6, got);

        checkSameThreads(left, right, sameIfNormalMode, ifNeverMain, ifAlwaysSpawn);
    }

    @Test
    public void vAdd() throws Exception {
        vAdd(Weight.LIGHT, Weight.LIGHT, true, true, true);
        vAdd(Weight.LIGHT, Weight.HEAVY, true, false, false);
        vAdd(Weight.HEAVY, Weight.LIGHT, true, false, false);
        vAdd(Weight.HEAVY, Weight.HEAVY, false, false, false);
    }

    private void diamond(Weight leftWeight, Weight rightWeight, boolean sameIfNormalMode, boolean ifAlwaysSpawn) throws Exception {
        UberTask top = $.task(task());
        UberTask left = $.task(task());
        UberTask right = $.task(task());
        UberTask bottom = $.task(task());
        CompletableFuture<Integer> tf = top.ret(1);
        CompletableFuture<Integer> lf = leftWeight.inc(left, tf);
        CompletableFuture<Integer> rf = rightWeight.inc(right, tf);
        CompletableFuture<Integer> bf = bottom.add(lf, rf);
        int got = bf.get();
        assertEquals(4, got);

        checkSameThreads(left, right, sameIfNormalMode, sameIfNormalMode, ifAlwaysSpawn);
    }

    @Test
    public void diamond() throws Exception {
        diamond(Weight.LIGHT, Weight.LIGHT, true, true);
        diamond(Weight.LIGHT, Weight.HEAVY, true, false);
        diamond(Weight.HEAVY, Weight.LIGHT, true, false);
        diamond(Weight.HEAVY, Weight.HEAVY, false, false);
    }

    @Test
    public void singleFutureArg() throws Exception {
        CompletableFuture<Integer> cf = CompletableFuture.supplyAsync(() -> {
            sleep(100);
            return 3;
        });
        CompletableFuture<Integer> cp = $.task(task()).inc(cf);
        int got = cp.get();
        assertEquals(4, got);
    }

    @Test
    public void add3() throws Exception {
        UberTask task = task(5);
        CompletableFuture<Integer> top = $.task(task).ret(1);
        CompletableFuture<Integer> left = $.task(task).inc(top);
        CompletableFuture<Integer> rcf = CompletableFuture.supplyAsync(() -> {
            sleep(300);
            return 3;
        });
        CompletableFuture<Integer> right = $.task(task).add((top), rcf);
        CompletableFuture<Integer> middle = $.task(task).add(left, (right));
        CompletableFuture<Integer> cp = $.task(task).add(left, middle, right);
        int got = cp.get();
        assertEquals(12, got);
    }

    @Test
    public void forceRunSpawned() throws Exception {
        UberTask task = task();
        int got = $.task(task).runSpawned().ret(1).get();
        assertEquals(1, got);
        checkInMainThread(task, false, false, false, false);
    }

    @Test
    public void forceWeightV() throws Exception {
        UberTask task1 = task();
        UberTask task2 = task();
        UberTask task3 = task();
        CompletableFuture<Integer> f1 = $.task(task1).runSpawned().ret(1);
        CompletableFuture<Integer> f2 = $.task(task2).light().ret(2);
        CompletableFuture<Integer> f3 = $.task(task3).runSpawned().ret(3);
        CompletableFuture<Integer> added = $.task(task()).add(f1, f2, f3);
        int got = added.get();
        assertEquals(6, got);

        checkInMainThread(task1, false, false, false, false);
        checkInMainThread(task2, true, true, true, true);
        checkInMainThread(task3, false, false, false, false);
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
        CompletableFuture<Integer> added = $.task(task()).add(f1, f2, f3);
        int got = added.get();
        assertEquals(6, got);

        checkInMainThread(baseTask, true, true, true, true);
        checkInMainThread(task1, false, false, false, false);
        checkInMainThread(task2, true, true, true, true);
        checkInMainThread(task3, false, false, false, false);
    }

    @Test
    public void onlyActivatedTasksRun() throws Exception {
        task(0);
        UberTask shouldRun = task(2);

        CompletableFuture<Integer> base = $.task(shouldRun).ret(1);
        CompletableFuture<Integer> left = $.task(shouldRun).inc(base);

        int got = left.get();
        assertEquals(2, got);
    }

    @Test
    public void multipleReturns() throws Exception {
        int delay = 20; // Enough to ensure that they both have while other may have started and is delayed
        CompletableFuture<Integer> t1 = $.task(task().delayFor(delay)).ret(1);
        CompletableFuture<Integer> t2 = $.task(task().delayFor(delay)).ret(1);

        $.execute(t1, t2);

        // These ensure we don't test endingTime before it is actually set, since at this point the final
        // bookkeeping/processing on t1 and t2 might not have completed (very small window)
        assertNotNull(t1.get());
        assertNotNull(t2.get());

        TaskMeta m1 = $.getTaskMeta(t1);
        TaskMeta m2 = $.getTaskMeta(t2);
        CommonTestingUtils.validateTimes(m1);
        CommonTestingUtils.validateTimes(m2);
        if (mode != SpawnMode.NEVER_SPAWN && mode != SpawnMode.DONT_SPAWN_UNLESS_EXPLICIT) {
            assertTrue(m1.overlapped(m2));
        }
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
        assertEquals(1, got);
    }

    @Test
    public void baseLight() throws Exception {
        CompletableFuture<Integer> tf = $.task(task()).light().ret(1);
        CompletableFuture<Integer> lf = $.task(task()).inc(tf);
        CompletableFuture<Integer> rf = $.task(task()).inc(tf);
        CompletableFuture<Integer> bf = $.task(task()).add(lf, rf);
        int got = bf.get();
        assertEquals(4, got);
    }

    @Test
    public void externals() throws Exception {
        CompletableFuture<Integer> e1 = CompletableFuture.supplyAsync(() -> sleepThen(15, 1));
        CompletableFuture<Integer> e2 = CompletableFuture.supplyAsync(() -> sleepThen(5, 2));
        CompletableFuture<Integer> e3 = CompletableFuture.supplyAsync(() -> sleepThen(25, 3));
        CompletableFuture<Integer> add = $.task(task()).add(e1, e2, e3);
        int got = add.get();
        assertEquals(6, got);
    }

    @Test
    public void externalToExternal() throws Exception {
        CompletableFuture<Integer> cf1 = CompletableFuture.supplyAsync(() -> sleepThen(20, 1));
        CompletableFuture<Integer> cfi = $.task(task()).inc(cf1);
        $.execute(cfi);
        CompletableFuture<Integer> cf2 = cfi.thenApply(v -> v + 1);
        assertEquals(3, (int) cf2.get());
    }

    @Test
    public void fromMultipleOrchestrators() {
        Orchestrator ox = Orchestrator.create("ox");
        Orchestrator oy = Orchestrator.create("oy");
        CompletableFuture<Integer> cf1 = ox.task(task()).ret(1);
        CompletableFuture<Integer> cf2 = oy.task(task()).inc(cf1);
        CompletableFuture<Integer> cf3 = ox.task(task()).inc(cf2);
        CompletableFuture<Integer> cf4 = oy.task(task()).add(cf1,cf2,cf3);
        assertEquals(6,(int)cf4.join());
    }

    private <T extends TaskInterface<T>> T spawn(T t, boolean spawn) {
        if (spawn) {
            t = t.runSpawned();
        }
        return t;
    }

    @Test
    public void orchestratorCreate() {
        class Holder {
            Orchestrator orc;
            Object arg;
        }
        Holder holder = new Holder();
        GlobalOrchestratorConfig.getConfig().initializeWith((orc,arg)->{holder.orc = orc; holder.arg=arg;});
        Object x = new Object();
        String name = "sample_name";
        $ = Orchestrator.create(name,x);
        assertEquals(name,$.getName());
        assertSame($,holder.orc);
        assertSame(x,holder.arg);
    }

    public void poolSizeExceeded(boolean spawn, Function<ExecutorService, Orchestrator> fn) throws Exception {
        ExecutorService svc = Executors.newFixedThreadPool(1);
        Orchestrator $ = fn.apply(svc);
        UberTask task = task(3).delayFor(100);
        CompletableFuture<Integer> t1 = spawn($.task(task), spawn).ret(1);
        CompletableFuture<Integer> t2 = spawn($.task(task), spawn).ret(2);
        CompletableFuture<Integer> t3 = spawn($.task(task), spawn).ret(4);
        CompletableFuture<Integer> add = spawn($.task(task().delayFor(0)), spawn).add(t1, t2, t3);
        int got = add.get();
        assertEquals(7, got);
    }

    private void poolSizeExceededGlobal(boolean spawn) throws Exception {
        poolSizeExceeded(spawn, svc -> {
            GlobalOrchestratorConfig.getConfig().setExecutorService(svc);
            return new Engine("foo", null);
        });
    }

    private void poolSizeExceededLocal(boolean spawn) throws Exception {
        poolSizeExceeded(spawn, svc -> {
            $.setExecutorService(svc);
            return $;
        });
    }

    @Test
    public void poolSizeExceededGlobal() throws Exception {
        poolSizeExceededGlobal(false);
    }

    @Test
    public void poolSizeExceededLocal() throws Exception {
        poolSizeExceededLocal(false);
    }

    @Test
    public void poolSizeExceededGlobalSpawning() throws Exception {
        poolSizeExceededGlobal(true);
    }

    @Test
    public void poolSizeExceededLocalSpawning() throws Exception {
        poolSizeExceededLocal(true);
    }

    @Test
    public void reuseMainThread() throws Exception {
        if ($.getSpawnMode() == SpawnMode.WHEN_NEEDED) {
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
        checkInMainThread(task, true, true, false, false);

        task = task();
        $.task(task).light().ret(1).get();
        checkInMainThread(task, true, true, true, true);
    }

    @Test
    public void noWaiting() throws Exception {
        int wait = 25;
        UberTasker task = task().delayFor(wait);
        CompletableFuture<Integer> t1 = $.task(task).name("delayed").runSpawned().ret(1);
        CompletableFuture<Integer> t2 = $.task(task().delayFor(0)).name("fast").ret(2);
        $.execute(t1, t2);
        int got = t2.get();
        int exp = mode == SpawnMode.NEVER_SPAWN ? 1 : 0;
        assertEquals(exp, task.getActualCount());
        assertEquals(2, got);
        sleep(wait + 5);
        assertEquals(1, task.getActualCount());
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

        $.executeAndWait(t1, t2, t3, t4);

        assertEquals(1, task1.getActualCount());
        assertEquals(1, task2.getActualCount());
        assertEquals(1, task3.getActualCount());
        assertEquals(1, task4.getActualCount());

        assertEquals(1, (int) t1.get());
        assertEquals(2, (int) t2.get());
        assertEquals(3, (int) t3.get());
        assertEquals(4, (int) t4.get());
    }

    @Test
    public void executeFuture() throws Exception {
        final int wait = 20;
        UberTasker fasterTask = task(2).delayFor(wait);
        UberTasker slowerTask = task().delayFor(wait*2);
        CompletableFuture<Integer> t1 = $.task(fasterTask).name("fast1").ret(1);
        CompletableFuture<Integer> t2 = $.task(fasterTask).name("fast2").ret(2);
        CompletableFuture<Integer> tSlow = $.task(slowerTask).name("slow3").ret(3);

        List<CompletableFuture<Integer>> list = Arrays.asList(t1,t2,tSlow);
        CompletableFuture<List<Integer>> waitFuture = $.executeFuture(list);
        Map<Integer,Long> map = new HashMap<>();
        waitFuture.thenAccept(fs-> {
            Long time = System.nanoTime();
            fs.forEach(v->map.put(v,time));
        });

        long after = System.nanoTime();
        Thread.sleep(wait*3); // Give enough time for tasks to complete

        assertEquals(list.size(),map.size());
        list.forEach(cf->{
            assertTrue(cf.isDone());
            Integer v = cf.join();
            if (mode == SpawnMode.NEVER_SPAWN || mode == SpawnMode.DONT_SPAWN_UNLESS_EXPLICIT) {
                Long completionTime = map.get(v);
                assertTrue(completionTime < after);
            }
        });
    }

    @Test
    public void executeFutureTimeout() throws Exception {
        int timeoutMs = 10;
        int slowness = timeoutMs*3;
        CompletableFuture<Integer> t1 = $.task(task(1).delayFor(slowness)).ret(0);
        // This shouldn't be executed because the first task exceeds timeout
        CompletableFuture<Integer> t2 = $.task(task(0).delayFor(0)).inc(t1);
        List<CompletableFuture<Integer>> list = Collections.singletonList(t2);
        CompletableFuture<List<Integer>> waitFuture = $.executeFuture(timeoutMs,TimeUnit.MILLISECONDS,list);
        waitFuture.thenAccept(fs->System.out.println("Shouldn't see this"));
        Thread.sleep(slowness*2); // Give time for completion
    }

    @Test(expected = TimeoutExceededException.class)
    public void executeAndWaitTimeout() {
        int timeoutMs = 10;
        int slowness = timeoutMs*3;
        CompletableFuture<Integer> t1 = $.task(task(1).delayFor(slowness)).ret(0);
        // This shouldn't be executed because the first task exceeds timeout
        CompletableFuture<Integer> t2 = $.task(task(0).delayFor(0)).inc(t1);
        List<CompletableFuture<Integer>> list = Collections.singletonList(t2);
        $.executeAndWait(timeoutMs,TimeUnit.MILLISECONDS,list);
    }

    @Test
    public void executeAsReadyInExpectedOrder() throws Exception {
        int spread = 10;
        // Create tasks completing a 'spread' intervals
        CompletableFuture<Integer> t1 = $.task(task(1).delayFor(spread  )).name("t1").ret(1);
        CompletableFuture<Integer> t2 = $.task(task(1).delayFor(spread*3)).name("t2").ret(2);
        CompletableFuture<Integer> t3 = $.task(task(1).delayFor(0       )).name("t3").ret(3);
        CompletableFuture<Integer> t4 = $.task(task(1).delayFor(spread*2)).name("t4").ret(4);

        class Track {
            int result;
            long timeFromStart;
            int count;
        }
        List<Track> results = new ArrayList<>();
        long start = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(4);
        $.executeAsReady(Arrays.asList(t1, t2, t3, t4), (t,ex,count)-> {
            Track track = new Track();
            track.result = t;
            track.timeFromStart = System.currentTimeMillis() - start;
            track.count = count;
            results.add(track);
            latch.countDown();
        });
        latch.await();
        assertEquals(4,results.size());
        boolean spawning = !(mode == SpawnMode.NEVER_SPAWN | mode == SpawnMode.DONT_SPAWN_UNLESS_EXPLICIT);
        List<Integer> order = spawning ? Arrays.asList(3,1,4,2) : Arrays.asList(1,2,3,4);
        assertEquals(order,results.stream().map(t->t.result).collect(Collectors.toList()));
        assertEquals(Arrays.asList(3,2,1,0),results.stream().map(t->t.count).collect(Collectors.toList()));
        if (spawning) {
            assertTrue(results.get(0).timeFromStart < results.get(2).timeFromStart - spread);
            assertTrue(results.get(1).timeFromStart < results.get(3).timeFromStart - spread);
        }
    }

    @Test
    public void executeAsReadyTimeout() throws Exception {
        int spread = 5;
        int timeoutMs = spread*4;
        // If !spawning then the timeout will extend past t3 and affect t4
        boolean spawning = !(mode == SpawnMode.NEVER_SPAWN | mode == SpawnMode.DONT_SPAWN_UNLESS_EXPLICIT);
        CompletableFuture<Integer> t1 = $.task(task(1).delayFor(spread*2)).name("t1").ret(1);
        CompletableFuture<Integer> t2 = $.task(task(1).delayFor(spread*6)).name("t2").ret(2);
        CompletableFuture<Integer> t3 = $.task(task(0).delayFor(spread)).name("t3").inc(t2);
        CompletableFuture<Integer> t4 = $.task(task(spawning?1:0).delayFor(spread*3)).name("t4").ret(4);
        Set<Integer> set = new HashSet<>();
        List<Throwable> exs = new ArrayList<>();
        $.executeAsReady(timeoutMs, TimeUnit.MILLISECONDS, Arrays.asList(t1, t2, t3, t4), (t,ex,count)-> {
            if (ex == null) {
                set.add(t);
            } else {
                exs.add(ex);
            }
        });
        Thread.sleep(spread*10); // Give time for completion
        assertTrue(set.contains(1));
        assertTrue(set.contains(2));
        if (spawning) {
            assertTrue(set.contains(4));
        }
        assertEquals(spawning?1:2,exs.size());
        assertTrue(exs.get(0) instanceof TimeoutExceededException);
    }

    @Test(expected = TimeoutExceededException.class)
    public void timeoutOrchestrator() throws Exception {
        long duration = 5;
        $.setTimeout(duration, TimeUnit.MILLISECONDS);
        assertEquals(duration, $.getTimeoutMs());
        CompletableFuture<Integer> f1 = $.task(task(1).delayFor(60)).name("delayed").ret(1);
        $.task(task(0)).name("follow").inc(f1).get();
    }

    @Test(expected = TimeoutExceededException.class)
    public void timeoutGlobal() throws Exception {
        GlobalOrchestratorConfig.getConfig().setTimeout(20, TimeUnit.MILLISECONDS);
        $ = Orchestrator.create("timer");  // So it picks up global settings
        CompletableFuture<Integer> f1 = $.task(task(1).delayFor(60)).name("delayed").ret(1);
        $.task(task(0)).name("follow").inc(f1).get();
    }

    @Test(expected = TimeoutExceededException.class)
    public void timeoutGlobalSpawned() throws Exception {
        GlobalOrchestratorConfig.getConfig().setTimeout(20, TimeUnit.MILLISECONDS);
        $ = Orchestrator.create("timer");  // So it picks up global settings
        CompletableFuture<Integer> f1 = $.task(task(1).delayFor(60)).runSpawned().name("delayed").ret(1);
        $.task(task(0)).name("follow").inc(f1).get();
    }

    @Test(expected = TimeoutExceededException.class)
    public void timeoutGetSpawned() throws Exception {
        CompletableFuture<Integer> f1 = $.task(task(1).delayFor(60)).runSpawned().name("delayed").ret(1);
        $.task(task(0)).name("follow").inc(f1).get(20, TimeUnit.MILLISECONDS);
    }

    @Test(expected = TimeoutExceededException.class)
    public void timeouOnOnePathOnly() throws Exception {
        CompletableFuture<Integer> f1 = $.task(task(1).delayFor(60)).runSpawned().name("f1-delayed").ret(1);
        CompletableFuture<Integer> f2 = $.task(task(0)).name("f2-follow").inc(f1);

        int exp = mode == SpawnMode.NEVER_SPAWN ? 0 : 1;
        CompletableFuture<Integer> g1 = $.task(task(exp).delayFor(4)).runSpawned().name("g1-delayed").ret(1);
        CompletableFuture<Integer> g2 = $.task(task(exp)).name("g2-follow").inc(g1);

        $.execute(20, TimeUnit.MILLISECONDS, f2, g2);
        assertEquals(2, (int) g2.get());
        f2.get();
    }

    private void timeout(TimeoutStrategy strategy, int exp) throws Exception {
        try {
            $.setTimeoutStrategy(strategy);
            $.setTimeoutMs(5);
            CompletableFuture<Integer> f1 = $.task(task(exp).delayFor(20)).name("f1").ret(1);

            CompletableFuture<Integer> f2 = $.task(task(0).delayFor(60)).name("f2").ret(1);
            CompletableFuture<Integer> f3 = $.task(task(0)).runSpawned().name("f3").add(f1, f2);
            f3.get();
        } catch (TaskInterruptedException | TimeoutExceededException e) {
            return;
        }
        fail("No exception");
    }

    @Test
    public void timeoutNextOpportunity() throws Exception {
        timeout(TimeoutStrategy.INTERRUPT_AT_NEXT_OPPORTUNITY, 1);
    }

    @Test
    public void timeoutImmediately() throws Exception {
        int exp = (mode == SpawnMode.NEVER_SPAWN || mode == SpawnMode.DONT_SPAWN_UNLESS_EXPLICIT) ? 1 : 0;
        timeout(TimeoutStrategy.INTERRUPT_IMMEDIATELY, exp);
    }

    @Test
    public void waitAndtimeoutImmediately() throws Exception {
        try {
            $.setTimeoutStrategy(TimeoutStrategy.INTERRUPT_IMMEDIATELY);
            int exp = mode == SpawnMode.NEVER_SPAWN || mode == SpawnMode.DONT_SPAWN_UNLESS_EXPLICIT ? 1 : 0;
            CompletableFuture<Integer> f1 = $.task(task(exp).delayFor(20)).name("f1").ret(1);
            CompletableFuture<Integer> f2 = $.task(task(0).delayFor(20)).name("f2").ret(1);
            CompletableFuture<Integer> f3 = $.task(task(0).delayFor(20)).name("f3").ret(1);

            $.executeAndWait(5, TimeUnit.MILLISECONDS, f1, f2, f3);
            f1.get();
            f2.get();
            f3.get();
        } catch (TaskInterruptedException | TimeoutExceededException e) {
            return;
        }
        fail("No timeout");
    }

    @Test
    public void nestedFault() throws Exception {
        CompletableFuture<Integer> cf = $.task(task()).faultRecover($, 3);
        int got = cf.get();
        assertEquals(3, got);
    }

    @Test
    public void faultPath2() throws Exception {
        String msg = "fault_path";
        int RV = 9;
        CompletableFuture<Integer> f1 = $.task(task()).ret(RV);
        CompletableFuture<Integer> f2 = $.task(new Faulty<Integer>()).faultAfter(f1, 5, msg);
        try {
            f2.get();
        } catch (FaultHappened e) {
            int gotF1 = f1.get();
            assertEquals(RV, gotF1);
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
        CompletableFuture<Integer> r2 = $.task(new Faulty<Integer>()).name("right2-faulter").faultAfter(r1, 1, msg);
        CompletableFuture<Integer> bottom = $.task(task(0)).name("bottom").add(l1, r2);
        try {
            bottom.get();
        } catch (FaultHappened e) {
            int gotF1 = l1.get();
            assertEquals(RV, gotF1);
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
        CompletableFuture<Integer> f2 = $.task(new Faulty<Integer>()).name("f2").faultAfter(f1, 5, msg);
        CompletableFuture<Integer> f3 = $.task(task(0)).name("f3").inc(f2);
        CompletableFuture<Integer> fadd = $.task(task(0)).add(f0, f3);
        try {
            fadd.get();
        } catch (FaultHappened e1) {
            assertEquals(msg, e1.getMessage());
            assertEquals(RV, (int) f0.get());
            assertEquals(RV, (int) f1.get());
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
        CompletableFuture<Integer> fLeft = $.task(new Faulty<Integer>()).name("left").faultAfter(1, msg);
        CompletableFuture<Integer> fRight = $.task(new Faulty<Integer>()).name("right").faultAfter(1, msg);
        CompletableFuture<Integer> bottom = $.task(task(0)).name("bottom").add(fLeft, (fRight));
        try {
            bottom.get();
        } catch (FaultHappened e) {
            assertEquals(msg, e.getMessage());
            return;
        }
        fail("No fault");
    }

    void checkExceptionIsExpectedType(CompletableFuture<?> cf, Class<? extends Exception> ec) {
        try {
            cf.get();
        } catch (Exception e) {
            assertEquals(ec, e.getClass());
            return;
        }
        fail("Exception not thrown");
    }

    @Test
    public void splitSeparateFaultPaths() throws Exception {
        CompletableFuture<Integer> fLeftTop = $.task(task().delayFor(50)).name("leftTop").ret(1);
        CompletableFuture<Integer> fLeftMid = $.task(task()).name("leftMid").inc(fLeftTop);
        CompletableFuture<Integer> fRightTop = $.task(task().delayFor(0)).name("rightTop").ret(10);
        ExceptionTask<Integer> ft = faulty();
        CompletableFuture<Integer> fRightMid = $.task(ft).name("rightMid").faultAfter(fRightTop, 0, "msg");

        assertEquals(10, (int) fRightTop.get());
        assertEquals(1, (int) fLeftTop.get());
        assertEquals(2, (int) fLeftMid.get());
        checkExceptionIsExpectedType(fRightMid, ExceptionTask.FaultHappened.class);
    }

    @Test
    public void splitFaultPathsMerge() throws Exception {
        CompletableFuture<Integer> fLeftTop = $.task(task().delayFor(50)).name("leftTop").ret(1);
        CompletableFuture<Integer> fLeftMid = $.task(task()).name("leftMid").inc(fLeftTop);
        CompletableFuture<Integer> fRightTop = $.task(task().delayFor(0)).name("rightTop").ret(10);
        ExceptionTask<Integer> ft = faulty();
        CompletableFuture<Integer> fRightMid = $.task(ft).name("rightMid").faultAfter(fRightTop, 0, "msg");

        CompletableFuture<Integer> fAdd = $.task(task(0)).name("add").add(fLeftMid, (fRightMid));

        assertEquals(10, (int) fRightTop.get());
        assertEquals(1, (int) fLeftTop.get());
        assertEquals(2, (int) fLeftMid.get());
        checkExceptionIsExpectedType(fRightMid, ExceptionTask.FaultHappened.class);
        checkExceptionIsExpectedType(fAdd, ExceptionTask.FaultHappened.class);
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
                CompletableFuture<Integer> it = task.name("inner").ret(x + 5);
                int n = get(it);
                return super.ret(x + n);
            }
        };
        TaskInterface<UberTask> outerTask = $.task(outer);
        if (spawn) {
            outerTask = outerTask.runSpawned();
        }
        int got = outerTask.name("outer").ret(1).get();
        assertEquals(7, got);
    }

    @Test
    public void addFromNested() throws Exception {
        addFromNested(false);
        addFromNested(true);
    }

    private void addFromNestedRefUnactivated(boolean spawn) throws Exception {
        CompletableFuture<Integer> slow = $.task(task().delayFor(50)).ret(5);
        AtomicReference<CompletableFuture<Integer>> it = new AtomicReference<>();
        UberTask outer = new UberTasker() {
            @Override
            public CompletableFuture<Integer> ret(int x) {
                TaskInterface<UberTask> task = $.task(task());
                if (spawn) {
                    task = task.runSpawned();
                }
                it.set(task.name("inner").inc(slow));
                return super.ret(x);
            }
        };
        TaskInterface<UberTask> outerTask = $.task(outer);
        if (spawn) {
            outerTask = outerTask.runSpawned();
        }
        int got = outerTask.name("outer").ret(1).get();
        got += it.get().get();
        assertEquals(7, got);
    }

    @Test
    public void addFromNestedRefUnactivated() throws Exception {
        addFromNestedRefUnactivated(false);
        addFromNestedRefUnactivated(true);
    }

    @Test
    public void singleCondTrue() throws Exception {
        CompletableFuture<Boolean> cb = $.task(task(1)).ret(true);
        CompletableFuture<Integer> c1 = $.task(task(1)).ret(2);
        Optional<Integer> cond = $.cond(cb, c1).get();
        assertTrue(cond.isPresent());
        assertEquals(2,(int)cond.get());
    }

    @Test
    public void singleCondFalse() throws Exception {
        CompletableFuture<Boolean> cb = $.task(task(1)).ret(false);
        CompletableFuture<Integer> c1 = $.task(task(0)).ret(2);
        Optional<Integer> cond = $.cond(cb, c1).get();
        assertFalse(cond.isPresent());
    }

    @Test
    public void doubleCond() throws Exception {
        CompletableFuture<Boolean> c1 = $.task(task(1)).ret(true);
        CompletableFuture<Boolean> cond = $.cond(c1, c1, c1);
        boolean b = cond.get();
        assertTrue(b);
    }

    private void cond(boolean cond, int thenCount, boolean thenActivate) throws Exception {
        CompletableFuture<Boolean> c1 = $.task(task(1)).name("chooser").ret(cond);
        CompletableFuture<Integer> p1 = $.task(task(thenCount)).name("then").ret(2);
        $.cond(c1, p1, thenActivate).get();

        sleep(25);  // Give tasks time to complete and update actualCount
    }

    @Test
    public void addCondFalse() throws Exception {
        cond(true, 1, false);
        cond(false, 0, false);
    }

    @Test
    public void addCondTrue() throws Exception {
        cond(true, 1, true);
        cond(false, 1, true);
    }

    private void cond(boolean cond, int thenCount, boolean thenActivate, int elseCount, boolean elseActivate) throws Exception {
        int exp = cond ? 1 : 2;
        CompletableFuture<Boolean> c1 = $.task(task(1)).name("chooser").ret(cond);
        CompletableFuture<Integer> p1 = $.task(task(thenCount)).name("then").ret(1);
        CompletableFuture<Integer> p2 = $.task(task(elseCount)).name("else").ret(2);
        CompletableFuture<Integer> r = $.cond(c1, p1, thenActivate, p2, elseActivate);
        int got = r.get();
        assertEquals(exp, got);

        sleep(25);  // Give tasks time to complete and update actualCount
    }

    @Test
    public void addCondFalseFalse() throws Exception {
        cond(true, 1, false, 0, false);
        cond(false, 0, false, 1, false);
    }

    @Test
    public void addCondTrueFalse() throws Exception {
        cond(true, 1, true, 0, false);
        cond(false, 1, true, 1, false);
    }

    @Test
    public void addCondFalseTrue() throws Exception {
        cond(true, 1, false, 1, true);
        cond(false, 0, false, 1, true);
    }

    @Test
    public void addCondTrueTrue() throws Exception {
        cond(true, 1, true, 1, true);
        cond(false, 1, true, 1, true);
    }

    @Test
    public void slowCond() throws Exception {
        CompletableFuture<Boolean> fCond = $.task(task().delayFor(20)).name("cond").ret(true);
        CompletableFuture<Integer> fThen = $.task(task().delayFor(1)).name("then").ret(1);
        CompletableFuture<Integer> fElse = $.task(task().delayFor(1)).name("else").ret(2);

        CompletableFuture<Integer> r = $.cond(fCond, fThen, true, fElse, true);

        assertEquals(1, (int) r.get());
    }

    @Test
    public void slowElse() throws Exception {
        CompletableFuture<Boolean> fCond = $.task(task().delayFor(1)).name("cond").ret(false);
        CompletableFuture<Integer> fThen = $.task(task().delayFor(1)).name("then").ret(1);
        CompletableFuture<Integer> fElse = $.task(task().delayFor(20)).name("else").ret(2);

        CompletableFuture<Integer> r = $.cond(fCond, fThen, true, fElse, true);

        assertEquals(2, (int) r.get());
    }

    ///
    /// Fault handling
    ///
    private static final String MSG1 = "msg1";
    private static final String MSG2 = "msg2";

    private void fault(Supplier<CompletableFuture<?>> fn, String expMsg) throws Exception {
        faultGet(fn.get(), expMsg);
    }

    private void faultGet(CompletableFuture<?> cf, String expMsg) throws Exception {
        try {
            cf.get();
        } catch (ExceptionTask.FaultHappened e) {
            assertEquals(expMsg, e.getMessage());
            return;
        }
        fail("No exception thrown");
    }

    @Test
    public void faultOneImmediate() throws Exception {
        fault(() -> $.task(faulty()).faultImmediate(MSG1), MSG1);
    }

    @Test
    public void faultOneDelay() throws Exception {
        fault(() -> $.task(faulty()).faultAfter(0, MSG1), MSG1);
    }

    @Test
    public void faultOneDelayComplete() throws Exception {
        fault(() -> $.task(faulty()).faultWithCompletionAfter(0, MSG2), MSG2);
    }

    @Test
    public void faultOneSpawnImmediate() throws Exception {
        fault(() -> $.task(faulty()).runSpawned().faultImmediate(MSG1), MSG1);
    }

    @Test
    public void faultOneSpawnDelay() throws Exception {
        fault(() -> $.task(faulty()).runSpawned().faultAfter(0, MSG1), MSG1);
    }

    @Test
    public void faultOneSpawnDelayComplete() throws Exception {
        fault(() -> $.task(faulty()).runSpawned().faultWithCompletionAfter(0, MSG1), MSG1);
    }

    @Test
    public void faultOneImmediateComplete() throws Exception {
        fault(() -> $.task(faulty()).faultImmediateCompletion(0, MSG1), MSG1);
    }

    @Test(expected = ExceptionTask.FaultHappened.class)
    public void faultPropagates() throws Exception {
        final String msg = "Bad";
        Faulty<Integer> faulty = faulty();
        CompletableFuture<Integer> f1 = $.task(faulty).name("f1").faultAfter(0, msg);
        CompletableFuture<Integer> f2 = $.task(task(0)).name("f2").inc(f1);
        f2.get();
    }

    @Test
    public void noFate0() throws Exception {
        CompletableFuture<Boolean> fb = $.fate();
        assertFalse(fb.get());
    }

    @Test
    public void noFate1() throws Exception {
        CompletableFuture<Integer> f1 = $.task(task()).ret(1);
        CompletableFuture<Boolean> fb = $.fate(f1);
        assertFalse(fb.get());
    }

    @Test
    public void yesFate1() throws Exception {
        Faulty<Void> faulty = new Faulty<>();
        CompletableFuture<Void> f1 = $.task(faulty).faultAfter(0, "msg");
        CompletableFuture<Boolean> fb = $.fate(f1);
        assertTrue(fb.get());
    }

    @Test
    public void fate3() throws Exception {
        String msg = "fault_message";
        Faulty<Void> faulty = new Faulty<>();
        CompletableFuture<Integer> f1 = $.task(task()).name("f1").ret(1);
        CompletableFuture<Void> f2 = $.task(faulty).name("f2").faultAfter(0, msg);
        final boolean spawning = mode != SpawnMode.NEVER_SPAWN && mode != SpawnMode.DONT_SPAWN_UNLESS_EXPLICIT;
        int exp = spawning ? 1 : 0; // If not spawning, then fate will cancel f3
        CompletableFuture<Integer> f3 = $.task(task(exp)).name("f3").ret(3);

        CompletableFuture<Boolean> fb = $.fate(f1, f2, f3);
        CompletableFuture<Integer> fr = $.task(task()).name("fr").ret(8);
        CompletableFuture<Optional<Integer>> fv = $.cond(fb, fr);
        fv.get();

        assertEquals(1, (int) f1.get());
        if (spawning) {
            assertEquals(3, (int) f3.get());
        } else {
            // f3 will not have started if everything in same thread, so it will have been canelled
            checkExceptionIsExpectedType(f3, TaskNotStartedException.class);
        }

        sleep(10); // Give time for tasks to complete
    }

    @Test
    public void faultReadme() throws Exception {
        CompletableFuture<Integer> f1 = $.task(task().delayFor(20)).name("f1").ret(1);
        CompletableFuture<Integer> f2 = $.task(task().delayFor(0)).name("f2").ret(2);
        CompletableFuture<Integer> f3 = $.task(task().delayFor(0)).name("f3").ret(3);

        final SpawnMode mode = getEffectiveMode();
        final boolean spawning = mode != SpawnMode.NEVER_SPAWN && mode != SpawnMode.DONT_SPAWN_UNLESS_EXPLICIT;
        int expExecCount = spawning ? 0 : 1;
        CompletableFuture<Integer> f4 = $.task(task(expExecCount)).name("f4").inc(f1);
        ExceptionTask<Integer> faulty = faulty();
        CompletableFuture<Integer> f5 = $.task(faulty).name("f5").faultAfter(f3, 0, "msg");

        CompletableFuture<Integer> f6 = $.task(task(expExecCount)).name("f6").inc(f4);

        CompletableFuture<Integer> f7 = $.task(task(expExecCount)).name("f7").inc(f6);
        CompletableFuture<Boolean> f8 = $.fate(f2, f5, f6);

        CompletableFuture<Integer> f9 = $.task(task(0)).name("f9").incIf(f7, f8);
        CompletableFuture<Boolean> f10 = $.task(task()).name("f10").invert(f8);
        CompletableFuture<Boolean> f11 = $.task(task()).name("f11").invert(f8);
        CompletableFuture<Integer> f12 = $.task(task(0)).name("f12").incIf(f5, f8);

        CompletableFuture<Integer> f13 = $.task(task(0)).name("f13").addb(f9, f10, f11, f12);

        checkExceptionIsExpectedType(f13, ExceptionTask.FaultHappened.class);
        checkExceptionIsExpectedType(f12, ExceptionTask.FaultHappened.class);
        checkExceptionIsExpectedType(f5, ExceptionTask.FaultHappened.class);

        if (spawning) { // Else the order is implementation not time dependent
            checkExceptionIsExpectedType(f4, TaskNotStartedException.class);
            checkExceptionIsExpectedType(f6, TaskNotStartedException.class);
            checkExceptionIsExpectedType(f7, TaskNotStartedException.class);
            checkExceptionIsExpectedType(f9, TaskNotStartedException.class);
        }

        assertTrue("f8", f8.get());
        assertFalse("f10", f10.get());
        assertFalse("f11", f10.get());

        assertEquals(1, (int) f1.get());
        assertEquals(2, (int) f2.get());
        assertEquals(3, (int) f3.get());

        threadCheck(3, 3, 4, 7, 0);

        sleep(30);
    }

    @Test
    public void mainReadme() throws Exception {
        CompletableFuture<Integer> f1 = $.task(task().delayFor(0)).name("f1").ret(1);
        CompletableFuture<Integer> f2 = $.task(task().delayFor(10)).name("f2").ret(2);
        CompletableFuture<Integer> f3 = $.task(task().delayFor(20)).name("f3").ret(3);

        $.task(task(0)).name("f4").inc(f1);
        CompletableFuture<Integer> f5 = $.task(task()).name("f5").add(f1, f2);
        CompletableFuture<Integer> f6 = $.task(task().delayFor(0)).name("f6").add(f2, f3);

        CompletableFuture<Integer> f7 = $.task(task()).name("f7").inc(f5);
        CompletableFuture<Integer> f8 = $.task(task()).name("f8").add(f5, f6);
        CompletableFuture<Integer> f9 = $.task(task()).name("f9").inc(f6);
        CompletableFuture<Integer> f10 = $.task(task()).name("f10").inc(f6);

        $.task(task(0)).name("f11").inc(f7);
        CompletableFuture<Integer> f12 = $.task(task().delayFor(0)).name("f12").add(f7, f8, f9, f10);

        assertEquals(24, (int) f12.get());  // Execute first so all threads activated for tests below

        assertEquals(1, (int) f1.get());
        assertEquals(2, (int) f2.get());
        assertEquals(3, (int) f3.get());

        assertEquals(3, (int) f5.get());
        assertEquals(5, (int) f6.get());

        assertEquals(4, (int) f7.get());
        assertEquals(8, (int) f8.get());
        assertEquals(6, (int) f9.get());
        assertEquals(6, (int) f10.get());

        threadCheck(4, 4, 5, 10, 0);

        sleep(30);
    }
}
