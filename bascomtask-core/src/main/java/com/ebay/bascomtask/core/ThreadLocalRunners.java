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

import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Allows setting of a per-orchestrator TaskRunner that applies to an Orchestrator created by the current thread
 * using ThreadLocal storage.
 *
 * @author Brendan McCarthy
 */
public class ThreadLocalRunners<RUNNER extends TaskRunner> {

    private final ThreadLocal<RUNNER> threadRunner = new ThreadLocal<>();

    private void firstSet(Orchestrator orchestrator, Supplier<RUNNER> fn) {
        RUNNER runner = fn.get();
        orchestrator.firstInterceptWith(runner);
        threadRunner.set(runner);
    }

    private void lastSet(Orchestrator orchestrator, Supplier<RUNNER> fn) {
        RUNNER runner = fn.get();
        orchestrator.lastInterceptWith(runner);
        threadRunner.set(runner);
    }

    /**
     * Adds a TaskRunner to each new Orchestrator at the head of the list.
     *
     * @param fn to generate the TaskRunner to add
     */
    public void firstInterceptWith(Supplier<RUNNER> fn) {
        GlobalConfig.getConfig().initializeWith(($,arg)-> {
            firstSet($,fn);
        });
    }

    /**
     * Adds a TaskRunner to each new Orchestrator to the tail of the list.
     *
     * @param fn to generate the TaskRunner to add
     */
    public void lastInterceptWith(Supplier<RUNNER> fn) {
        GlobalConfig.getConfig().initializeWith(($,arg)-> {
            lastSet($,fn);
        });
    }

    /**
     * Adds a TaskRunner to each new Orchestrator at the head of the list if the supplied predicate is true.
     * The predicate arguments are the same as those for
     * {@link GlobalConfig.Config#afterDefaultInitialization(Orchestrator, Object)}.
     *
     * @param fn to generate the TaskRunner to add
     */
    public void firstInterceptWith(Supplier<RUNNER> fn, BiPredicate<Orchestrator,Object> pred) {
        GlobalConfig.getConfig().initializeWith(($,arg)-> {
            if (pred.test($,arg)) {
                firstSet($,fn);
            }
        });
    }

    /**
     * Adds a TaskRunner to each new Orchestrator to the tail of the list if the supplied predicate is true.
     * The predicate arguments are the same as those for
     * {@link GlobalConfig.Config#afterDefaultInitialization(Orchestrator, Object)}.
     *
     * @param fn to generate the TaskRunner to add
     */
    public void lastInterceptWith(Supplier<RUNNER> fn, BiPredicate<Orchestrator,Object> pred) {
        GlobalConfig.getConfig().initializeWith(($,arg)-> {
            if (pred.test($,arg)) {
                lastSet($,fn);
            }
        });
    }

    /**
     * Returns the TaskRunner last generated by the supplier function to {@link #firstInterceptWith(Supplier)}
     * or {@link #firstInterceptWith(Supplier, BiPredicate)} for this thread. If more than one Orchestrator
     * was created for this thread since one of those methods was called, this will only return the last one.
     *
     * @return the last TaskRunner created for this thread
     */
    public RUNNER getAndClear() {
        RUNNER runner = threadRunner.get();
        threadRunner.remove();
        return runner;
    }
}