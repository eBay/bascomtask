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

/**
 * Task execution hook points. Implementations of this task can be added for all Orchestrators
 * through {@link GlobalOrchestratorConfig} or locally to any individual {@link Orchestrator}. Several built-in versions are
 * also available.
 *
 * @author Brendan McCarthy
 */
public interface TaskRunner {

    /**
     * Called before {@link #executeTaskMethod(TaskRun, Thread, Object)}, but in the parent thread.
     * If there is no immediate spawning then it will be the same thread. The returned object will
     * simply be passed to executeTaskMethod and is otherwise not read or modified by the framework.
     *
     * @param taskRun that will also be passed to executeTaskMethod
     * @return a possibly-null object to pass to executeTaskMethod
     */
    Object before(TaskRun taskRun);

    /**
     * Task invocation -- an implementation should invoke {@link com.ebay.bascomtask.core.TaskRun#run()}
     * to complete the invocation (unless there is some reason to prevent it, but then the implementation
     * must take responsibility to ensure the all CompletableFuture return values are completed). A typical
     * subclass implementation would be to simply surround the call, e.g.:
     * <pre>
     *     public Object executeTaskMethod(TaskRun taskRun, Thread parentThread, Object fromBefore) {
     *         LOG.info("STARTED " + taskRun.getTaskPlusMethodName());
     *         try {
     *             taskRun.run()
     *         } finally {
     *             LOG.info("ENDED " + taskRun.getTaskPlusMethodName());
     *         }
     *     }
     * </pre>
     *
     * @param taskRun      to execute
     * @param parentThread of spawned task, which may be same as Thread.currentThread()
     * @param fromBefore   value returned from {@link #before(TaskRun)}
     * @return return value from task invocation
     */
    Object executeTaskMethod(TaskRun taskRun, Thread parentThread, Object fromBefore);

    // TODO expose exceptions to handler

    /**
     * Called once any CompletableFuture return result completes, which may be before the call to
     * TaskRun.run() or after if a method returns an incomplete CompletableFuture.
     *
     * <p>The provided doneOnExit parameter indicates whether a CompletableFuture return value had isDone()==true
     * when the method completed. This should not be taken as very close but not exactly precise, since there are
     * very small time intervals involved in the steps behind making that determination.
     *
     * @param taskRun    that was executed
     * @param fromBefore value returned from {@link #before(TaskRun)}
     * @param doneOnExit false iff a CompletableFuture was returned and it was not done when the method exited
     */
    void onComplete(TaskRun taskRun, Object fromBefore, boolean doneOnExit);
}
