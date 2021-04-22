/*-**********************************************************************
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
package com.ebay.bascomtask.core;

/**
 * Base class for function tasks.
 *
 * @param <RETURNTYPE> of function, which may be Void
 * @param <THIS> Leaf class
 * @author bremccarthy
 */
abstract class BaseFnTask<RETURNTYPE,THIS extends BaseFnTask<RETURNTYPE,THIS>> extends Binding<RETURNTYPE> /*implements SupplierTask<RETURNTYPE>*/ {
    private String name = null;

    public BaseFnTask(Engine engine) {
        super(engine);
    }

    /*@Override*/
    public THIS name(String name) {
        this.name = name;
        return (THIS)this;
    }

    @Override
    String doGetExecutionName() {
        if (name == null) {
            return "FunctionTask";
        } else {
            return name;
        }
    }

    @Override
    public TaskInterface<?> getTask() {
        return null;
    }

    @Override
    public void formatActualSignature(StringBuilder sb) {

    }

    @Override
    protected Object invokeTaskMethod() {
        throw new RuntimeException("Invalid internal state for function task " + this);
    }
}
