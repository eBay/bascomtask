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
 * Binding for a CompletableFuture not managed by the framework, so there is no actual user task.
 *
 * @author Brendan McCarthy
 */
class ExternalBinding<RETURNTYPE> extends Binding<RETURNTYPE> {

    ExternalBinding(Engine engine, CompletableFuture<RETURNTYPE> cf) {
        super(engine,cf);
    }

    @Override
    Binding<?> doActivate(Binding<?> pending) {
        return pending;
    }

    @Override
    protected Object invokeTaskMethod() {
        throw new RuntimeException("Not expected");
    }

    @Override
    String doGetExecutionName() {
        return "<<EXTERN>>";
    }

    @Override
    public TaskInterface<?> getTask() {
        return null;
    }

    @Override
    public void formatActualSignature(StringBuilder sb) {}
}
