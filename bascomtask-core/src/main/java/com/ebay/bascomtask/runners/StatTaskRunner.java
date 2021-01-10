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
package com.ebay.bascomtask.runners;

import com.ebay.bascomtask.core.TaskRun;
import com.ebay.bascomtask.core.TaskRunner;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Collects various task execution statistics.
 *
 * @author Brendan McCarthy
 */
public class StatTaskRunner implements TaskRunner {
    private final Map<String, InternalStat> map = new HashMap<>();

    private static class InternalTiming {
        private long maxExecTime = 0;
        private long minExecTime = Long.MAX_VALUE;
        private long execTotal = 0;

        double avg(int count) {
            return count == 0 ? 0 : execTotal / (double) count;
        }

        void add(long duration) {
            execTotal += duration;
            minExecTime = Math.min(minExecTime, duration);
            maxExecTime = Math.max(maxExecTime, duration);
        }
    }

    private static class InternalStat {
        private int count = 0;
        final InternalTiming execTime = new InternalTiming();
        final InternalTiming completionTime = new InternalTiming();

        void add(long duration) {
            count++;
            execTime.add(duration);
        }
    }

    private synchronized void add(String key, long duration) {
        InternalStat stat = map.computeIfAbsent(key, k -> new InternalStat());
        stat.add(duration);
    }

    private synchronized void extend(String key, long duration) {
        InternalStat stat = map.computeIfAbsent(key, k -> new InternalStat());
        stat.completionTime.add(duration);
    }

    @Override
    public Object before(TaskRun taskRun) {
        return null;
    }

    @Override
    public Object executeTaskMethod(TaskRun taskRun, Thread parentThread, Object fromBefore) {
        return taskRun.run();
    }

    @Override
    public void onComplete(TaskRun taskRun, Object fromBefore, boolean doneOnExit) {
        long duration = taskRun.getEndedAt() - taskRun.getStartedAt();
        add(taskRun.getTaskPlusMethodName(), duration);
        if (!doneOnExit) {
            duration = taskRun.getCompletedAt() - taskRun.getEndedAt();
            extend(taskRun.getTaskPlusMethodName(), duration);
        }
    }

    private static int width(long number) {
        int length = 1;
        if (number >= 100000000) {
            length += 8;
            number /= 100000000;
        }
        if (number >= 10000) {
            length += 4;
            number /= 10000;
        }
        if (number >= 100) {
            length += 2;
            number /= 100;
        }
        if (number >= 10) {
            length += 1;
        }
        return length;
    }

    public static class Timing {
        public long average;
        public long max;
        public long min;

        void populateFrom(InternalTiming internalTiming, int count) {
            this.average = Math.round(internalTiming.avg(count));
            this.max = internalTiming.maxExecTime;
            this.min = internalTiming.minExecTime;
        }
    }

    public static class Stat {
        public String taskMethod;
        public long count;
        public Timing execTime;
        public Timing completionTime;
    }

    public static class Report {
        Stat[] stats;
    }

    /**
     * Returns a summarized execution data snapshot.
     *
     * @return data
     */
    public synchronized Report collect() {
        Report report = new Report();
        int sz = map.size();
        report.stats = new Stat[sz];
        int pos = 0;
        for (Map.Entry<String, InternalStat> next : map.entrySet()) {
            Stat stat = report.stats[pos++] = new Stat();
            stat.taskMethod = next.getKey();
            InternalStat internalStat = next.getValue();
            stat.count = internalStat.count;
            stat.execTime = new Timing();
            stat.execTime.populateFrom(internalStat.execTime, internalStat.count);
            if (internalStat.completionTime.maxExecTime > 0) {
                stat.completionTime = new Timing();
                stat.completionTime.populateFrom(internalStat.completionTime, internalStat.count);
            }
        }
        return report;
    }

    private static void fill(PrintStream ps, char c, int count) {
        for (int i = 0; i < count; i++) {
            ps.print(c);
        }
    }

    /**
     * A table column.
     */
    private static abstract class Col {
        final String hdr;
        int maxWidth;

