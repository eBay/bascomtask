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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ebay.bascomtask.config.ITaskClosureGenerator;

/**
 * Wraps an invocation of a task method call and its result. Subclasses can
 * override key methods to customize invocation behavior.
 * 
 * @author brendanmccarthy
 * @see #prepareTaskMethod()
 * @see #executeTaskMethod()
 * @see #getClosure()
 */
public class TaskMethodClosure implements ITaskClosureGenerator {

    static final Logger LOG = LoggerFactory.getLogger(TaskMethodClosure.class);

    private TaskMethodClosure parent = null;

    private Call.Instance callInstance;
    private Object[] args;

    private Object pojoTargetTask;
    private Object output;

    private String context;
    private String kind;

    private long durationMs;
    private long durationNs;

    private boolean called = false;

    private static Object[] EMPTY_ARGS = new Object[0];
    
    /**
     * The closure of one of the incoming parameters for this call, if any. If more than one, the one that took the longest
     */
    private TaskMethodClosure longestIncoming = null;
    
    @Override
    public String toString() {
        if (callInstance == null) {
            return "<<no call instance>>";
        }
        else {
            String what = called ? "called@" : ready() ? "ready@" : "not-ready@";
            return what + callInstance.formatState();
        }
    }
    
    Object getOutput() {
        return output;
    }

    TaskMethodClosure getParent() {
        return parent;
    }

    void setParent(TaskMethodClosure parent) {
        this.parent = parent;
    }

    Call.Instance getCallInstance() {
        return callInstance;
    }

    private boolean ready() {
        if (called)
            return false;
        if (args == null)
            return false;
        for (Object next : args) {
            if (next == null)
                return false;
        }
        return true;
    }

    Object[] copyArgs() {
        if (args == EMPTY_ARGS) {
            return args;
        }
        else {
            Object[] copy = new Object[args.length];
            System.arraycopy(args,0,copy,0,args.length);
            return copy;
        }
    }

    /**
     * Alternative to
     * {@link #initCall(com.ebay.bascomtask.main.Call.Instance, Object[])} that
     * simply installs its (injected) task instance without actually making a
     * call.
     * 
     * @param taskInstance
     */
    void initCall(Task.Instance taskInstance) {
        this.pojoTargetTask = taskInstance;
    }

    /**
     * In the normal (non-injection) case, a call instance is invoked with the
     * supplied args. This must be called before
     * {@link #invoke(Orchestrator, String, boolean)}.
     * 
     * @param callInstance
     * @param args
     * @param longestIncoming 
     */
    void initCall(/*DataFlowSource.Instance source, */final Call.Instance callInstance, final Object[] args, TaskMethodClosure longestIncoming) {
        //this.dataFlowSource = source;
        this.callInstance = callInstance;
        this.pojoTargetTask = callInstance.taskInstance.targetPojo;
        if (longestIncoming != null) {
            // Null-method closures reflect POJOs added to the graph but not executed. Avoid recording them
            // because they always produce zero-valued timing results and just add noise to the profiling graph.
            if (longestIncoming.getCallInstance().getCall().getMethod() != null) {
                this.longestIncoming = longestIncoming;    
            }
        }
        if (args == null) {
            this.args = null;
        }
        else {
            this.args = new Object[args.length];
            System.arraycopy(args,0,this.args,0,args.length);
        }
    }

    public String getTaskName() {
        return callInstance.taskInstance.getName();
    }

    public String getMethodName() {
        return callInstance.getCall().getMethodName();
    }

    public String getMethodFormalSignature() {
        return callInstance.getCall().signature();
    }

    public String getMethodActualSignature() {
        StringBuilder sb = new StringBuilder();
        sb.append(getMethodName());
        boolean needsComma = false;
        sb.append('(');
        for (int i = 0; i < args.length; i++) {
            if (needsComma)
                sb.append(',');
            needsComma = false;
            sb.append(args[i].toString()); // never null
        }
        sb.append(')');
        return sb.toString();
    }

    public Object getTargetPojoTask() {
        return pojoTargetTask;
    }

    /**
     * Returns the number of actual arguments on entry to a task method invocation.
     * @return number of arguments
     */
    public int getNumberOfActualArguments() {
        return args==null ? 0 : args.length;
    }
    
    /**
     * Gets the specified actual argument on entry to a task method invocation.
     * Assumes the caller has verified that the provided index is valid.
     * @param index of argument
     * @see #getNumberOfActualArguments()
     * @return value of argument
     */
    public Object getActualArgument(int index) {
        return args[index];
    }
    
    /**
     * Replaces the actual argument prior to task method invocation. This is
     * provided for interceptors that want to change arguments values.
     * Assumes the caller has verified that the provided index is valid.
     * @param index of argument
     * @see #getNumberOfActualArguments()
     * @param v new value to set
     */
    public void setActualArgument(int index, Object v) {
        args[index] = v;
    }

