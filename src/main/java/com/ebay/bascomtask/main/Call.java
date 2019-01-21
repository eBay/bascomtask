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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ebay.bascomtask.annotations.Scope;

/**
 * A wrapper for a Java method on a task POJO, including parameter wrappers for
 * each parameter.
 * 
 * @author brendanmccarthy
 */
class Call extends DataFlowSource {

    static final Logger LOG = LoggerFactory.getLogger(Call.class);

    /**
     * A call has exactly one task
     */
    private final Task task;

    /**
     * Corresponding method on user POJO. This may be null, but only when this
     * call is used as a dummy call for tasks that have no task methods.
     */
    private final Method method;

    /**
     * Marks a task method that should be fast so should be executed directly
     * when encountered rather than spawning a new thread for it.
     */
    private boolean light;

    /**
     * Governs behavior when invoked multiple times.
     */
    private final Scope scope;

    /**
     * Formal parameters.
     */
    private List<Param> params = new ArrayList<>();

    private Map<Task, Param> hiddenParamMap = null;

    Task getTask() {
        return task;
    }

    int getNumberOfParams() {
        return params.size();
    }

    Method getMethod() {
        return method;
    }

    String getMethodName() {
        return method.getName();
    }
    

    @Override
    String getShortName() {
         return task.getShortName() + '.' + getMethodName();
    }
    
    @Override Object chooseOutput(Object targetPojo, Object methodResult) {
        return methodResult;
    }
    
    void add(Param param) {
        signature = null; // Force recompute
        params.add(param);
    }

    Instance genInstance(Task.Instance taskInstance) {
        return new Instance(taskInstance);
    }

    private static final int[] EMPTY_FREEZE = new int[0];

    /**
     * A representation of a {@link Call} for a Task.Instance. Collects
     * parameters in preparation for invoking the Call method. Note that
     * multiple method invocations may be made through given Call.Instance,
     * potentially at overlapping times, so it must be threadsafe.
     * 
     * @author brendanmccarthy
     */
    class Instance extends DataFlowSource.Instance implements Iterable<Param.Instance> {

        /**
         * Owner of this call
         */
        final Task.Instance taskInstance;

        /**
         * Parameter instances that correspond to param list in outer call
         */
        final Param.Instance[] paramInstances = new Param.Instance[params.size()];

        List<Param.Instance> hiddenParameters = null;

        /**
         * The positions of active parameters at the time of creation of this
         * instance. This allows for task instances that have already fired to
         * be replayed for this task when added dynamically (i.e. other tasks
         * were already started and may therefore have already fired prior to
         * this instance being added or even created).
         */
        final int[] startingFreeze = new int[params.size()];

        /**
         * Non-null if Scope.Sequential has been set, and if so this accumulates
         * calls while a task is active
         */
        private final ConcurrentLinkedDeque<Object[]> followCallArgs;

        /**
         * True iff a task method has been entered and not exited. Only used for
         * Scope.Sequential case, thus only one thread can set it.
         */
        private boolean reserved = false;

        Instance(Task.Instance taskInstance) {
            this.taskInstance = taskInstance;
            final int sz = params.size();
            for (int i = 0; i < sz; i++) {
                Param next = params.get(i);
                paramInstances[i] = next.new Instance(this);
            }
            if (scope == Scope.SEQUENTIAL) {
                followCallArgs = new ConcurrentLinkedDeque<>();
            }
            else {
                followCallArgs = null;
            }
        }

        @Override
        public String toString() {
            return formatState();
        }
        
        @Override
        String getShortName() {
            return taskInstance.getShortName() + '.' + getMethodName();
        }
        
        @Override
        public DataFlowSource.Instance getCompletableSource() {
            return taskInstance;
        }

        Call getCall() {
            return Call.this;
        }

        @Override
        Task.Instance getTaskInstance() {
            return taskInstance;
        }
        
        private class Itr implements Iterable<Instance>, Iterator<Instance> {
            private boolean accessed = true;  // XXX TBD/TODO remove this itr
            
            @Override
            public Iterator<Instance> iterator() {
                return this;
            }

            @Override
            public boolean hasNext() {
                return !accessed;
            }

            @Override
            public Instance next() {
                accessed = true;
                return Instance.this;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        }
        
