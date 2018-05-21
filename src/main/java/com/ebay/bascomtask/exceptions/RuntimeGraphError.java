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
package com.ebay.bascomtask.exceptions;

/**
 * Generated during Orchestrator execution. Some tasks may have completed
 * and some may still be in progress.
 * @author brendanmccarthy
  */
@SuppressWarnings("serial")
public class RuntimeGraphError extends RuntimeException {

	public RuntimeGraphError(String message) {
		super(message);
	}

	public RuntimeGraphError(String message, Exception e) {
		super(message,e);
	}

	public static class Timeout extends InvalidTask {
		public Timeout(long ms) {
			super("Timed out after " + ms + "ms");
		}
	}
}
