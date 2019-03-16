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
package com.ebay.bascomtask.main;

/**
 * Records the result of a DataFlowSource.Instance firing, with a version number that is
 * unique within an orchestrator. The uniqueness property always increments, with the
 * result that relative to any Fired one can traverse the set of all Fired instances in
 * that orchestrator that were fired earlier, even if other threads have in the meantime
 * added new Fired instances.
 * 
 * @author bremccarthy
 */
class Fired {
    private final DataFlowSource.Instance source;
    private final TaskMethodClosure closure;
    private final int version;
    
    Fired(Orchestrator orc, DataFlowSource.Instance source, TaskMethodClosure closure) {
        this.source = source;
        this.closure = closure;
        version = orc.genVersion();
    }
    
    DataFlowSource.Instance getSource() {
        return source;
    }
    
    TaskMethodClosure getClosure() {
        return closure;
    }
    
    int getVersion() {
        return version;
    }
}
