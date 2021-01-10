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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

/**
 * A specialization of CompletableFuture that tracks downstream Bindings for task methods that take this object
 * as an input, and also that ensures activation for standard CompletableFuture access operations (with the exception
 * of Compose operations -- see below). A call to {@link Orchestrator#task(TaskInterface)} returns an instance of this
 * class. When the user task returns its CompleteFuture result, that is bound to this class so that when the user CF
 * is completed, the BascomTask thread-optimization invocation logic can be applied. If the Bindings simply listened
 * to the CF directly then we'd have to spawn a thread for each one always.
 * <pre>
 *
 *     CF returned by user task or supplied externally
 *                       |
 *                 BascomTaskFuture
 *                    /    \
 *                   /      \
 *              Binding1   Binding2
 *                  |        |
 *             userTask1   userTask2
 * </pre>
 *
 * @author Brendan McCarthy
 */
class BascomTaskFuture<T> extends CompletableFuture<T> {

    // The Binding for which this object holds the output
    private final Binding<T> binding;

    // Holds activated bindings that are waiting on this CF (actually the CF target of this CF) to be completed,
    // set to null once that completion happens
    private List<Binding<?>> listenerBindings = new ArrayList<>();

    // Serves to avoid thread conflicts when adding to and readingFrom listenerBindings
    private final Object listenerLock = new Object();

    BascomTaskFuture(Binding<T> binding) {
        this.binding = binding;
    }

    @Override
    public String toString() {
        return "BascomTaskFuture[" + getBinding() + "]==>" + super.toString();
    }

    Binding<T> getBinding() {
        return binding;
    }

    /**
     * Registers this object as a listener on the provided CF which might or might not be another BascomTaskFuture.
     *
     * @param cf to register on
     */
    void bind(CompletableFuture<T> cf) {
        cf.thenAccept(this::finish).whenComplete((msg, ex) -> {
            if (ex != null) {
                Throwable cause = ex.getCause(); // Unwrap from java.util.concurrent.CompletionException
                binding.faultForward(cause);
            }
        });
    }

    void propagateException(Throwable t, List<FateTask> fates) {
        for (Binding<?> nextBinding : this.listenerBindings) {
            BascomTaskFuture<?> bascomTaskFuture = nextBinding.getOutput();
            bascomTaskFuture.binding.faultForward(t, fates);
        }
    }

    private void finish(T t) {
        complete(t);
        List<Binding<?>> lbs;
        synchronized (listenerLock) {
            lbs = listenerBindings;
            // Setting to null is an indication that should any other Binding be activated that depends
            // on us, they should process our already-complete result directly
            listenerBindings = null;
        }
        binding.onCompletion(lbs);
    }

    Binding<?> activate(Binding<?> becomingActivated, Binding<?> pending, TimeBox timeBox) {
        boolean complete = true;
        if (listenerBindings != null) {
            // If this CF has already completed, listenerBindings will be null
            synchronized (listenerLock) {
                if (listenerBindings != null) {
                    // Set listeners before activating, in case execution occurs
                    complete = false;
                    listenerBindings.add(becomingActivated);
                }
            }
        }

        pending = binding.activate(pending, timeBox);
        if (complete) {
            // Only propagate forward if not done already
            pending = becomingActivated.argReady(pending);
        }
        return pending;
    }

    private static RuntimeException rethrow(ExecutionException e) {
        Throwable t = e.getCause();
        if (t instanceof RuntimeException) {
            return (RuntimeException) t;
        }
        return new RuntimeException(t);
    }


    //////////////////////////////////////////////////////////////////////
    // CompletableFuture overrides, each calls super after activating this
    //////////////////////////////////////////////////////////////////////

    @Override
    public T get() throws InterruptedException {
        binding.engine.executeAndReuseUntilReady(this);
        try {
            return super.get();
        } catch (ExecutionException e) {
            throw rethrow(e);
        }
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException {
        binding.engine.executeAndReuseUntilReady(this, timeout, unit);
        try {
            return super.get();
        } catch (ExecutionException e) {
            throw rethrow(e);
        }
    }

    // Additional methods added in Java 9 for timeouts, (orTimeout and completeOnTimeout) are not
    // handled here in order to keep Java 8 compatibility at this point. Use of those methods would
    // not be able to leverage BascomTask's timeout/interrupt handling, but is more of an optimization
    // issue that should not otherwise affect functionality.

    @Override
    public T join() {
        binding.engine.executeAndReuseUntilReady(this);
        return super.join();
    }

    @Override
    public T getNow(T valueIfAbsent) {
        binding.engine.executeAndReuseUntilReady(this);
        return super.getNow(valueIfAbsent);
    }

    @Override
    public <U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
        binding.engine.execute(this);
        return super.thenApply(fn);
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        binding.engine.execute(this);
        return super.thenApplyAsync(fn);
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        binding.engine.execute(this);
        return super.thenApplyAsync(fn, executor);
    }

