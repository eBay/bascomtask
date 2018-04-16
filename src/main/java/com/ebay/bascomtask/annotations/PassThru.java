package com.ebay.bascomtask.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a task method that may be executed when a task is added through
 * {@link com.ebay.bascomtask.main.Orchestrator.addPassThru(Object)}} rather 
 * than {@link com.ebay.bascomtask.main.Orchestrator.addWork(Object)}}.
 * A {@literal @}PassThru method is intended to capture behavior applicable when
 * a task's main {@literal @}Work method(s) should not be invoked. For example, it 
 * might simply provide some hardwired defaults, or retrieve data from one of 
 * its task arguments and pass it through with little or no change. Accordingly, a 
 * @{literal @}PassThru task is always considered 'light', i.e. it is expected 
 * to run quickly so no separate thread ever need be spawned for it.
 * @author brendanmccarthy
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface PassThru {
}
