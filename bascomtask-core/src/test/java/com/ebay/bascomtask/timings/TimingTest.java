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
package com.ebay.bascomtask.timings;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Timing tests to ensure that multiple loops of various graph combinations do not take an
 * unexpected amount of time.
 *
 * @author Brendan McCarthy
 */
public class TimingTest {
    private static final Logger LOG = LoggerFactory.getLogger(TimingTest.class);

    private static int LOOP_COUNT = 1000;

    public static Level levelSave;

    @BeforeClass
    public static void beforeClass() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger logger = loggerContext.getLogger("root");
        levelSave = logger.getLevel();
        // Avoid debug logging because we're looping thousands of times here
        logger.setLevel(Level.INFO);
    }

    @AfterClass
    public static void afterClass() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger logger = loggerContext.getLogger("root");
        logger.setLevel(levelSave);
    }

    /**
     * Runs fn repeatedly after a warmup, testing to ensure that there's no egregious slowness.
     * Typically the average max should be much lower that the supplied expectedMaxMs to avoid failure for an
     * occasional system overload irregularity.
     *
     * @param expectedMaxMs verify that avg for all runs does not exceed this
     * @param loc           description to include in log output
     * @param fn            to run on eac loop
     * @param expecting     verify each run
     */
    private void run(double expectedMaxMs, String loc, Function<Long, Long> fn, Function<Long, Long> expecting) {
        for (long i = 100; i < LOOP_COUNT; i++) { // Warmup
            fn.apply(i);
        }
        long start = System.nanoTime();
        for (long i = 0; i < LOOP_COUNT; i++) {
            long got = fn.apply(i);
            long exp = expecting.apply(i);
            if (exp != got) {
                assertEquals("Loop#" + i, exp, got);
            }
        }
        long duration = System.nanoTime() - start;
        double avg = (duration / (float) LOOP_COUNT) / 1000000;
        String msg = String.format("Avg time for %d loops on \"%s\": %.2fms%n", LOOP_COUNT, loc, avg);
        LOG.info(msg);
        assertTrue(avg < expectedMaxMs);
    }

    @Test
    public void diamondTest() {
        run(1, "diamond", GraphVariations::diamond, v -> (v + 1) * 2);
    }

    @Test
    public void gridTest() {
        run(1.5, "grid3x3", GraphVariations::grid3x3, v -> v * 27);
    }

    @Test
    public void stacksTest() {
        run(2, "stacks", GraphVariations::stacks, v -> v * 16 + 10);
    }
}