        Col(String hdr) {
            this.hdr = hdr;
            maxWidth = hdr.length();
        }

        void widen(Stat stat) {
            maxWidth = Math.max(maxWidth, getValueWidth(stat));

        }

        void hdr(PrintStream ps) {
            int len = hdr.length();
            int fill = Math.max(0, maxWidth - len);
            int before = fill / 2;
            int after = fill - before;
            fill(ps, ' ', before + 1);
            ps.print(hdr);
            fill(ps, ' ', after + 1);
            ps.print('|');
        }

        void sep(PrintStream ps, char c) {
            fill(ps, '-', maxWidth + 2);
            ps.print(c);
        }

        abstract int getValueWidth(Stat stat);

        abstract void print(PrintStream ps, Stat stat);

        void cell(PrintStream ps, Stat stat) {
            int width = getValueWidth(stat);
            print(ps, stat);
            fill(ps, ' ', 2 + maxWidth - width);
            ps.print('|');
        }
    }

    private static class LongCol extends Col {
        private final Function<Stat, Long> fn;

        LongCol(String hdr, Function<Stat, Long> fn) {
            super(hdr);
            this.fn = fn;
        }

        @Override
        int getValueWidth(Stat stat) {
            return width(fn.apply(stat));
        }

        @Override
        void print(PrintStream ps, Stat stat) {
            ps.print(fn.apply(stat));
        }
    }

    static class StringCol extends Col {
        private final Function<Stat, String> fn;

        StringCol(String hdr, Function<Stat, String> fn) {
            super(hdr);
            this.fn = fn;
        }

        @Override
        int getValueWidth(Stat stat) {
            return fn.apply(stat).length();
        }

        @Override
        void print(PrintStream ps, Stat stat) {
            ps.print(fn.apply(stat));
        }
    }

    private static class AddCol extends Col {
        private final Function<Timing, Long> fn;

        AddCol(String hdr, Function<Timing, Long> fn) {
            super(hdr);
            this.fn = fn;
        }

        @Override
        int getValueWidth(Stat stat) {
            int w = width(fn.apply(stat.execTime));
            if (stat.completionTime != null) {
                w += width(fn.apply(stat.completionTime)) + 1;
            }
            return w;
        }

        @Override
        void print(PrintStream ps, Stat stat) {
            ps.print(fn.apply(stat.execTime));
            if (stat.completionTime != null) {
                ps.print('+');
                ps.print(fn.apply(stat.completionTime));
            }
        }
    }

    private void row(PrintStream ps, List<Col> cols, char div, Consumer<Col> fn) {
        ps.print(div);
        cols.forEach(fn);
        ps.print('\n');
    }

    /**
     * Prints a tabular-formatted summary of statistics to the given PrintStream.
     *
     * @param ps to print to
     */
    public void report(PrintStream ps) {
        Report report = collect();

        List<Col> cols = Arrays.asList(
                new LongCol("Count", stat -> stat.count),
                new AddCol("Avg", timing -> timing.average),
                new AddCol("Min", timing -> timing.min),
                new AddCol("Max", timing -> timing.max),
                new StringCol("Method", stat -> stat.taskMethod)
        );

        for (Stat next : report.stats) {
            cols.forEach(col -> col.widen(next));
        }

        row(ps, cols, '-', col -> col.sep(ps, '-'));
        row(ps, cols, '|', col -> col.hdr(ps));
        row(ps, cols, '|', col -> col.sep(ps, '|'));

        for (Stat next : report.stats) {
            row(ps, cols, '|', col -> col.cell(ps, next));
        }

        row(ps, cols, '-', col -> col.sep(ps, '-'));
    }

    /**
     * Returns a table-formatted summary as a string.
     *
     * @return table summary
     */
    public String report() {
        final String ENC = StandardCharsets.UTF_8.name();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos, true, ENC);
            report(ps);
            ps.flush();
            return baos.toString(ENC);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Bad encoding", e);
        }
    }
}
