package com.ebay.bascomtask.config;

import com.ebay.bascomtask.main.TaskMethodClosure;

public interface ITaskClosureGenerator {

    /**
     * Provides a closure for invoking task method calls. The default invocation
     * behavior can be customized by overriding this method and returning a
     * <code>TaskMethodClosure</code> subclass. This method will always be
     * called for root tasks (i.e. those that can start immediately without
     * waiting on other tasks), and will be called for all (root and non-root)
     * tasks unless
     * {@link com.ebay.bascomtask.main.TaskMethodClosure#getClosure()} is
     * overridden and returns a non-null result, in which case that will apply
     * to non-root tasks. In other words, simply overriding this method is
     * sufficient for many cases, but if/when it is desired to distinguish
     * between root and non-root tasks, the TaskMethodClosure override may be
     * useful.
     * <p>
     * Note that there is no guarantee that the <code>TaskMethodClosure</code>
     * returned will be invoked; overriding subclasses should simply return an
     * instance of the desired type.
     * 
     * @return a closure ready for execution
     */
    TaskMethodClosure getClosure();
}
