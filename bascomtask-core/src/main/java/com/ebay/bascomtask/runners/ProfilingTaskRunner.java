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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks and creates a task execution profile on demand.
 *
 * @author Brendan McCarthy
 * @see #format()
 */
public class ProfilingTaskRunner implements TaskRunner {

    private final List<Event> events = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, ThreadTracker> threadMap = new ConcurrentHashMap<>();
    private final List<Event> rowHeaders = new ArrayList<>();
    private final AtomicInteger externCount = new AtomicInteger(0);

    /**
     * One for each logical thread, as determined by its thread name.
     */
    private class ThreadTracker {
        final int index;
        final String threadName;
        Event firstEvent = null;
        Event[] events = null;
        boolean active = false;
        final char mark;

        ThreadTracker(String nm, char mark) {
            this.index = threadMap.size();
            this.threadName = nm;
            this.mark = mark;
        }

        boolean isInternElseExtern() {
            return mark == '-';
        }
    }

    private abstract class Event {
        final long ts;
        final TaskRun taskRun;
        final ThreadTracker threadTracker;
        int level;
        final int columnOrder;
        char bracket = '-';
        final char mark;

        Event(TaskRun taskRun, long ts, String threadName, char mark, int columnOrder) {
            this.taskRun = taskRun;
            this.ts = ts;
            this.threadTracker = threadMap.computeIfAbsent(threadName, k -> new ThreadTracker(threadName, mark));
            this.mark = mark;
            this.columnOrder = columnOrder;
        }

        void setBracket(char c) {
            this.bracket = c;
        }

        @Override
        public String toString() {
            String nm = getClass().getSimpleName();
            String mn = taskRun.getTaskPlusMethodName();
            return nm + "(" + mn + ", [" + threadTracker.threadName + "], level=" + level + ")";

        }

        abstract boolean effect();

        abstract boolean nameElseBlanks();

        abstract boolean replaces(Event e);

        boolean replacedBy(StartEvent e) {
            return false;
        }

        boolean replacedBy() {
            return false;
        }

        boolean replacedBy(CompletionEvent e) {
            return false;
        }
    }

    private class StartEvent extends Event {

        StartEvent(TaskRun taskRun, long ts, String threadName, char mark, int columnOrder) {
            super(taskRun, ts, threadName, mark, columnOrder);
        }

        @Override
        boolean effect() {
            return true;
        }

        @Override
        boolean nameElseBlanks() {
            return true;
        }

        @Override
        boolean replaces(Event e) {
            return e.replacedBy(this);
        }

        @Override
        boolean replacedBy(StartEvent that) {
            return this.taskRun == that.taskRun;
        }
    }

    private class EndEvent extends Event {
        EndEvent(TaskRun taskRun, long ts, String threadName) {
            super(taskRun, ts, threadName, '-', 0);
        }

        @Override
        boolean effect() {
            return false;
        }

        @Override
        boolean nameElseBlanks() {
            return false;
        }

        @Override
        boolean replaces(Event e) {
            return e.replacedBy();
        }

        boolean replacedBy(StartEvent e) {
            return true;
        }

        boolean replacedBy(CompletionEvent e) {
            return true;
        }
    }

    private class CompletionEvent extends Event {

        CompletionEvent(TaskRun taskRun, long ts, String threadName) {
            super(taskRun, ts, threadName, '+', 0);
        }

        @Override
        boolean effect() {
            return false;
        }

        @Override
        boolean nameElseBlanks() {
            return false;
        }

        @Override
        boolean replaces(Event e) {
            return e.replacedBy(this);
        }

        boolean replacedBy(StartEvent e) {
            return true;
        }
    }

    @Override
    public Object before(TaskRun taskRun) {
        return null;
    }

    @Override
    public Object executeTaskMethod(TaskRun taskRun, Thread parentThread, Object fromBefore) {
        final String threadName = Thread.currentThread().getName();
        events.add(new StartEvent(taskRun, taskRun.getStartedAt(), threadName, '-', 0));
        try {
            return taskRun.run();
        } finally {

            events.add(new EndEvent(taskRun, taskRun.getEndedAt(), threadName));
        }
    }

    @Override
    public void onComplete(TaskRun taskRun, Object fromBefore, boolean doneOnExit) {
        long cat = taskRun.getCompletedAt();
        if (cat > taskRun.getEndedAt()) {
            final String threadName = "EX*TERN+" + externCount.getAndIncrement();
            events.add(new StartEvent(taskRun, taskRun.getStartedAt(), threadName, '+', 1));
            events.add(new CompletionEvent(taskRun, cat, threadName));
        }
    }

    private static void fill(StringBuilder sb, int spaces) {
        for (int i = 0; i < spaces; i++) {
            sb.append(' ');
        }
    }

    private final static int TIMESTAMP_COLUMN_WIDTH = 3;

    private static void fill(StringBuilder sb, long v) {
        String s = String.valueOf(v);
        int spaces = Math.max(0, TIMESTAMP_COLUMN_WIDTH - s.length());
        fill(sb, spaces);
        sb.append(v);
    }

