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
}
