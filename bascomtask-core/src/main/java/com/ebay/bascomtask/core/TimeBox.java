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

import com.ebay.bascomtask.exceptions.TimeoutExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records user-requested timeout durations and detects when that timeout has been exceeded.
 *
 * @author Brendan McCarthy
 */
class TimeBox {
    private static final Logger LOG = LoggerFactory.getLogger(TimeBox.class);

    static final TimeBox NO_TIMEOUT = new TimeBox(0);

    final long timeBudget;
    final long start;

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
        if (timeBudget==0) {
            return "TimeBox(0)";
        } else {
            long left = (start + timeBudget) - System.currentTimeMillis();
            String msg = left <=0 ? "EXCEEDED" : (String.valueOf(left)+"ms left");
            return "TimeBox(" + start + "," + timeBudget + ',' + msg + ')';
        }
    }

    void check(Binding binding) {
        if (timeBudget > 0 && System.currentTimeMillis() >= start + timeBudget) {
            String msg = "Timeout " + timeBudget + " exceeded before " + binding.getTaskPlusMethodName() + ", ceasing task creation";
            LOG.debug("Throwing " + msg);
            throw new TimeoutExceededException(msg);
        }
    }
}
