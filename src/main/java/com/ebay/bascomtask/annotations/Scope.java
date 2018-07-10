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

/**
 * Specifies desired behavior when a task is fired more than once.
 * 
 * @author brendanmccarthy
 */
public enum Scope {
    /**
     * Calls are processed in parallel without constraint; assumes the task/call
     * is stateless or can manage multiple threads.
     */
    FREE,

    /**
     * Calls are delayed and processed on after the other; the task/call need
     * not be threadsafe.
     */
    SEQUENTIAL
}
