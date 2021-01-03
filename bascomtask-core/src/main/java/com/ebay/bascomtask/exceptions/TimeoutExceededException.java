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
 * When a timeout has been specified and exceeded. This is used by framework instead of
 * {@link java.util.concurrent.TimeoutException} because that is a checked exception and setting
 * the timeout in BascomTask is optional so it would tedious to force callers to handle it.
 *
 * @author Brendan McCarthy
 */
public class TimeoutExceededException extends RuntimeException {

    public TimeoutExceededException(String msg) {
        super(msg);
    }
}