    /**
     * Returns a tabular execution profile with events as rows and threads as columns. The first column is the
     * millisecond timestamp relative to the start of execution. The second column is the task method name. The
     * third column rightward are execution threads, labeled with unique numbers and letters except that for the
     * thread calling this (format) method, a caret is displayed since that typically represents the calling thread
     * for orchestration execution.
     *
     * <p>A row is created any time (millisecond granularity) during which at least one task-related event occurred.
     * A task start and end is indicated is by '---' entries, and if active between these two points then just a
     * a '-  entry. For example,
     *
     * <pre>
     *                    0
     *     0| gold.pond  ---
     *    10|            ---
     * </pre>
     * and
     * <pre>
     *                    0
     *     0| gold.pond  ---
     *     5|             -
     *    10|            ---
     * </pre>
     * These each indicate that gold.pond ended 10 ms after start; the value at 5 indicates that it was active
     * and would only be drawn if there were additional columns (not shown above) because other things of interest
     * (not shown above) occurred at that time.
     * <p>
     * When a task immediately follows another within the same timestamp value, the end of the first is omitted and
     * the new task entry has '=' markers instead of '-'. In the following, for example, gold.pond ends at timestamp 10
     * and gold.fish begins at that same timestamp, so the entry is '=-=' (the middle char serves other purposes,
     * see next paragraph):
     *
     * <pre>
     *                    0
     *     0| gold.pond  ---
     *    10| gold.fish  =-=
     *    20|            ---
     * </pre>
     * <p>
     * Tasks that return non-completed CompletableFutures have by definition a thread outside of BascomTask control.
     * Each of these is represented in its own column and such columns are letter-numbered beginning with 'A'. The
     * markers are '-+-' and '+', replacing the corresponding markers used in BascomTask-controlled threads.
     * A task _always_ has a beginning '---' or '=-=' marker in a BascomTask-controlled thread, and it it _may also_
     * have a '-+-' entry on the same line in a letter-valued column.
     *
     * @return table-formatted execution summary
     */
    public String format() {
        if (events.size() == 0) {
            return "<<No tasks executed>>";
        } else {
            events.sort(Comparator.comparingLong(e -> e.ts));

            int nameColumnWidth = prepare();

            nameColumnWidth += 2;
            StringBuilder sb = new StringBuilder();
            List<ThreadTracker> threadTrackers = getOrderedTrackers();

            formatHdr(sb, nameColumnWidth, threadTrackers);
            formatBody(sb, nameColumnWidth, threadTrackers);
            return sb.toString();
        }
    }

    private int prepare() {
        int max = 0;
        rowHeaders.clear();
        for (Event next : events) {
            int sz = rowHeaders.size();
            if (sz == 0) {
                rowHeaders.add(next);
            } else {
                Event lastRowHeader = rowHeaders.get(sz - 1);
                if (next.ts == lastRowHeader.ts && next.replaces(lastRowHeader)) {
                    if (next.mark == '-') {  // Only for BT columns
                        next.setBracket('=');
                    }
                    rowHeaders.set(sz - 1, next);
                } else {
                    rowHeaders.add(next);
                }
            }
            next.level = rowHeaders.size() - 1;
            if (next.threadTracker.firstEvent == null) {
                next.threadTracker.firstEvent = next;
            }
        }

        for (ThreadTracker next : threadMap.values()) {
            next.events = new Event[rowHeaders.size()];
        }

        for (Event next : events) {
            max = Math.max(max, next.taskRun.getTaskPlusMethodName().length());
            next.threadTracker.events[next.level] = next;
        }
        return max;
    }

    List<ThreadTracker> getOrderedTrackers() {
        List<ThreadTracker> trackers = new ArrayList<>(threadMap.values());

        // Order by time of firs event so that the visual flow tends to go left-right / top-bottom.
        // Secondarily order so that BascomTask thread columns are shown before external thread columns.
        Comparator<ThreadTracker> byTimestamp = Comparator.comparingLong(t -> t.firstEvent.ts);
        Comparator<ThreadTracker> byOrder = Comparator.comparingLong(t -> t.firstEvent.columnOrder);
        trackers.sort(byTimestamp.thenComparing(byOrder));
        return trackers;
    }

    private void formatHdr(StringBuilder sb, int nameColumnWidth, List<ThreadTracker> trackers) {

        fill(sb, TIMESTAMP_COLUMN_WIDTH + 4 + nameColumnWidth);
        int internCount = 0;
        int externCount = 0;

        for (ThreadTracker next : trackers) {
            String hdr;
            if (Thread.currentThread().getName().equals(next.threadName)) {
                hdr = "^";
            } else if (next.isInternElseExtern()) {
                hdr = String.valueOf(internCount++);
            } else {
                hdr = String.format("%c", ('A' + externCount++));
            }
            sb.append(' ');
            sb.append(hdr);
            fill(sb, 3 - hdr.length());
        }
        sb.append('\n');
    }

    private void formatBody(StringBuilder sb, int nameColumnWidth, List<ThreadTracker> trackers) {
        final long baseline = events.get(0).ts;

        for (int i = 0; i < rowHeaders.size(); i++) {
            Event nextRowHeader = rowHeaders.get(i);
            long delta = nextRowHeader.ts - baseline;
            fill(sb, delta);
            sb.append("| ");
            String nm = nextRowHeader.taskRun.getTaskPlusMethodName();
            if (nextRowHeader.nameElseBlanks()) {
                sb.append(nm);
            } else {
                fill(sb, nm.length());
            }
            int spaces = nameColumnWidth - nm.length() + 2;
            fill(sb, spaces);
            for (ThreadTracker nextTracker : trackers) {
                Event ex = nextTracker.events[i];

                char b = ex == null ? ' ' : ex.bracket;
                char m = (ex == null && !nextTracker.active) ? ' ' : nextTracker.mark;
                sb.append(b);
                sb.append(m);
                sb.append(b);
                sb.append(' ');

                if (ex != null) {
                    nextTracker.active = ex.effect();
                }
            }
            sb.append('\n');
        }
    }
}
