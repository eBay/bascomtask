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

import java.util.concurrent.CompletableFuture;

/**
 * If-then-else or if-then (if elseFuture is null), optimized for thread execution. The task also serves as its
 * own {@link Binding} to reduce object creation for this built-in case.
 *
 * @author Brendan McCarthy
 */
class TernaryConditionalTask<R> extends ConditionalTask<R> implements TaskInterface<TernaryConditionalTask<R>> {
    final BascomTaskFuture<R> thenFuture;
    final BascomTaskFuture<R> elseFuture;
    final boolean thenActivate;
    final boolean elseActivate;

    TernaryConditionalTask(Engine engine,
                           CompletableFuture<Boolean> condition,
                           CompletableFuture<R> thenValue, boolean thenActivate,
                           CompletableFuture<R> elseValue, boolean elseActivate) {
        super(engine,condition);
        this.thenFuture = ensureWrapped(thenValue, false);
        this.elseFuture = ensureWrapped(elseValue, false);
        this.thenActivate = thenActivate;
        this.elseActivate = elseActivate;
    }

    @Override
    public TaskInterface<?> getTask() {
        return this;
    }

    /**
     * Always activate the condition, and also active then/else futures if requested.
     *
     * @param pending to be processed
     * @return pending
     */
    @Override
    Binding<?> doActivate(Binding<?> pending, TimeBox timeBox) {
        pending = condition.activate(this, pending, timeBox);
        pending = activateIf(pending, thenFuture, thenActivate, timeBox);
        pending = activateIf(pending, elseFuture, elseActivate, timeBox);
        return pending;
    }

    @Override
    protected Object invokeTaskMethod() {
        if (get(condition)) {
            ensureActivated(thenFuture);
            return thenFuture;
        } else {
            ensureActivated(elseFuture);
            return elseFuture;
        }
    }
}
