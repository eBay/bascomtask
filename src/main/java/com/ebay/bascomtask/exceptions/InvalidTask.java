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
 * A task supplied to an archestrator cannot be processed.
 * @author brendanmccarthy
  */
@SuppressWarnings("serial")
public class InvalidTask extends RuntimeException {

	public InvalidTask(String message) {
		super(message);
	}

	public static class BadParam extends InvalidTask {
		public BadParam(String message) {
			super(message);
		}
	}
	
	public static class BadReturn extends InvalidTask {
		public BadReturn(String message) {
			super(message);
		}
	}

	public static class NotPublic extends InvalidTask {
		public NotPublic(String message) {
			super(message);
		}
	}

	public static class NameConflict extends InvalidTask {
		public NameConflict(String message) {
			super(message);
		}
	}
}
