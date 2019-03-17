package com.ebay.bascomtask.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An ExecutorService (although we've only bothered to implement one method thereof) that is also a latch,
 * and makes use of a single top-level thread instead of pulling from the pool rather than keeping that
 * top-level thread blocked.
 * 
 * @author bremccarthy
 */
public class ExecutorLatch {
    private final ExecutorService target;
    private boolean threadWaiting = false;
    private Runnable pendingCommand = null;
    private int latchCount = 0;
    private AtomicInteger numberOfThreadsCreated = new AtomicInteger(0);
    
    /**
     * Creates a new latch that draws threads from the provided pool when needed
     * @param es to draw from
     */
    public ExecutorLatch(ExecutorService es) {
        this.target = es;
    }
    
    /**
     * Equivalent to ExecutorService.execute.
     * 
     * @param command to run
     */
    public void execute(final Runnable command) {
        synchronized (this) {
            if (threadWaiting) {
                this.pendingCommand = command;
                notify();
                return;
            }
            latchCount++;
        }
        numberOfThreadsCreated.incrementAndGet();
        target.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    command.run();
                }
                finally {
                    free();
                }
            }
        });
    }
    
    /**
     * Variant of {@link #execute(Runnable)} with a second argument that controls the source
     * from which the thread is drawn from.
     * @param command to run
     * @param fromLatchElseDirect iff true, skips execute on this object and redirects to target pool directly
     */
    public void executeFrom(final Runnable command, boolean fromLatchElseDirect) {
        if (fromLatchElseDirect) {
            execute(command);
        }
        else {
            numberOfThreadsCreated.incrementAndGet();
            target.execute(command);
        }
    }

    /**
     * Returns the number times a thread was drawn from the pool provided as constructor arguments.
     * @return number of threads drawn from pool
     */
    public int getNumberOfThreadsCreated() {
        return numberOfThreadsCreated.get();
    }
    
    private synchronized void free() {
        if (--latchCount <= 0) {
            notify();
        }
    }
    
    /**
     * Thread-waits until there are no other active threads (i.e. threads create from {@link #execute(Runnable)}
     * on this object). While waiting, the calling thread may be put to useful work.
     */
    public void workWait() {
        do {
            Runnable cmd = null;
            synchronized (this) {
                if (pendingCommand != null) {
                    cmd = pendingCommand;
                    pendingCommand = null;
                }
                else if (latchCount > 0) {
                    try {
                        if (threadWaiting) {
                            throw new RuntimeException("Cannot have two threads waiting");
                        }
                        threadWaiting = true;
                        wait();
                        threadWaiting = false;
                    }
                    catch (InterruptedException e) {
                        throw new RuntimeException("workWait interrupt",e);
                    }
                }
                else {
                    break;
                }
            }
            if (cmd != null) {
                cmd.run();
            }
        }
        while (true);
    }
}
