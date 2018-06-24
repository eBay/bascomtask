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
package com.ebay.bascomtask.config;

import com.ebay.bascomtask.main.ITaskMethodClosure;

/**
 * Interceptor for POJO task method calls.
 * @author brendanmccarthy
 */
public interface ITaskInterceptor {
	
	/**
	 * Called when a task method is ready to be invoked. Implementations should call execute() 
	 * on the supplied object to actually make the call. Such an implementation should also be
	 * prepared to catch and rethrow -- or use finally block -- any exceptions, if they want 
	 * to implement wrapping behavior (e.g. doing extra logging on entry and exit).
	 * @param closure to be invoked
	 * @return result from taskMethod call (if invoked task method is void, this value == true)
	 */
	boolean invokeTaskMethod(ITaskMethodClosure closure);
}



