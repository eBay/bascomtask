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

    // Set to true when this binding's task is scheduled for execution, either immediately oe once its
    // arguments are ready; only ever set from false to true, and that only happens once
    private final AtomicBoolean activated = new AtomicBoolean(false);

    // Subset of args that are BascomTaskFutures
    private final List<BascomTaskFuture<?>> inputs = new ArrayList<>();

    // Only ever gets reset from false to true, not a thread-safety issue as it doesn't matter if it
    // it set to true multiple times
    private boolean started = false;

    // The output for this task method invocation
    private final BascomTaskFuture<RETURNTYPE> output = new BascomTaskFuture<>(this);

    // Number of inputs that are completed and ready; when these reaches the threshold, all arguments are
    // available and the task method is ready to fire (execute)
    private final AtomicInteger readyCount = new AtomicInteger();

    // Cached because logging/profiling can call repeatedly
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
        return getClass().getSimpleName() + "(" + getTaskPlusMethodName() + ")";
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

    Binding<?> doActivate(Binding<?> pending) {
        if (inputs.size() == 0) {
            pending = runAccordingToMode(pending, "activate");
        } else {
            for (BascomTaskFuture<?> next : inputs) {
                pending = next.activate(this, pending);
                if (next.isCompletedExceptionally()) {
                    // Once an exception is found, propagate it to our output
                    propagateMostUsefulFault();
                    break;
                }
            }
        }
        return pending;
    }


    /**
     * Given that it is know that at least one input generates an exception, propagate that exception to
     * our output, or a better exception if there more than one of our inputs has an exception.
     */
    private void propagateMostUsefulFault() {
        Exception fx = null;
        for (BascomTaskFuture<?> next : inputs) {
            if (next.isCompletedExceptionally()) {
                try {
                    next.get();  // Only way to get the pending exception is to try and access it
                } catch (Exception e) {
                    if (fx == null || !(e instanceof TaskNotStartedException)) {
                        fx = e;
                    }
                }
            }
        }
        if (fx == null) {
            // Shouldn't happen because this method should only be called when it is known that there is an exception
            throw new RuntimeException("Unexpected fx not null");
        } else {
            getOutput().completeExceptionally(fx);
        }
    }

    Binding<?> runAccordingToMode(Binding<?> pending, String src) {
        SpawnMode mode = engine.getSpawnMode();
        if (isLight()) {
            fire(src, "light", true);
        } else if (mode == SpawnMode.NEVER_MAIN && engine.isMainThread() && !isLight()) {
            fire(src, "neverMain", false);
        } else if (mode == SpawnMode.ALWAYS_SPAWN) {
            fire(src, "alwaysSpawn", false);
        } else if (mode == SpawnMode.NEVER_SPAWN) {
            fire(src, "neverSpawn", true);
        } else if (isRunSpawned()) {
            fire(src, "runSpawned", false);
        } else if (mode == SpawnMode.DONT_SPAWN_UNLESS_EXPLICIT) {
            fire(src, "explicit", true);
        } else { // May or may not spawn
            if (pending != null) {
                pending.fire(src, "conflict", false);
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
            pending.fire("onCompletion", "direct", true);
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
        // Do nothing by default
    }

    void fire(String src1, String src2, boolean direct) {
        if (activated.get()) {  // Only activated tasks are executed
            if (!output.isCompletedExceptionally()) {
                //if (engine.areThereAnyExceptions()) {  // Don't fire if any exceptions have happened
                //output.faultForward(new TaskNotStartedException("Fault detected"));
                //output.completeExceptionally(new TaskNotStartedException("Fault detected"));
                //} else {
                started = true;
                final Thread parentThread = Thread.currentThread();
                List<TaskRunner> localRunners = this.engine.getRunners();
                int sz = localRunners.size();
                if (sz == 0) {
                    chooseThreadAndFire(this, this, parentThread, null, src1, src2, direct);
                } else {
                    fireTaskThruRunners(localRunners, sz-1, parentThread, this, src1, src2, direct);
                }
            }
        }
    }

    private void fireTaskThruRunners(List<TaskRunner> runners, int index, Thread parentThread, TaskRun under, String src1, String src2, boolean direct) {
        TaskRunner next = runners.get(index);
        if (index==0) {
            Object fromBefore = next.before(under);
            chooseThreadAndFire(next, under, parentThread, fromBefore, src1, src2, direct);
        } else {
            PlaceHolderRunner phr = new PlaceHolderRunner(next, parentThread, under);
            fireTaskThruRunners(runners, index-1,parentThread, phr, src1, src2, direct);

        }
    }

    private void chooseThreadAndFire(TaskRunner taskRunner, TaskRun taskRun, Thread parentThread, Object fromBefore, String src1, String src2, boolean direct) {
        if (direct) {
            fireFirstRunner(taskRunner, taskRun, parentThread, fromBefore, src1, src2);
        } else {
            Runnable runnable = () -> fireFirstRunner(taskRunner, taskRun, parentThread, fromBefore, src1, src2);
            engine.run(runnable, parentThread);
        }
    }

    private void fireFirstRunner(TaskRunner taskRunner, TaskRun taskRun, Thread parentThread, Object fromBefore, String src1, String src2) {
        final String name = getName();
        startedAt = System.currentTimeMillis(); // Set here so runners can access it
        LOG.debug("Firing {} from {}-{}", name, src1, src2);
        try {
            Object rv = taskRunner.executeTaskMethod(taskRun, parentThread, fromBefore);
            if (rv instanceof CompletableFuture) {
                @SuppressWarnings("unchecked")
                CompletableFuture<RETURNTYPE> cf = (CompletableFuture<RETURNTYPE>) rv;
                // Allow taskRunners to complete before we continue processing other tasks here
                completeRunner(taskRunner, taskRun, fromBefore, cf);
                LOG.debug("Exiting {} from {}-{}", name, src1, src2);
                output.bind(cf);
            } else {
                throw new RuntimeException("Return value is not a CompletableFuture " + this);
            }
        } catch (Exception e) {
            LOG.debug("Exception-exit {} from {}-{}", name, src1, src2);
            faultForward(e);
        }
    }

    final void faultForward(Throwable t) {
        List<FateTask> fates = new ArrayList<>();

        // First propagate the exception to all direct & indirect descendents, excluding FateTasks
        // which we collect in a list for later
        faultForward(t, fates);

        // Next propagate TaskNotStartedException to all reachable task nodes reachable from all fate inputs
        // and not yet started
        TaskNotStartedException tns = new TaskNotStartedException(t);
        for (int i = 0; i < fates.size(); i++) {
            FateTask next = fates.get(i);
            next.cancelInputs(tns, fates);
        }

        // Finally, executed FateTasks after having already propagated exceptions to every reachable place;
        // now any executions emanating from any FateTasks will stop at any already faulted node
        Binding<?> pending = null;
        for (FateTask next : fates) {
            pending = next.runAccordingToMode(pending, "faultForward");
        }
        if (pending != null) {
            pending.fire("faultForward", "fate", true);
        }
    }

    /**
     * Cancels all direct or indirect ancestor tasks that feed into our inputs, and propagate that
     * cancellation downward to every reachable task from those cancelled tasks. Cancellation means
     * setting the output CompletableFuture to TaskNotStartedException, which should only be set
     * of course on tasks that have not in fact started.
     *
     * @param tns   to apply
     * @param fates to collect
     */
    void cancelInputs(TaskNotStartedException tns, List<FateTask> fates) {
        for (BascomTaskFuture<?> next : inputs) {
            Binding<?> nextBinding = next.getBinding();
            if (!nextBinding.started // avoid rewriting earlier exception
                    && next.completeExceptionally(tns)) {  // Able to reset output to exception?
                LOG.debug("Task cancelled: {}", nextBinding);
                nextBinding.cancelInputs(tns, fates); // backward
                next.propagateException(tns, fates); // forward
            }
        }
    }

    /**
     * Subclasses can override to alter normal propagation behavior.
     *
     * @param t     being thrown
     * @param fates list of FateTasks to collect
     */
    void faultForward(Throwable t, List<FateTask> fates) {
        if (output.completeExceptionally(t)) {
            LOG.debug("Faulting forward {} with {}", this, t.getMessage());
            output.propagateException(t, fates);
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
