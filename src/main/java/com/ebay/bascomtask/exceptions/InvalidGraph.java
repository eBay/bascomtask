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
 * Conditions that occur prior to any task being processed.
 * @author brendanmccarthy
  */
@SuppressWarnings("serial")
public class InvalidGraph extends RuntimeException {

	public InvalidGraph(String message) {
		super(message);
	}

	/**
	 * A task cannot be executed because it has no call with all paramaters added.
	 * @author brendanmccarthy
	 */
	public static class MissingDependents extends InvalidTask {
		public MissingDependents(String message) {
			super(message);
		}
	}

	/**
	 * A circular reference has been detected in the graph.
	 * @author brendanmccarthy
	 */
	public static class Circular extends InvalidTask {
		public Circular(String message) {
			super(message);
		}
	}

	/**
	 * A task has more than one method that can fire.
	 * @author brendanmccarthy
	 */
	public static class MultiMethod extends InvalidTask {
		public MultiMethod(String message) {
			super(message);
		}
	}
}
