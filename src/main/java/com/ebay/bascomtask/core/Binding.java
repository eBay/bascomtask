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

import com.ebay.bascomtask.exceptions.TaskNotStartedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runtime bookkeeping for a method invocation on a user task.
 *
 * @author Brendan McCarthy
 */
abstract class Binding<RETURNTYPE> implements TaskRunner, TaskRun {
    private static final Logger LOG = LoggerFactory.getLogger(Binding.class);

    final Engine engine;
    // Subset of args that are BascomTaskFutures
    protected final List<BascomTaskFuture<?>> inputs = new ArrayList<>();
    private final BascomTaskFuture<RETURNTYPE> output = new BascomTaskFuture<>(this);

    private final AtomicBoolean activated = new AtomicBoolean(false);
    private final AtomicInteger readyCount = new AtomicInteger();

    private String cachedTaskPlusName = null;

    private long startedAt;
    private long endedAt;
    private long completedAt;

    /**
     * Invoked for a CF that is not BT-managed.
     *
     * @param engine being run
     * @param cf     return value
     */
    Binding(Engine engine, CompletableFuture<RETURNTYPE> cf) {
        this.engine = engine;
        this.activated.set(true);
        output.bind(cf);
    }

    /**
     * Invoked for a BT-managed input.
     *
     * @param engine being run
     */
    Binding(Engine engine) {
        this.engine = engine;
    }

    @Override
    public String toString() {
        return "Binding(" + getTaskPlusMethodName() + ")";
    }

    protected boolean isLight() {
        return false;
    }

    protected boolean isRunSpawned() {
        return false;
    }

    /**
     * Ensures the provided CF is or is wrapped by a BT-controlled CF, and registered as an input argument.
     *
     * @param cf to ensure
     * @return BT-controlled CF
     */
    protected <RV> BascomTaskFuture<RV> ensureWrapped(CompletableFuture<RV> cf, boolean registerAsDependentInput) {
        BascomTaskFuture<RV> bascomTaskFuture;
        if (cf instanceof BascomTaskFuture) {
            bascomTaskFuture = (BascomTaskFuture<RV>) cf;
        } else {
            Binding<RV> binding = new ExternalBinding<>(engine, cf);
            bascomTaskFuture = binding.getOutput();
        }
        if (registerAsDependentInput) {
            inputs.add(bascomTaskFuture);
        }
        return bascomTaskFuture;
    }

    /**
     * Ensure the task for this binding is executed (or returning it for execution) either by starting it
     * now or starting its predecessors -- unless (in each case) already started.
     *
     * @param pending possibly null Binding (task) that needs to be started
     * @return ready and needing to-be-executed Binding (task) that needs to be started
     */
    final Binding<?> activate(Binding<?> pending) {
        if (activated.compareAndSet(false, true)) {
            pending = doActivate(pending);
        }
        return pending;
    }

    abstract Binding<?> doActivate(Binding<?> pending);

    Binding<?> runAccordingToMode(Binding<?> pending, String src) {
        SpawnMode mode = engine.getEffectiveSpawnMode();
        if (isLight()) {
            fire(src + "-light", true);
        } else if (mode == SpawnMode.NEVER_MAIN && engine.isMainThread() && !isLight()) {
            fire(src + "-neverMain", false);
        } else if (mode == SpawnMode.ALWAYS_SPAWN) {
            fire(src + "-alwaysSpawn", false);
        } else if (mode == SpawnMode.NEVER_SPAWN) {
            fire(src + "-neverSpawn", true);
        } else if (isRunSpawned()) {
            fire(src + "-runSpawned", false);
        } else if (mode == SpawnMode.DONT_SPAWN_UNLESS_EXPLICIT) {
            fire(src + "-explicit", true);
        } else { // May or may not spawn
            if (pending != null) {
                pending.fire(src + "-conflict", false);
            }
            pending = this;
        }
        return pending;
    }

    final void onCompletion(List<Binding<?>> bindings) {
        completedAt = System.currentTimeMillis();
        Binding<?> pending = null;
        for (Binding<?> next : bindings) {
            pending = next.argReady(pending);
        }
        if (pending != null) {
            pending.fire("completion", true);
        }
    }

    /**
     * Called when a CF argument has completed and is ready -- should only be ONCE for each such arguments
     *
     * @param pending argument
     * @return binding ready to process or null if none
     */
    final Binding<?> argReady(Binding<?> pending) {
        if (readyCount.incrementAndGet() == inputs.size()) {
            pending = runAccordingToMode(pending, "completion");
            pending = onReady(pending);
        }
        return pending;
    }

    /**
     * Subclasses can optionally override to activate bindings.
     *
     * @param pending to process
     * @return pending
     */
    Binding<?> onReady(Binding<?> pending) {
        return pending;
    }

