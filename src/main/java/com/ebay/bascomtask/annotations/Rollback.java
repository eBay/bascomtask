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
package com.ebay.bascomtask.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method to be executed after normal execution {@link Work} or {@link PassThru}, 
 * when a downstream task has thrown an exception. When executed, all dependent downstream
 * tasks will themselves already have been rolled back.
 * 
 * @author brendanmccarthy
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface Rollback {
    /**
     * Equivalent of {@link Work#light()}
     * 
     * @return true iff the associated method is 'light'
     */
    public boolean light() default false;
}