    public long getDurationMs() {
        return durationMs;
    }

    /**
     * Usually set internally. Visible for test.
     * @param durationMs
     */
    void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public long getDurationNs() {
        return durationNs;
    }

    /**
     * Invokes the Java task method conditionally.
     * 
     * @param orc
     * @param context string for debug messages
     * @return result from target method
     */
    void invoke(Orchestrator orc, String context) {
        if (!ready()) {
            throw new RuntimeException("TaskMethodClosure not ready: " + this);
        }
        Method method = callInstance.getCall().getMethod();
        if (method != null) {
            prepare();
        }
        called = true;
        Task.Instance taskInstance = callInstance.taskInstance;
        String kind = taskInstance.taskMethodBehavior == Task.TaskMethodBehavior.WORK ? "@Work" : "@PassThru";

        callInstance.startOneCall();
        if (method != null) {
            this.context = context;
            this.kind = kind;
            // This call may safely generate an exception, which will be processed
            // further up the chain.
            output = executeTaskMethod();
            orc.validateProvided(taskInstance);
        }

        // For Scope.SEQUENTIAL, only one thread will be active at a time, so it
        // is safe for all threads to just reset this to false.
        callInstance.setReserve(false);
    }

    private boolean prepared = false;

    public void prepare() {
        if (!prepared) {
            prepared = true;
            prepareTaskMethod();
        }
    }

    /**
     * Called before {@link #executeTaskMethod()}. The default implementation
     * does nothing, and is provided only for the benefit of subclasses to
     * override.
     */
    protected void prepareTaskMethod() {
        // Do nothing by default
    }

    /**
     * Invokes the actual pojo task method.
     * <p>
     * Called after {@link #prepareTaskMethod()} by the thread that will be used
     * to invoke the method. This thread may or may not be the same as the one
     * in <code>prepareTaskMethod</code>. It will be different if the
     * orchestrator decided to run this task in a separate thread in order to
     * maximize parallelism.
     * <p>
     * This method can be overridden but this super method should be invoked
     * from the overridden method to actually perform the call.
     * 
     * @return Object of target method
     */
    protected Object executeTaskMethod() {
        Object methodResult = null;
        long startMs = System.currentTimeMillis();
        long startNs = System.nanoTime();
        String msg = null;
        TaskThreadStat threadStat = callInstance.taskInstance.orc.getThreadStatForCurrentThread();
        Object targetPojo = getTargetPojoTask();
        try {
            threadStat.setActive(true);
            LOG.debug("Invoking {} {} on {}",context,kind,targetPojo);
            Method method = callInstance.getCall().getMethod();
            methodResult = method.invoke(targetPojo,(Object[]) args);
        }
        catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (target instanceof RuntimeException) {
                throw (RuntimeException) target;
            }
            throw new RuntimeException(target);
        }
        catch (Exception e) {
            msg = "Could not invoke " + context + " task " + kind + " " + getMethodFormalSignature() + " : "
                    + e.getMessage();
            throw new RuntimeException(msg);
        }
        finally {
            threadStat.setActive(false);
            durationMs = System.currentTimeMillis() - startMs;
            durationNs = System.nanoTime() - startNs;
            if (LOG.isDebugEnabled()) {
                String rez = msg == null ? "success" : msg;
                int added = callInstance.getTaskInstance().getOrchestrator().getCountOfNewTasks();
                String am = added == 0 ? "" : (" + " + added + " task" + (added == 1 ? "" : "s") + " added");
                LOG.debug("Completed {} {} on {} in {}ms result: {}{}",context,kind,targetPojo,durationMs,rez,am);
            }
        }
        return methodResult;
    }

    /**
     * Returns a closure for nested (non-root, i.e. tasks that have to wait for
     * other tasks to finish) tasks. This implementation returns null indicating
     * that the closure should be retrieved from
     * {@link com.ebay.bascomtask.config.IBascomConfig#getExecutionHook(Orchestrator, String)}.getClosure().
     * Subclasses can override to provide different behavior for non-root vs.
     * root tasks, if desired. The difference with this method is that
     * subclasses can know what the parent closure is, if that matters.
     */
    @Override
    public TaskMethodClosure getClosure() {
        return null;
    }
    
    TaskMethodClosure getLongestIncoming() {
        return longestIncoming;
    }

    /**
     * Returns the execution duration of this call + the longest equivalent time of any incoming
     * parameter that fired to initiate this call.
     * @return
     */
    long getLongestDuration() {
        long result = durationMs;
        if (longestIncoming != null) {
            result += longestIncoming.getLongestDuration();
        }
        return result;
    }
    
    /**
     * Has completed and has no dependents?
     */
    private boolean isEndPath = false;

    public void setIsEndPath() {
        isEndPath = true;
    }
    
    /**
     * Indicates that this closure is a full path.
     * @return true iff completed and has no dependents.
     */
    public boolean isEndPath() {
        return isEndPath;
    }
}
