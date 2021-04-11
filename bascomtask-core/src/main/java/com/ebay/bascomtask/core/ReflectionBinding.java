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

import com.ebay.bascomtask.annotations.Light;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * Binding for a task with a method to be called through reflection. This is the standard case for user POJO tasks.
 *
 * @author Brendan McCarthy
 */
class ReflectionBinding<USERTASKTYPE, RETURNTYPE> extends Binding<RETURNTYPE> {
    private final TaskWrapper<USERTASKTYPE> taskWrapper;
    private final Object userTask;
    private final Method method;
    private final Object[] args;
    private final boolean light;
    private final boolean runSpawned;

    ReflectionBinding(Engine engine, TaskWrapper<USERTASKTYPE> taskWrapper, Object userTask, Method method, Object[] args) {
        super(engine);
        this.userTask = userTask;
        this.taskWrapper = taskWrapper;
        this.method = method;
        this.args = args;

        // Only one of these should be set -- that is also true in TaskWrapper
        // An explicit call on the task overrules a @Light annotation if present
        this.runSpawned = taskWrapper.isRunSpawned();
        this.light = taskWrapper.isLight()
                || (Utils.getAnnotation(userTask, method, Light.class) != null && !taskWrapper.explicitRunSpawn());

        if (args != null) {
            for (Object next : args) {
                if (next instanceof CompletableFuture) {
                    CompletableFuture<?> cf = (CompletableFuture<?>) next;
                    ensureWrapped(cf, true);
                }
            }
        }
    }

    @Override
    public boolean isLight() {
        return light;
    }

    @Override
    public boolean isRunSpawned() {
        return runSpawned;
    }

    @Override
    String doGetExecutionName() {
        String taskName = taskWrapper.getName();
        return taskName + "." + method.getName();
    }

    @Override
    public TaskInterface<?> getTask() {
        return (TaskInterface<?>) userTask;
    }

    @Override
    protected Object invokeTaskMethod() {
        try {
            // Don't require public access, especially because of poor JVM exception messages, e.g. failure to make
            // an interface public, when BT accessed as a library, can otherwise result in
            // IllegalAccessException ... cannot access a member of interface ... with modifiers "public abstract"
            method.setAccessible(true);
            return method.invoke(userTask, args);
        } catch (InvocationTargetException itx) {
            Throwable actual = itx.getCause();
            RuntimeException re;
            if (actual instanceof RuntimeException) {
                re = (RuntimeException) actual;
            } else {
                re = new RuntimeException(actual);
            }
            throw re;
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to invoke method", e);
        }
    }

    @Override
    public void formatActualSignature(StringBuilder sb) {
        Utils.formatFullSignature(sb, getTaskPlusMethodName(), args);
    }
}