        @Override
        Iterable<Call.Instance> calls() {
            return new Itr();
        }

        @Override
        Completable containingCompletable() {
            return taskInstance;
        }

        boolean isNoWait() {
            return !taskInstance.wait;
        }

        String formatState() {
            StringBuilder sb = new StringBuilder();
            sb.append(taskInstance.getName());
            sb.append('.');
            sb.append(format(method));
            sb.append('(');
            boolean first = true;
            for (Param.Instance next : paramInstances) {
                if (!first)
                    sb.append(',');
                first = false;
                sb.append(next.toString());
            }
            sb.append(')');
            if (hiddenParameters != null) {
                sb.append("&[");
                first = true;
                for (Param.Instance next : hiddenParameters) {
                    if (!first)
                        sb.append(',');
                    first = false;
                    String nm;
                    if (next.incoming.size()==1) { // It should be this, but checking anyway
                        nm = next.incoming.get(0).getShortName();
                    }
                    else {
                        nm = next.toString();  // Not expected, but return best alternative
                    }
                    sb.append(nm);
                    
                }
                sb.append("]");
            }
            sb.append(' ');
            sb.append(completionSay());
            return sb.toString();
        }

        /**
         * Adds a hidden (i.e. not part of the formal argument list) parameter.
         * These are created as needed then cached.
         * 
         * @param task type for new parameter
         * @return newly-created parameter
         */
        Param.Instance addHiddenParameter(Task task) {
            if (hiddenParamMap == null) {
                hiddenParamMap = new HashMap<>();
            }
            Param param = hiddenParamMap.get(task);
            if (param == null) {
                param = new Param(task,-1,false,false);
                hiddenParamMap.put(task,param);
            }
            Param.Instance paramInstance = param.new Instance(this);
            if (hiddenParameters == null) {
                hiddenParameters = new ArrayList<>();
            }
            hiddenParameters.add(paramInstance);
            return paramInstance;
        }

        /**
         * Iterates over all parameters, actual and hidden
         */
        public Iterator<Param.Instance> iterator() {
            return new Iterator<Param.Instance>() {
                private boolean onHidden = false;
                private int pos;

                @Override
                public boolean hasNext() {
                    if (onHidden) {
                        return hiddenParameters != null && pos < hiddenParameters.size();
                    }
                    if (pos >= paramInstances.length) {
                        onHidden = true;
                        pos = 0;
                        return hasNext();
                    }
                    return true;
                }

                @Override
                public Param.Instance next() {
                    if (onHidden) {
                        return hiddenParameters.get(pos++);
                    }
                    return paramInstances[pos++];
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("Removing parameters");
                }
            };
        }
        
        private TaskMethodClosure longestIncoming = null;

        /**
         * Remembers the given closure if it is longer than anything else we've seen so far.
         * @return
         */
        void scoreIncoming(TaskMethodClosure closure) {
            if (closure != null) {
                if (longestIncoming==null || closure.getDurationMs() > longestIncoming.getDurationMs()) {
                    longestIncoming = closure;
                }
            }
        }