    BascomTaskFuture<RETURNTYPE> getOutput() {
        return output;
    }

    @Override
    public Object before(TaskRun taskRun) {
        return null;
    }

    @Override
    public Object executeTaskMethod(TaskRun taskRun, Thread parentThread, Object fromBefore) {
        return run();
    }

    @Override
    public void onComplete(TaskRun taskRun, Object fromBefore, boolean doneOnExit) {
        //System.out.println("Binding complete " + this);
    }

    void fire(String src, boolean direct) {
        if (activated.get()) {  // Only activated tasks are executed
            if (engine.areThereAnyExceptions()) {  // Don't fire if any exceptions have happened
                //output.faultForward(new TaskNotStartedException("Fault detected"));
                output.completeExceptionally(new TaskNotStartedException("Fault detected"));
            } else {
                final Thread parentThread = Thread.currentThread();
                List<TaskRunner> globalRunners = GlobalConfig.INSTANCE.globalRunners;
                List<TaskRunner> localRunners = this.engine.getRunners();
                int sz = globalRunners.size() + localRunners.size();
                if (sz == 0) {
                    chooseThreadAndFire(this, this, parentThread, null, src, direct);
                } else {
                    fireTaskThruRunners(localRunners, globalRunners, sz - 1, parentThread, this, direct);
                }
            }
        }
    }

    void fireTaskThruRunners(List<TaskRunner> runners1, List<TaskRunner> runners2, int combinedIndex, Thread parentThread, TaskRun under, boolean direct) {
        List<TaskRunner> rs = runners1;
        int localIndex = combinedIndex;
        if (combinedIndex >= runners1.size()) {
            rs = runners2;
            localIndex -= runners1.size();
        }
        TaskRunner next = rs.get(localIndex);
        if (combinedIndex == 0) {
            Object fromBefore = next.before(under);
            chooseThreadAndFire(next, under, parentThread, fromBefore, "TODO", direct);
        } else {
            PlaceHolderRunner phr = new PlaceHolderRunner(next, parentThread, under);
            fireTaskThruRunners(runners1, runners2, combinedIndex - 1, parentThread, phr, direct);
        }
    }

    void chooseThreadAndFire(TaskRunner taskRunner, TaskRun taskRun, Thread parentThread, Object fromBefore, String src, boolean direct) {
        if (direct) {
            fireFirstRunner(taskRunner, taskRun, parentThread, fromBefore, src);
        } else {
            Runnable runnable = () -> fireFirstRunner(taskRunner, taskRun, parentThread, fromBefore, src);
            engine.run(runnable, parentThread);
        }
    }

    void fireFirstRunner(TaskRunner taskRunner, TaskRun taskRun, Thread parentThread, Object fromBefore, String src) {
        final String name = getName();
        LOG.debug("Firing {} from {}", name, src);
        try {
            Object rv = taskRunner.executeTaskMethod(taskRun, parentThread, fromBefore);
            if (rv instanceof CompletableFuture) {
                @SuppressWarnings("unchecked")
                CompletableFuture<RETURNTYPE> cf = (CompletableFuture<RETURNTYPE>) rv;
                // Allow taskRunners to complete before we continue processing other tasks here
                completeRunner(taskRunner, taskRun, fromBefore, cf);
                LOG.debug("Exiting {} from {}", name, src);
                output.bind(cf);
            } else {
                throw new RuntimeException("Return value is not a CompletableFuture " + this);
            }
        } catch (Exception e) {
            engine.recordAnyException();
            LOG.debug("Faulting {} from {}", name, src);
            output.faultForward(e);
        }
    }

    static void completeRunner(TaskRunner taskRunner, TaskRun taskRun, Object fromBefore, Object rv) {
        if (rv instanceof CompletableFuture) {
            CompletableFuture<?> cf = (CompletableFuture<?>) rv;
            boolean isDone = cf.isDone();
            cf.thenAccept(v -> taskRunner.onComplete(taskRun, fromBefore, isDone));
        }
    }

    protected abstract Object invokeTaskMethod();

    @Override
    public final Object run() {
        startedAt = System.currentTimeMillis();
        try {
            return invokeTaskMethod();
        } finally {
            endedAt = System.currentTimeMillis();
        }
    }

    @Override
    public final String getName() {
        if (cachedTaskPlusName == null) {
            cachedTaskPlusName = doGetExecutionName();
        }
        return cachedTaskPlusName;
    }

    abstract String doGetExecutionName();

    @Override
    public final String getTaskPlusMethodName() {
        return getName();
    }

    @Override
    public long getStartedAt() {
        return startedAt;
    }

    @Override
    public long getEndedAt() {
        return endedAt;
    }

    @Override
    public long getCompletedAt() {
        return completedAt;
    }
}
