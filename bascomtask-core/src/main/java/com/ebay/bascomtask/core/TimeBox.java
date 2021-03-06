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

import com.ebay.bascomtask.exceptions.TimeoutExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Records user-requested timeout durations and detects when that timeout has been exceeded.
 *
 * @author Brendan McCarthy
 */
class TimeBox {
    private static final Logger LOG = LoggerFactory.getLogger(TimeBox.class);

    // Static instance used for no-timeout case (timeBudget==0) since there is no need to have a unique
    // instance for values that will always be the same
    static final TimeBox NO_TIMEOUT = new TimeBox(0);

    // How many milliseconds before the timeout
    final long timeBudget;

    // When did the clock start, in milliseconds
    final long start;

    // Records threads to be interrupted, when the TimeoutStrategy in effect calls for interrupts
    private List<Thread> activeThreeads = null;

    /**
     * A timeBudget of zero means no timeout check will later be made.
     *
     * @param timeBudget to (later) check for
     */
    TimeBox(long timeBudget) {
        this.timeBudget = timeBudget;
        this.start = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        if (timeBudget == 0) {
            return "TimeBox(0)";
        } else {
            long left = (start + timeBudget) - System.currentTimeMillis();
            String msg = isTimedOut() ? "EXCEEDED" : (left + "ms left");
            return "TimeBox(" + start + "," + timeBudget + ',' + msg + ')';
        }
    }

    private boolean isTimedOut() {
        // Apply gt here rather than gte since in some spawnmodes we get to this point very quickly
        return System.currentTimeMillis() > start + timeBudget;
    }

    void checkIfTimeoutExceeded(Binding<?> binding) {
        if (timeBudget > 0 && isTimedOut()) {
            String msg = "Timeout " + timeBudget + " exceeded before " + binding.getTaskPlusMethodName() + ", ceasing task creation";
            LOG.debug("Throwing " + msg);
            throw new TimeoutExceededException(msg);
        }
    }

    void checkForInterruptsNeeded(Binding<?> binding) {
        if (timeBudget > 0) {
            if (binding.engine.getTimeoutStrategy() == TimeoutStrategy.INTERRUPT_AT_NEXT_OPPORTUNITY) {
                if (isTimedOut()) {
                    interruptRegisteredThreads();
                }
            }
        }
    }

    /**
     * Interrupts any currently-registered thread.
     */
    void interruptRegisteredThreads() {
        synchronized (this) {
            if (activeThreeads != null) {
                int count = activeThreeads.size() - 1;  // Exclude current thread
                if (count > 0) {
                    String msg = " on timeout " + timeBudget + " exceeded";
                    for (Thread next : activeThreeads) {
                        if (next != Thread.currentThread()) {
                            LOG.debug("Interrupting " + next.getName() + msg);
                            next.interrupt();
                        }
                    }
                }
            }
        }
    }

    /**
     * Register current thread so that it may later be interrupted on timeout.
     *
     * @param orchestrator active
     */
    void register(Orchestrator orchestrator) {
        if (orchestrator.getTimeoutStrategy() != TimeoutStrategy.PREVENT_NEW) {
            synchronized (this) {
                if (activeThreeads == null) {
                    activeThreeads = new ArrayList<>();
                }
                activeThreeads.add(Thread.currentThread());
            }
        }
    }

    /**
     * De-registers current thead and ensures monitoring thread, if present is notified if there are no
     * more registered threads. This call must follow every {@link #register(Orchestrator)} call.
     */
    synchronized void deregister() {
        if (activeThreeads != null) {
            activeThreeads.remove(Thread.currentThread());
            if (activeThreeads.size() == 0) {
                notify();
            }
        }
    }

    /**
     * Sets up a monitoring thread if needed.
     *
     * @param orchestrator context
     */
    void monitorIfNeeded(Orchestrator orchestrator) {
        if (orchestrator.getTimeoutStrategy() == TimeoutStrategy.INTERRUPT_IMMEDIATELY) {
            orchestrator.getExecutorService().execute(() -> {
                synchronized (this) {
                    try {
                        wait(timeBudget);
                        interruptRegisteredThreads();
                    } catch (InterruptedException ignore) {
                        // do nothing
                    }
                }
            });
        }
    }
}
