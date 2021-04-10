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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * If-then conditional logic.
 *
 * @author Brendan McCarthy
 */
class BinaryConditionalTask<R> extends ConditionalTask<Optional<R>> implements TaskInterface<BinaryConditionalTask<Optional<R>>> {
    final BascomTaskFuture<R> thenFuture;
    final BascomTaskFuture<Optional<R>> thenOptional;
    final boolean thenActivate;

    BinaryConditionalTask(Engine engine,
                          CompletableFuture<Boolean> condition,
                          CompletableFuture<R> thenValue, boolean thenActivate) {
        super(engine,condition);
        this.thenFuture = ensureWrapped(thenValue, false);
        this.thenOptional = new BascomTaskFuture<>(this);
        this.thenActivate = thenActivate;
    }

    @Override
    public TaskInterface<?> getTask() {
        return this;
    }

    /**
     * Always activate the condition, and also active thenFuture if requested.
     *
     * @param pending to be processed
     * @return pending
     */
    @Override
    Binding<?> doActivate(Binding<?> pending, TimeBox timeBox) {
        pending = condition.activate(this, pending, timeBox);
        pending = activateIf(pending, thenFuture, thenActivate, timeBox);
        return pending;
    }

    @Override
    protected Object invokeTaskMethod() {
        if (get(condition)) {
            ensureActivated(thenFuture);
            return thenFuture.thenApply(Optional::of);
        } else {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }
}
