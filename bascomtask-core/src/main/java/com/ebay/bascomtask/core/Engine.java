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

import com.ebay.bascomtask.exceptions.InvalidTaskException;
import com.ebay.bascomtask.exceptions.InvalidTaskMethodException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.stream.Collectors;

/**
 * Core implementation of orchestrator maintains task state during execution.
 *
 * @author Brendan McCarthy
 */
public class Engine implements Orchestrator {
    private static final Logger LOG = LoggerFactory.getLogger(Engine.class);

    // Optionally supplied on create
    private String name;

    private long timeoutMs = 0;
    private TimeoutStrategy timeoutStrategy;
    private ExecutorService executorService;
    private SpawnMode spawnMode;

    private final LinkedList<TaskRunner> runners = new LinkedList<>();
    private final List<TaskRunner> exposeRunners = Collections.unmodifiableList(runners);

    // For generating unique thread names for framework-managed threads
    private static final AtomicInteger engineCounter = new AtomicInteger(0);
    private final int uniqueIndex;
    private final AtomicInteger threadCounter = new AtomicInteger(0);

    // BT-managed threads are flagged for bookkeeping purposes
    // TODO still needed?
    private final ThreadLocal<Boolean> isBtManagedThread = ThreadLocal.withInitial(() -> false);

    // For passing work (i.e. running tasks) back to the main thread
    private final BlockingQueue<BlockingQueue<CrossThreadChannel>> idleThreads = new LinkedBlockingDeque<>();

    static class CrossThreadChannel {
        final Thread parentThread;
        final Runnable runnable;

        CrossThreadChannel(Thread parentThread, Runnable runnable) {
            this.parentThread = parentThread;
            this.runnable = runnable;
        }
    }

