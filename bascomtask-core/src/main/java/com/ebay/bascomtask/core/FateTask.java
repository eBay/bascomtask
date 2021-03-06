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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implements {@link Orchestrator#fate(CompletableFuture[])}.
 *
 * @author Brendan McCarthy
 */
class FateTask extends Binding<Boolean> implements TaskInterface<FateTask> {
    private static final Logger LOG = LoggerFactory.getLogger(FateTask.class);

    private CompletableFuture<Boolean> result = CompletableFuture.completedFuture(false);
    private final AtomicBoolean executed = new AtomicBoolean(false);

    FateTask(Engine engine, CompletableFuture<?>... cfs) {
        super(engine);
        for (CompletableFuture<?> cf : cfs) {
            ensureWrapped(cf, true);
        }
    }

    @Override
    protected Object invokeTaskMethod() {
        return result;
    }

    /**
     * Intercepts parent method to prevent fault propagation.
     *
     * @param t     being thrown
     * @param fates list of FateTasks to collect
     */
    @Override
    void faultForward(Throwable t, List<FateTask> fates) {
        if (executed.compareAndSet(false, true)) {
            result = CompletableFuture.completedFuture(true);
            LOG.debug("Swallowing forward-fault");
            fates.add(this);
        }
    }

    @Override
    String doGetExecutionName() {
        return "<<FATE>>";
    }

    @Override
    public TaskInterface<?> getTask() {
        return this;
    }

    @Override
    public void formatActualSignature(StringBuilder sb) {

    }
}
