package com.ebay.bascomtask.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a task method that may be executed when a task is added through
 * {@link com.ebay.bascomtask.main.Orchestrator.addWork(Object)}} rather 
 * than {@link com.ebay.bascomtask.main.Orchestrator.addPassThru(Object)}}.
 * A {@literal @}Work method implements the primary functionality of a task.
 * @author brendanmccarthy
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface Work {
	/**
	 * Normally task methods are executed in their own thread when the opportunity for
	 * parallel execution arises. Override that here to specify that task method is
	 * sufficiently fast that it would be quicker to execute directly rather than
	 * spawning it in a new thread.
	 * @return
	 */
	public boolean light() default false;
	
	/**
	 * Specifies alternative behavior when a method is invoked more than once, e.g.
	 * because there are multiple instances of one or more parameters.
	 * @return
	 */
	public Scope scope() default Scope.FREE;
}