        /**
         * Adds a binding for a parameter, possibly resulting in POJO task
         * methods being invoked if all parameters for any dependent method are
         * available. The sequencing here is non-trivial due to allowance for
         * multiple instance matching for each parameter.
         * <p>
         * Although all tasks could be spawned as necessary, one task
         * TaskMethodClosure is returned for the calling thread since it will be
         * available to do work and it would be inefficient to spawn a new
         * thread and then have the calling thread just wait(). The caller may
         * already have such a pending invocation which they can provide as the
         * last argument, which is either returned as-is or a replacement is
         * returned; if the latter then the input TaskMethodClosure is spawned
         * in a new thread.
         * 
         * @param orc
         * @param context
         * @param userTaskInstance instance of user's task
         * @param parameterIndex index of parameter that is being bound
         * @param pendingClosure current invocation, null if none
         * @return same or possibly new invocation for the calling thread to invoke
         */
        TaskMethodClosure bind(Orchestrator orc, String context, TaskMethodClosure firing,
                Binding binding, Param.Instance firingParameter, TaskMethodClosure pendingClosure) {
            int[] freeze;
            int ordinalOfFiringParameter;
            if (firingParameter == null) { // A root task call, i.e. one with no task parameters?
                freeze = EMPTY_FREEZE;
                ordinalOfFiringParameter = -1;
            }
            else {
                // Obtain a unique set of parameter indexes within the synchronized block such that no
                // other thread would get the same set. Once these are set, the actual execution can
                // safely proceed outside the synchronized block.
                synchronized (this) {
                    scoreIncoming(firing);
                    ordinalOfFiringParameter = firingParameter.bindings.size();
                    if (firingParameter.getParam().isOrdered) {
                        ordinalOfFiringParameter = firing.getCallInstance().getTaskInstance().getIndexInType();
                        Task.Instance ti = firing.getCallInstance().getTaskInstance();
                        System.out.println("-- TI="+ti+", ix="+ti.getIndexInType());
                        firingParameter.setActual(binding,ordinalOfFiringParameter);
                        //firing = firingParameter.bindings.get(0).closure;
                        firing = binding.closure;
                        ordinalOfFiringParameter = 0;
                    }
                    else {
                        firingParameter.addActual(binding);
                    }
                    for (Param.Instance next : paramInstances) {
                        if (!next.ready()) {
                            return pendingClosure; // If not all parameters ready (non-list params must have
                                                   // at least one binding), not ready to execute call
                        }
                    }
                    if (hiddenParameters != null) {
                        for (Param.Instance next : hiddenParameters) {
                            if (!next.ready()) {
                                return pendingClosure; // If not all hidden parameters ready, not ready to execute call
                            }
                        }
                    }
                    freeze = new int[paramInstances.length];
                    for (int i = 0; i < paramInstances.length; i++) {
                        freeze[i] = paramInstances[i].bindings.size();  
                    }
                }
            }

            return crossInvoke(firing,pendingClosure,freeze,firingParameter,ordinalOfFiringParameter,orc,context);
        }

        /**
         * Invokes POJO task method 1 or more times with the cross-product off
         * all parameters within the 'freeze' range, *except* for the the firing
         * parameter for which we don't include any of its sibling parameters.
         * 
         * @param pendingClosure the current invocation to be returned to the
         *            calling thread, null if none
         * @param freeze the max ordinal position of each parameter to include
         * @param firingParameterIndex which parameter fired that cause this
         *            method to be invoked
         * @param ordinalOfFiringParameter the ordinal position of the firing
         *            parameter within its Param.Instance.bindings
         * @param orc
         * @param context descriptive text for logging
         * @return an invocation to be invoked by caller, possibly null or
         *         possibly the input inv parameter unchanged
         */
        TaskMethodClosure crossInvoke(TaskMethodClosure firing, /*DataFlowSource.Instance source, Object output, */TaskMethodClosure pendingClosure, int[] freeze,
                Param.Instance firingParameter, int ordinalOfFiringParameter, Orchestrator orc, String context) {
            Object[] args = new Object[paramInstances.length];
            return crossInvoke(0,args,true,firing,pendingClosure,freeze,firingParameter,ordinalOfFiringParameter,orc,context,false);
        }

