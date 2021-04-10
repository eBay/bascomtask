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
 * Base class for conditional logic (if-then or if-then-else) optimized for thread execution. The task also
 * serves as its own {@link Binding} to reduce object creation for this built-in case.
 *
 * @author Brendan McCarthy
 */
abstract class ConditionalTask<R> extends Binding<R> {
    protected final BascomTaskFuture<Boolean> condition;

    ConditionalTask(Engine engine, CompletableFuture<Boolean> condition) {
        super(engine);
        this.condition = ensureWrapped(condition, true);
    }

    protected Binding<?> activateIf(Binding<?> pending, BascomTaskFuture<?> bascomTaskFuture, boolean activate, TimeBox timeBox) {
        if (activate && bascomTaskFuture != null) {
            // Activate it, but don't connect its completion yet -- that will happen once condition is resolved
            pending = bascomTaskFuture.getBinding().activate(pending, timeBox);
        }
        return pending;
    }

    /**
     * Ensure that the future is activated if it is not already.
     * @param bf to ensure to activate
     */
    protected void ensureActivated(BascomTaskFuture<?> bf) {
        engine.executeAndReuseUntilReady(bf);
    }

    @Override
    String doGetExecutionName() {
        return "<<CONDITIONAL>>";
    }

    @Override
    public void formatActualSignature(StringBuilder sb) {

    }
}
