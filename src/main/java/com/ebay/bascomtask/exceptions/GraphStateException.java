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
 * The orchestration graph ended up in a state where it could not complete.
 * @author brendanmccarthy
  */
@SuppressWarnings("serial")
public class GraphStateException extends RuntimeException {

	public GraphStateException(String message) {
		super(message);
	}

	public static class OrphanedTasks extends GraphStateException {
		public OrphanedTasks(String message) {
			super(message);
		}
	}
}
