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

import com.ebay.bascomtask.core.Orchestrator;

import java.util.concurrent.CompletableFuture;

/**
 * Some graph variations for testing.
 *
 * @author Brendan McCarthy
 */
public class GraphVariations {

    private static LongOperationsTask task() {
        return new LongOperationsTask.LongOperationsTaskImpl();
    }

    private static <T> T get(CompletableFuture<T> cf) {
        try {
            return cf.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static long diamond(long input) {
        Orchestrator $ = Orchestrator.create("diamond");

        CompletableFuture<Long> base = $.task(task()).ret(input);
        CompletableFuture<Long> left = $.task(task()).inc(base);
        CompletableFuture<Long> right = $.task(task()).inc(base);
        CompletableFuture<Long> bottom = $.task(task()).add(left, right);

        return get(bottom);
    }

    public static long grid3x3(long input) {
        Orchestrator $ = Orchestrator.create("grid3x3");

        CompletableFuture<Long> ul = $.task(task()).ret(input);
        CompletableFuture<Long> um = $.task(task()).ret(input);
        CompletableFuture<Long> ur = $.task(task()).ret(input);
        CompletableFuture<Long> ml = $.task(task()).add(ul, um, ur);
        CompletableFuture<Long> mm = $.task(task()).add(ul, um, ur);
        CompletableFuture<Long> mr = $.task(task()).add(ul, um, ur);
        CompletableFuture<Long> bl = $.task(task()).add(ml, mm, mr);
        CompletableFuture<Long> bm = $.task(task()).add(ml, mm, mr);
        CompletableFuture<Long> br = $.task(task()).add(ml, mm, mr);

        $.activateAndWait(bl, bm, br);

        return get(bl) + get(bm) + get(br);
    }

    public static long stacks(long input) {
        Orchestrator $ = Orchestrator.create("diamond");

        CompletableFuture<Long> left1 = $.task(task()).ret(input);  // v
        CompletableFuture<Long> left2 = $.task(task()).inc(left1);  // v+1
        CompletableFuture<Long> left3 = $.task(task()).add(left1, left2);   // v+v+1 = 2v+1
        CompletableFuture<Long> left4 = $.task(task()).add(left2, left3);  // (v+1) + (2v+1) = 3v+2
        CompletableFuture<Long> left5 = $.task(task()).add(left2, left3, left4);  // v+1 + 2v + 1 + 3v +2 = 6v+ 4

        CompletableFuture<Long> right1 = $.task(task()).ret(input);
        CompletableFuture<Long> right2 = $.task(task()).inc(right1);
        CompletableFuture<Long> right3 = $.task(task()).add(right1, right2);
        CompletableFuture<Long> right4 = $.task(task()).add(right2, right3);
        CompletableFuture<Long> right5 = $.task(task()).add(right2, right3, right4);

        CompletableFuture<Long> left6 = $.task(task()).add(left3, right5); // 2v+1 + 6v+4 = 8v+5
        CompletableFuture<Long> right6 = $.task(task()).add(right3, left5); //
        CompletableFuture<Long> add = $.task(task()).add(left6, right6);

        // Start various combinations first for variation in graph flow
        switch (((int) input % 5) + 1) {
            case 1:
                $.activate(left1, right1);
                break;
            case 2:
                $.activate(left2, right2);
                break;
            case 3:
                $.activate(left3, right3);
                break;
            case 4:
                $.activate(left4, right4);
                break;
            case 5:
                $.activate(left5, right5);
                break;
            default: // do nothing
        }

        return get(add);
    }
}
