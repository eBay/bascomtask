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
package com.ebay.bascomtask.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * Wraps user POJO tasks and is returned by calls to {@link Orchestrator#task(TaskInterface)}. Propagates task method
 * calls to the user POJO task and ensures that CompletableFutures returned from those tasks is wrapped with a
 * a {@link BascomTaskFuture} and {@link Binding} that together provide various bookkeeping responsibilities for
 * task dependency resolution.
 *
 * @author Brendan McCarthy
 */
class TaskWrapper<T> implements InvocationHandler {
    private final Engine engine;
    private final T original;
    private final TaskInterface<T> target; // same instance as original, but needed to call TaskInterface ops
    private String name = null;
    private boolean light = false;
    private boolean runSpawned = false;

    public TaskWrapper(Engine engine, T original, TaskInterface<T> target) {
        this.engine = engine;
        this.original = original;
        this.target = target;
    }

    public String getName() {
        if (name == null) return target.getName();
        return name;
    }

    public boolean isLight() {
        return light;
    }

    public boolean isRunSpawned() {
        return runSpawned;
    }

    public Object invoke(Object proxy, Method method, Object[] args)
            throws IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {
        String nm = method.getName();
        switch (nm) {
            case "hashCode": return original.hashCode();
            case "equals": return this == args[0];
            case "toString": return "TaskWrapper[" + original + "]";
            default: return fakeTaskMethod(proxy, method, args, nm);
        }
    }

    private Object fakeTaskMethod(Object proxy, Method method, Object[] args, String targetMethodName)
            throws IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {

        if ("name".equals(targetMethodName)) {
            name = args[0].toString();
            return proxy;
        }
        else if ("getName".equals(targetMethodName)) {
            return getName();
        }
        else if ("light".equals(targetMethodName)) {
            this.runSpawned = false;
            this.light = true;
            return proxy;
        }
        else if ("runSpawned".equals(targetMethodName)) {
            this.light = false;
            this.runSpawned = true;
            return proxy;
        }

        if (method.getReturnType().equals(Void.TYPE) || !CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
            // Execute immediately if not return a CompletableFuture
            return method.invoke(original,args);
        } else {
            Binding<T> binding = new ReflectionBinding<>(engine,this,original,method,args);
            return binding.getOutput();
        }
    }
}