    @Override
    public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        binding.engine.execute(this);
        return super.thenAccept(action);
    }

    @Override
    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action) {
        binding.engine.execute(this);
        return super.thenAcceptAsync(action);
    }

    @Override
    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        binding.engine.execute(this);
        return super.thenAcceptAsync(action, executor);
    }

    @Override
    public CompletableFuture<Void> thenRun(Runnable action) {
        binding.engine.execute(this);
        return super.thenRun(action);
    }

    @Override
    public CompletableFuture<Void> thenRunAsync(Runnable action) {
        binding.engine.execute(this);
        return super.thenRunAsync(action);
    }

    @Override
    public CompletableFuture<Void> thenRunAsync(Runnable action, Executor executor) {
        binding.engine.execute(this);
        return super.thenRunAsync(action, executor);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombine(
            CompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn) {
        binding.engine.execute(other, this);
        return super.thenCombine(other, fn);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(
            CompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn) {
        binding.engine.execute(other, this);
        return super.thenCombineAsync(other, fn);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(
            CompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        binding.engine.execute(other, this);
        return super.thenCombineAsync(other, fn, executor);
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBoth(
            CompletionStage<? extends U> other,
            BiConsumer<? super T, ? super U> action) {
        binding.engine.execute(other, this);
        return super.thenAcceptBoth(other, action);
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBothAsync(
            CompletionStage<? extends U> other,
            BiConsumer<? super T, ? super U> action) {
        binding.engine.execute(other, this);
        return super.thenAcceptBothAsync(other, action);
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBothAsync(
            CompletionStage<? extends U> other,
            BiConsumer<? super T, ? super U> action, Executor executor) {
        binding.engine.execute(other, this);
        return super.thenAcceptBothAsync(other, action, executor);
    }

    @Override
    public CompletableFuture<Void> runAfterBoth(CompletionStage<?> other,
                                                Runnable action) {
        binding.engine.execute(other, this);
        return super.runAfterBoth(other, action);
    }

    @Override
    public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other,
                                                     Runnable action) {
        binding.engine.execute(other, this);
        return super.runAfterBothAsync(other, action);
    }

    @Override
    public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other,
                                                     Runnable action,
                                                     Executor executor) {
        binding.engine.execute(other, this);
        return super.runAfterBothAsync(other, action, executor);
    }

    @Override
    public <U> CompletableFuture<U> applyToEither(
            CompletionStage<? extends T> other, Function<? super T, U> fn) {
        binding.engine.execute(other, this);
        return super.applyToEither(other, fn);
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(
            CompletionStage<? extends T> other, Function<? super T, U> fn) {
        binding.engine.execute(other, this);
        return super.applyToEitherAsync(other, fn);
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(
            CompletionStage<? extends T> other, Function<? super T, U> fn,
            Executor executor) {
        binding.engine.execute(other, this);
        return super.applyToEitherAsync(other, fn, executor);
    }

    @Override
    public CompletableFuture<Void> acceptEither(
            CompletionStage<? extends T> other, Consumer<? super T> action) {
        binding.engine.execute(other, this);
        return super.acceptEither(other, action);
    }

    @Override
    public CompletableFuture<Void> acceptEitherAsync(
            CompletionStage<? extends T> other, Consumer<? super T> action) {
        binding.engine.execute(other, this);
        return super.acceptEitherAsync(other, action);
    }

    @Override
    public CompletableFuture<Void> acceptEitherAsync(
            CompletionStage<? extends T> other, Consumer<? super T> action,
            Executor executor) {
        binding.engine.execute(other, this);
        return super.acceptEitherAsync(other, action, executor);
    }

    @Override
    public CompletableFuture<Void> runAfterEither(CompletionStage<?> other,
                                                  Runnable action) {
        binding.engine.execute(other, this);
        return super.runAfterEither(other, action);
    }

    @Override
    public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other,
                                                       Runnable action) {
        binding.engine.execute(other, this);
        return super.runAfterEitherAsync(other, action);
    }

    @Override
    public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other,
                                                       Runnable action,
                                                       Executor executor) {
        binding.engine.execute(other, this);
        return super.runAfterEitherAsync(other, action, executor);
    }

    /**
     * The execute() logic can't be fully applied on compose operations since the incoming parameter
     * is a function that hides in CF result until the function is applied. Such a CF must be started
     * separately with another call such as {@link #get()} or {@link Orchestrator#execute(CompletionStage[])}.
     *
     * @param fn  to apply
     * @param <U> of return
     * @return CompletableFuture
     */
    @Override
    public <U> CompletableFuture<U> thenCompose(
            Function<? super T, ? extends CompletionStage<U>> fn) {
        binding.engine.execute(this);
        return super.thenCompose(fn);
    }

    @Override
    public <U> CompletableFuture<U> thenComposeAsync(
            Function<? super T, ? extends CompletionStage<U>> fn) {
        binding.engine.execute(this);
        return super.thenComposeAsync(fn);
    }

    @Override
    public <U> CompletableFuture<U> thenComposeAsync(
            Function<? super T, ? extends CompletionStage<U>> fn,
            Executor executor) {
        binding.engine.execute(this);
        return super.thenComposeAsync(fn, executor);
    }

    @Override
    public CompletableFuture<T> whenComplete(
            BiConsumer<? super T, ? super Throwable> action) {
        binding.engine.execute(this);
        return super.whenComplete(action);
    }

    @Override
    public CompletableFuture<T> whenCompleteAsync(
            BiConsumer<? super T, ? super Throwable> action) {
        binding.engine.execute(this);
        return super.whenCompleteAsync(action);
    }

    @Override
    public CompletableFuture<T> whenCompleteAsync(
            BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        binding.engine.execute(this);
        return super.whenCompleteAsync(action, executor);
    }

    @Override
    public <U> CompletableFuture<U> handle(
            BiFunction<? super T, Throwable, ? extends U> fn) {
        binding.engine.execute(this);
        return super.handle(fn);
    }

    @Override
    public <U> CompletableFuture<U> handleAsync(
            BiFunction<? super T, Throwable, ? extends U> fn) {
        binding.engine.execute(this);
        return super.handleAsync(fn);
    }

    @Override
    public <U> CompletableFuture<U> handleAsync(
            BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        binding.engine.execute(this);
        return super.handleAsync(fn, executor);
    }

    @Override
    public CompletableFuture<T> exceptionally(
            Function<Throwable, ? extends T> fn) {
        binding.engine.execute(this);
        return super.exceptionally(fn);
    }
}