    Engine(String name, Object arg) {
        this.name = name;
        this.uniqueIndex = engineCounter.incrementAndGet();
        GlobalOrchestratorConfig.Config config = GlobalOrchestratorConfig.getConfig();
        config.updateConfigurationOn(this, arg);

        LaneRunner.apply(this);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    String createThreadName() {
        StringBuilder sb = new StringBuilder();
        sb.append("BT-");
        sb.append(uniqueIndex);
        sb.append('-');
        if (name != null) {
            sb.append(name);
            sb.append('-');
        }
        sb.append(threadCounter.incrementAndGet());
        return sb.toString();
    }

    @Override
    public int getCountOfThreadsSpawned() {
        return threadCounter.get();
    }

    @Override
    public void restoreConfigurationDefaults(Object arg) {
        GlobalOrchestratorConfig.getConfig().updateConfigurationOn(this, arg);
    }

    @Override
    public SpawnMode getSpawnMode() {
        return spawnMode;
    }

    @Override
    public void setSpawnMode(SpawnMode mode) {
        this.spawnMode = mode == null ? SpawnMode.WHEN_NEEDED : mode;
    }

    @Override
    public long getTimeoutMs() {
        return timeoutMs;
    }

    @Override
    public void setTimeoutMs(long ms) {
        this.timeoutMs = ms;
    }

    @Override
    public TimeoutStrategy getTimeoutStrategy() {
        return timeoutStrategy;
    }

    @Override
    public void setTimeoutStrategy(TimeoutStrategy strategy) {
        this.timeoutStrategy = strategy;
    }

    @Override
    public ExecutorService getExecutorService() {
        return executorService;
    }

    @Override
    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public void restoreDefaultExecutorService() {
        this.executorService = GlobalOrchestratorConfig.getConfig().getExecutorService();
    }

    boolean isMainThread() {
        return !isBtManagedThread.get();
    }

    void executeAndReuseUntilReady(CompletableFuture<?> cf) {
        executeAndReuseUntilReady(cf, timeoutMs);
    }

    void executeAndReuseUntilReady(CompletableFuture<?> cf, long timeout, TimeUnit timeUnit) {
        timeout = timeUnit.toMillis(timeout);
        executeAndReuseUntilReady(cf, timeout);
    }

    void executeAndReuseUntilReady(CompletableFuture<?> cf, long timeoutMs) {
        execute(timeoutMs, cf);
        waitUntilComplete(timeoutMs, cf);
    }

    /**
     * Called before returning a CompletableFuture value to user code, in {@link BascomTaskFuture#get()} for example.
     * If that CompletableFuture is !isDone() we make the calling thread available for work (i.e. running spawned
     * tasks) in the meantime since that thread would block on the the read call while we otherwise would have to
     * pull a new task from the thread pool.
     *
     * @param timeoutMs here prevents main-thread reuse if is not zero (meaning no timeout is in effect)
     * @param cf        to start are watch for completion
     */
    private void waitUntilComplete(long timeoutMs, CompletableFuture<?> cf) {
        if (getSpawnMode().isMainThreadReusable() && timeoutMs == 0) {
            if (!cf.isDone()) {  // Redundant with later checks, but done here to avoid bookkeeping overhead for common cases
                BlockingQueue<CrossThreadChannel> waiting = new LinkedBlockingDeque<>(1);
                cf.whenComplete((msg, ex) -> {
                    // Prevents run() method from taking it (it's ok if it's already taken it)
                    if (!idleThreads.remove(waiting)) {
                        // This is ok, just log for information purposes
                        LOG.debug("Main thread preempted remove on {}", cf);
                    }
                    // Complete a waiting thread if there is one and run() method hasn't already processed it
                    if (!waiting.offer(new CrossThreadChannel(null, null))) {
                        // This is ok, just log for information purposes
                        LOG.debug("Main thread preempted offer on {}", cf);
                    }
                });

                while (!cf.isDone()) {
                    if (idleThreads.offer(waiting)) {  // Publish the availability of this thread
                        CrossThreadChannel channel;
                        try {
                            LOG.debug("Main thread waiting on {}", cf);
                            channel = waiting.take();  // Waits for either a work task (runnable) or a termination marker
                        } catch (InterruptedException e) {
                            break;
                        }
                        if (channel.runnable == null) {
                            break;
                        } else {
                            LOG.debug("Main thread reused from parent thread \"{}\" on {}", channel.parentThread.getName(), cf);
                            channel.runnable.run();
                        }
                    }
                }
            }
        }
    }

    void run(Runnable runnable, Thread parentThread, TimeBox timeBox) {
        BlockingQueue<CrossThreadChannel> waiting = idleThreads.poll();
        if (waiting != null) { // Check for a waiting thread first
            CrossThreadChannel channel = new CrossThreadChannel(parentThread, runnable);
            if (waiting.offer(channel)) {
                return; // If we were able to offer to 1-sized queue, it will be picked up by main thread
            }
        }
        // Else get one from the pool
        executorService.execute(() ->
        {
            String nm = createThreadName();
            Thread.currentThread().setName(nm);
            LOG.debug("Spawned thread \"{}\" --> \"{}\"", parentThread.getName(), nm);
            isBtManagedThread.set(true);
            timeBox.register(this);
            try {
                runnable.run();
            } finally {
                timeBox.deregister();
                isBtManagedThread.set(false);
            }
        });
    }

    @Override
    public void execute(long timeoutMs, CompletionStage<?>... futures) {
        TimeBox timeBox = timeoutMs <= 0 ? TimeBox.NO_TIMEOUT : new TimeBox(timeoutMs);
        executeWithMonitoringIfNeeded(timeBox, futures);
    }

    private void executeWithMonitoringIfNeeded(TimeBox timeBox, CompletionStage<?>... futures) {
        executeWithMonitoringIfNeeded(timeBox,true,futures);
    }

    /**
     * Initiates execution, and if the TimeoutStrategy calls for it, and ensures timeBox is
     * properly setup for monitoring.
     *
     * @param timeBox governs timeouts
     * @param direct indicates that the call should be made in current thread, else in a newly spawned thread
     * @param futures to execute
     */
    private void executeWithMonitoringIfNeeded(TimeBox timeBox, boolean direct, CompletionStage<?>... futures) {
        Binding<?> pending = null;
        for (CompletionStage<?> next : futures) {
            if (next instanceof BascomTaskFuture) {
                BascomTaskFuture<?> bascomTaskFuture = (BascomTaskFuture<?>) next;
                pending = bascomTaskFuture.getBinding().activate(pending, timeBox);
            }
        }
        timeBox.monitorIfNeeded(this);
        timeBox.register(this);
        try {
            if (pending != null) {
                String src = timeBox.timeBudget > 0 ? "timed" : "untimed";
                pending.fire("execute", src, direct);
            }
        } finally {
            timeBox.deregister();
        }
    }

    @Override
    public void executeAndWait(long timeoutMs, CompletableFuture<?>... futures) {
        TimeBox timeBox = timeoutMs <= 0 ? TimeBox.NO_TIMEOUT : new TimeBox(timeoutMs);
        executeWithMonitoringIfNeeded(timeBox, futures);
        for (CompletableFuture<?> next : futures) {
            try {
                next.get(timeBox.timeBudget, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                // do nothing since our only purpose here is to wait; subsequent external calls
                // to get() will deal with the exception
            }
        }
    }

    @Override
    public <T> List<T> executeAndWait(long timeoutMs, List<CompletableFuture<T>> futures) {
        CompletableFuture<?>[] array = new CompletableFuture[futures.size()];
        futures.toArray(array);
        executeAndWait(timeoutMs,array);
        try {
            return futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
        } catch (CompletionException e) {
            // Strip away the CompletionException since that is just an internal artifact from
            // the join above
            Throwable t = e.getCause();
            if (t instanceof RuntimeException) {
                throw (RuntimeException)t;
            } else {
                throw new RuntimeException("Execution exception",t);
            }
        }
    }

    @Override
    public <T> CompletableFuture<List<T>> executeFuture(long timeoutMs, List<CompletableFuture<T>> futures) {
        TimeBox timeBox = new TimeBox(timeoutMs);
        CompletableFuture<?>[] array = new CompletableFuture[futures.size()];
        futures.toArray(array);
        executeWithMonitoringIfNeeded(timeBox, false, array);
        CompletableFuture<List<T>> cfs = new CompletableFuture<>();
        CompletableFuture.allOf(array)
                .thenAccept(v->cfs.complete(futures.stream().map(CompletableFuture::join).collect(Collectors.toList())))
                .exceptionally(t->{cfs.completeExceptionally(t); return null;});
        return cfs;
    }

    @Override
    public <T> void executeAsReady(long timeoutMs, List<CompletableFuture<T>> futures, TriConsumer<T,Throwable,Integer> completionFn) {
        TimeBox timeBox = new TimeBox(timeoutMs);
        CompletableFuture<?>[] array = new CompletableFuture[futures.size()];
        futures.toArray(array);
        AtomicInteger countDown = new AtomicInteger(futures.size());
        executeWithMonitoringIfNeeded(timeBox, false, array);
        // Invoked after above because the whenComplete() operation would otherwise itself active the
        // BascomTaskFutures without a BascomTask-managed timeout
        futures.forEach(cf->{
            synchronized (countDown) {
                cf.whenComplete((v,t)->completionFn.apply(v,t,countDown.decrementAndGet()));
            }
        });
    }

    @Override
    public TaskMeta getTaskMeta(CompletableFuture<?> cf) {
        if (cf instanceof BascomTaskFuture) {
            BascomTaskFuture<?> bascomTaskFuture = (BascomTaskFuture<?>) cf;
            return bascomTaskFuture.getBinding();
        }
        return null;
    }

    @Override
    public String toString() {
        return "Engine";
    }

    @Override
    public <BASE, SUB extends TaskInterface<BASE>> BASE task(SUB t) {

        if (t instanceof Proxy) {
            throw new InvalidTaskMethodException("Cannot add a previously added/wrapped task: " + t);
        }

        Class<BASE> tc = extractTaskInterface(t);

        @SuppressWarnings("unchecked")
        BASE base = (BASE) t;
        TaskWrapper<BASE> task = new TaskWrapper<>(this, base, t);

        @SuppressWarnings("unchecked")
        BASE proxy = (BASE) Proxy.newProxyInstance(tc.getClassLoader(),
                new Class[]{tc},
                task);

        return proxy;
    }

    @Override
    public <R> CompletableFuture<Optional<R>> cond(CompletableFuture<Boolean> condition,
                                        CompletableFuture<R> thenFuture, boolean thenActivate) {
        ConditionalTask<R> task = new ConditionalTask<>(this, condition, thenFuture, thenActivate);
        return task.getOutput().thenApply(r->r==null ? Optional.empty() : Optional.of(r));
    }


    @Override
    public <R> CompletableFuture<R> cond(CompletableFuture<Boolean> condition, CompletableFuture<R> thenFuture, boolean thenActivate, CompletableFuture<R> elseFuture, boolean elseActivate) {
        ConditionalTask<R> task = new ConditionalTask<>(this, condition, thenFuture, thenActivate, elseFuture, elseActivate);
        return task.getOutput();
    }

    @Override
    public CompletableFuture<Boolean> fate(CompletableFuture<?>... cfs) {
        FateTask task = new FateTask(this, cfs);
        return task.getOutput();
    }

    @Override
    public <R> SupplierTask<R> fnTask(Supplier<R> fn) {
        return new SupplierTask.SupplierTask0<>(this, fn);
    }

    @Override
    public <IN, R> SupplierTask<R> fnTask(Supplier<IN> s1, Function<IN, R> fn) {
        CompletableFuture<IN> in1 = fnTask(s1).apply();
        return new SupplierTask.SupplierTask1<>(this, in1,fn);
    }

    @Override
    public <T, R> SupplierTask<R> fnTask(CompletableFuture<T> input, Function<T, R> fn) {
        return new SupplierTask.SupplierTask1<>(this, input,fn);
    }

    @Override
    public <T,U,R> SupplierTask<R> fnTask(CompletableFuture<T> firstInput, CompletableFuture<U> secondInput, BiFunction<T,U,R> fn) {
        return new SupplierTask.SupplierTask2<>(this,firstInput,secondInput,fn);
    }



    /**
     * Extracts the interface BASE from TaskInterface<BASE> in the class hierarchy.
     *
     * @param task   to extract interface from
     * @param <BASE> Generic type of TaskInterface
     * @return extracted interface
     */
    <BASE> Class<BASE> extractTaskInterface(TaskInterface<BASE> task) {
        Class<BASE> clazz = extractTaskInterfaceFromClass(task.getClass());
        if (clazz == null) {
            // This should not happen because compiler restricts calls to those that
            // implement interface TaskInterface
            throw new InvalidTaskException("Task does not implement com.ebay.bascomtask.core.TaskInterface: " + task);
        } else return clazz;
    }

    @SuppressWarnings("unchecked")
    <BASE> Class<BASE> extractTaskInterfaceFromClass(Class<?> clazz) {
        Type[] types = clazz.getGenericInterfaces();
        for (Type nextType : types) {
            if (nextType instanceof ParameterizedType) {
                ParameterizedType type = (ParameterizedType) nextType;
                nextType = type.getRawType();
            }
            Class<BASE> classBase = (Class<BASE>) nextType;
            if (nextType.equals(TaskInterface.class)) {
                return classBase;
            }
            Class<BASE> xs = extractTaskInterfaceFromClass(classBase);
            if (xs != null) {
                return classBase;  // Return interface that extends TaskInterface
            }
        }

        Class<?> sc = clazz.getSuperclass();
        if (sc != null) {
            return extractTaskInterfaceFromClass(sc);
        }
        return null;
    }

    List<TaskRunner> getRunners() {
        return exposeRunners;
    }

    @Override
    public void firstInterceptWith(TaskRunner taskRunner) {
        synchronized (runners) {
            runners.addFirst(taskRunner);
        }
    }

    @Override
    public void lastInterceptWith(TaskRunner taskRunner) {
        synchronized (runners) {
            runners.addLast(taskRunner);
        }
    }

    @Override
    public int getNumberOfInterceptors() {
        return runners.size();
    }

    @Override
    public void removeInterceptor(TaskRunner taskRunner) {
        synchronized (runners) {
            runners.remove(taskRunner);
        }
    }

    @Override
    public void removeAllTaskRunners() {
        synchronized (runners) {
            runners.clear();
        }
    }
}