        /**
         * Invoked recursively for each parameter position, accumulating
         * parameter assignments in args, and performing the invocation when
         * (and if) all args are assigned.
         */
        private TaskMethodClosure crossInvoke(int px, Object[] args, boolean fire, TaskMethodClosure firing,
                /*DataFlowSource.Instance source, Object output, */TaskMethodClosure pendingClosure, int[] freeze, Param.Instance firingParameter,
                int ordinalOfFiringParameter, Orchestrator orc, String context, boolean immediate) {
            if (px == args.length) {
                TaskMethodClosure newInvocation = orc.getTaskMethodClosure(firing,this,args,longestIncoming); // makes a copy of args!
                if (!fire) {
                    orc.invokeAndFinish(newInvocation,"non-fire",false);
                }
                else if (light || immediate) {
                    orc.invokeAndFinish(newInvocation,"light",fire);
                }
                else if (isNoWait() && orc.isCallingThread()) {
                    // Don't assign main thread with tasks it should not wait for
                    orc.spawn(newInvocation);
                }
                else if (taskInstance.isFork()) {
                    // Spawn right away if taskInstance has been flagged this way
                    orc.spawn(newInvocation);
                }
                else if (!postPending(newInvocation)) {
                    if (pendingClosure != null) {
                        orc.spawn(pendingClosure);
                    }
                    return newInvocation;
                }
            }
            else {
                final Param.Instance paramAtIndex = paramInstances[px];
                if (paramAtIndex.getParam().isList) {
                    if (!paramAtIndex.ready()) {
                        return pendingClosure; // List arg not ready
                    }
                    args[px] = paramAtIndex.asListArg();
                    // Don't change 'fire' value -- args only contains values
                    // that have fired; may even be empty but fire anyway
                    pendingClosure = crossInvoke(px + 1,args,fire,firing,pendingClosure,freeze,firingParameter,
                            ordinalOfFiringParameter,orc,context,immediate);
                }
                else {
                    int from = 0;
                    int to = freeze[px];
                    if (paramAtIndex == firingParameter) {
                        if (paramAtIndex.getParam().isOrdered) {
                            to = paramAtIndex.bindings.size();
                            immediate = true;
                            if (pendingClosure != null) {
                                orc.spawn(pendingClosure);
                                pendingClosure = null;
                            }
                        }
                        else {
                            from = ordinalOfFiringParameter;
                            to = from+1;
                        }
                    }
                    for (int i = from; i < to; i++) {
                        if (paramAtIndex.bindings.get(i) == null) {
                            System.out.println("NOPE");
                        }
                        firing = paramAtIndex.bindings.get(i).closure;
                        pendingClosure = crossInvokeNext(px,args,fire,i,firing,pendingClosure,freeze,firingParameter,
                                ordinalOfFiringParameter,orc,context,immediate);
                    }
                }
            }
            return pendingClosure;
        }

        /**
         * Assigns an actual parameter value to the accumulating args array and
         * proceeds to the next parameter position to the right.
         * 
         * @param px
         * @param args
         * @param fire
         * @param bindingIndex
         * @param pendingClosure
         * @param freeze
         * @param firingParameterIndex
         * @param ordinalOfFiringParameter
         * @param orc
         * @param context
         * @return
         */
        private TaskMethodClosure crossInvokeNext(int px, Object[] args, boolean fire, int bindingIndex,
                TaskMethodClosure firing, TaskMethodClosure pendingClosure, int[] freeze,
                Param.Instance firingParameter, int ordinalOfFiringParameter, Orchestrator orc, String context, boolean immediate) {
            Param.Instance paramAtIndex = paramInstances[px];
            args[px] = paramAtIndex.bindings.get(bindingIndex).output;
            boolean fireAtLevel = fire; // && paramClosure.getReturned();
            return crossInvoke(px + 1,args,fireAtLevel,firing,pendingClosure,freeze,firingParameter,
                    ordinalOfFiringParameter,orc,context,immediate);
        }

        /**
         * If this is a Scope.SEQUENTIAL call and another thread is operating on
         * our method, queue the invocation for later execution. Also sets the
         * proper state so that later invocations of this method will behave
         * accordingly.
         * 
         * @param inv to (possibly) queue
         * @return true iff queued
         */
        private boolean postPending(TaskMethodClosure inv) {
            if (scope == Scope.SEQUENTIAL) {
                synchronized (this) {
                    if (reserved) {
                        followCallArgs.add(inv.copyArgs());
                        return true;
                    }
                    else {
                        reserved = true;
                    }
                }
            }
            return false;
        }

        void setReserve(boolean which) {
            reserved = which;
        }

        Object[] popSequential() {
            if (followCallArgs != null) {
                return followCallArgs.pollFirst();
            }
            return null;
        }
    }

    /**
     * Cache signature since does not change after params added
     */
    private String signature = null;

    Call(Task task, Method method, Scope scope, boolean light) {
        super(method==null?null:method.getReturnType());
        this.task = task;
        this.method = method;
        this.light = light;
        this.scope = scope;
    }

    @Override
    public String toString() {
        String sig = signature();
        return "Call " + sig;
    }

    static String format(Method method) {
        return method == null ? "<<no-method>>" : method.getName();
    }
    
    String signature() {
        if (signature==null) {
            signature = constructSignature();
        }
        return signature; 
    }
    
