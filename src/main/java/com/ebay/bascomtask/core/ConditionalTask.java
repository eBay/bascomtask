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

import java.util.concurrent.CompletableFuture;

/**
 * If-then-else, optimized for thread execution. The task also serves as its own {@link Binding}.
 *
 * @author Brendan McCarthy
 */
class ConditionalTask<R> extends Binding<R> implements TaskInterface<ConditionalTask<R>> {
    private final BascomTaskFuture<Boolean> condition;
    final BascomTaskFuture<R> thenFuture;
    final BascomTaskFuture<R> elseFuture;
    final boolean thenActivate;
    final boolean elseActivate;

    ConditionalTask(Engine engine, CompletableFuture<Boolean> condition, CompletableFuture<R> thenValue, boolean thenActivate, CompletableFuture<R> elseValue, boolean elseActivate) {
        super(engine);
        this.condition = ensureWrapped(condition, true);
        this.thenFuture = ensureWrapped(thenValue, false);
        this.elseFuture = ensureWrapped(elseValue, false);
        this.thenActivate = thenActivate;
        this.elseActivate = elseActivate;
    }

    private Binding<?> activateIf(Binding<?> pending, BascomTaskFuture<R> bascomTaskFuture, boolean activate) {
        if (activate) {
            pending = bascomTaskFuture.activate(this, pending);
        }
        return pending;
    }

    /**
     * Always activate the condition, and also active then and/or else if requested.
     *
     * @param pending to be processed
     * @return pending
     */
    @Override
    Binding<?> doActivate(Binding<?> pending) {
        pending = condition.activate(this, pending);
        pending = activateIf(pending, thenFuture, thenActivate);
        pending = activateIf(pending, elseFuture, elseActivate);
        return pending;
    }

    /**
     * Ensures that the condition of choice is activated. This is called after the condition has completed.
     *
     * @param pending to process
     * @return pending
     */
    @Override
    Binding<?> onReady(Binding<?> pending) {
        Boolean which = get(condition);
        BascomTaskFuture<?> choice = which ? thenFuture : elseFuture;
        pending = choice.activate(this, pending);
        return super.onReady(pending);
    }


    @Override
    protected Object invokeTaskMethod() {
        if (get(condition)) {
            return thenFuture;
        } else {
            return elseFuture;
        }
    }

    @Override
    String doGetExecutionName() {
        return "<<CONDITIONAL>>";
    }

    @Override
    public TaskInterface<?> getTask() {
        return this;
    }

    @Override
    public void formatActualSignature(StringBuilder sb) {

    }
}