package com.ebay.bascomtask.util;

import com.ebay.bascomtask.core.TaskMeta;

import static org.junit.Assert.assertTrue;

public class CommonTestingUtils {

    public static void validateTimes(TaskMeta meta) {
        assertTrue(meta.getStartedAt() > 0);
        assertTrue(meta.getEndedAt() > 0);
        assertTrue(meta.getCompletedAt() > 0);
        assertTrue(meta.getEndedAt() >= meta.getStartedAt());
        assertTrue(meta.getCompletedAt() >= meta.getCompletedAt());
    }
}