    private String constructSignature() {
        StringBuilder sb = new StringBuilder();
        sb.append(task==null?"???":task.getName());
        if (method != null) {
            // If no method, pojo was added without task method so just print task
            sb.append('.');
            sb.append(format(method));
            sb.append('(');
            boolean first = true;
            for (Param next : params) {
                if (!first)
                    sb.append(',');
                first = false;
                sb.append(next.getTypeName());
            }
            sb.append(')');
        }
        return sb.toString();
    }

    /**
     * A parameter of a POJO task method.
     */
    class Param {

        final DataFlowSource dataFlowSource;

        /**
         * Ordinal position of this parameter
         */
        final int paramaterPosition;

        /**
         * True iff List<X> rather than X
         */
        final boolean isList;
        
        /**
         * True if @Ordered
         */
        final boolean isOrdered;
        
        public boolean accumulate() {
            return isList || isOrdered;
        }
        
        class Instance {
            /**
             * The call which contains this parameter
             */
            final Call.Instance callInstance;

            /**
             * The actual arguments in proper order, all of which will be POJOs
             * added to the orchestrator as tasks
             */
            private final List<Binding> bindings = new ArrayList<>();
            
            private int countOfReadyParamters = 0;
            
            /**
             * The last parameter delivered -- to preserve @Ordered parameters, higher-indexed
             * items are not delivered even though they have been added to {@link #bindings}
             */
            //private int indexOfHighestDelivered = -1;

            /**
             * All tasks, auto-wired and explicit/hidden, that backlist to this
             * param instance.
             */
            final List<DataFlowSource.Instance> incoming = new ArrayList<>();

            /**
             * How we know, for list arguments, when all parameters are ready
             */
            private int threshold = 0;

            /**
             * Marks a parameter for which at least one explicit wiring has been
             * set.
             */
            private boolean explicitlyWired = false;

            Instance(Call.Instance callInstance) {
                this.callInstance = callInstance;
            }

            @Override
            public String toString() {
                return dataFlowSource.getShortName() + ':' + bindings.size() + '/' + threshold;
            }
            
            List<Object> asListArg() {
                List<Object> result = new ArrayList<>(bindings.size());
                for (Binding next: bindings) {
                    result.add(next.output);
                }
                return result;
            }

            boolean ready() {
                return accumulate() ? countOfReadyParamters >= threshold : countOfReadyParamters > 0;
            }

            void bumpThreshold() {
                this.threshold += 1;
            }

            int getThreshold() {
                return threshold;
            }

            Task getTask() {
                return dataFlowSource.getTask();
            }
            
            DataFlowSource getDataFlowSource() {
                return dataFlowSource;
            }

            Call.Instance getCall() {
                return callInstance;
            }

            Param getParam() {
                return Param.this;
            }

            String getTypeName() {
                return Param.this.getTypeName();
            }

            boolean isExplicitlyWired() {
                return explicitlyWired;
            }

            void setExplicitlyWired() {
                explicitlyWired = true;
            }
            
            void addActual(Binding binding) {
                bindings.add(binding);
                countOfReadyParamters++;
            }

            /**
             * Sets an actual parameter at the specified position, filling in
             * the list with nulls if necessary so that the list is big enough.
             * @param closure to set
             * @param pos in list
             */
            private void setActual(Binding binding, int pos) {
                for (int i = bindings.size(); i<=pos; i++) {
                    bindings.add(null);
                }
                bindings.set(pos,binding);
                countOfReadyParamters++;
            }

            /**
             * Adds a task to be executed before this one.
             * @param task to add before
             */
            void addBefore(ITask task) {
                for (Binding next: bindings) {
                    task.before(next.closure.getTargetPojoTask());
                }
            }
        }

        Param(DataFlowSource source, int parameterPosition, boolean isList, boolean ordered) {
            this.dataFlowSource = source;
            this.paramaterPosition = parameterPosition;
            this.isList = isList;
            this.isOrdered = ordered;
        }

        Call getCall() {
            return Call.this;
        }

        String getTypeName() {
            return dataFlowSource.getShortName();
        }

        @Override
        public String toString() {
            return "Param(" + dataFlowSource.getShortName() + ')';
        }
    }
}