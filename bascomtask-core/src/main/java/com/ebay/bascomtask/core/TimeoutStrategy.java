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

import java.util.concurrent.TimeUnit;

/**
 * Strategies for dealing with execution timeouts that impact threads spawned during the execution of a request
 * such as {@link java.util.concurrent.CompletableFuture#get(long, TimeUnit)}. The minimum is{@link #PREVENT_NEW},
 * which always applies and is the default. The other strategies indicate whether or not to interrupt any other
 * threads spawned by that request. As is standard with Java, if and how any user task POJO responds to an
 * interrupt is completely with the control of that task POJO.
 *
 * @author Brendan McCarthy
 */
public enum TimeoutStrategy {
    /**
     * Once a timeout occurs, don't start new tasks but don't interrupt existing tasks. Default strategy.
     */
    PREVENT_NEW,

    /**
     * Once a timeout occurs, in addition to {@link #PREVENT_NEW}, the next time any task is attempted then
     * interrupt all others.
     */
    INTERRUPT_AT_NEXT_OPPORTUNITY,

    /**
     * Once a timeout occurs, in addition to {@link #PREVENT_NEW}, immediately interrupt all tasks. This is
     * the strongest reaction, and incurs the expense spawning a dedicated thread to watch for the timeout.
     */
    INTERRUPT_IMMEDIATELY
}
