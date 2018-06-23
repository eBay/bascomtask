package com.ebay.bascomtask.main;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReflectionClosure implements ITaskMethodClosure {
	static final Logger LOG = LoggerFactory.getLogger(ReflectionClosure.class);    
    
    private final Call.Instance callInstance;
    private final Method method;
    private final Object[] args;
    private final String context;
    private final String kind;
    private long durationMs;
    private long durationNs;

    ReflectionClosure(final Call.Instance callInstance, final Method method, final Object[] args, final String context, final String kind) {
        this.callInstance = callInstance;
        this.method = method;
        this.args = args;
        this.context = context;
        this.kind = kind;
    }

    @Override
    public String getTaskName() {
        return callInstance.taskInstance.getName();
    }

    @Override
    public String getMethodName() {
        return method==null ? null : method.getName();
    }

    @Override
    public String getMethodFormalSignature() {
        return callInstance.getCall().signature();
    }

    @Override
    public String getMethodActualSignature() {
        StringBuilder sb = new StringBuilder();
        sb.append(getMethodName());
        boolean needsComma = false;
        sb.append('(');
        for (int i=0; i<args.length; i++) {
            if (needsComma) sb.append(',');
            needsComma = false;
            sb.append(args[i].toString()); // never null
        }
        sb.append(')');
        return sb.toString();
    }

    @Override
    public Object getTargetPojoTask() {
        return callInstance.taskInstance.targetPojo;
    }
    
    @Override
    public Object[] getMethodBindings() {
        return args;
    }

    @Override
    public boolean executeTaskMethod() {
        boolean returnValue = true;
        long startMs = System.currentTimeMillis();
        long startNs = System.nanoTime();
        String msg = null;
        TaskThreadStat threadStat = callInstance.taskInstance.orc.getThreadStatForCurrentThread();
        Object targetPojo = getTargetPojoTask();
        try {
            threadStat.setActive(true);
            LOG.debug("Invoking {} {} on {}",context,kind,targetPojo);
            Object methodResult = method.invoke(targetPojo, (Object[])args);
            if (Boolean.FALSE.equals(methodResult)) {
                returnValue = false;
            }
        }
        catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (target instanceof RuntimeException) {
                throw (RuntimeException)target;
            }
            throw new RuntimeException(target);
        }
        catch (Exception e) {
            msg = "Could not invoke " + context + " task " + kind + " " + getMethodFormalSignature() + " : " + e.getMessage();
            throw new RuntimeException(msg);
        }
        finally {
            threadStat.setActive(false);
            durationMs = System.currentTimeMillis() - startMs;
            durationNs = System.nanoTime() - startNs;
            if (LOG.isDebugEnabled()) {
                String rez = msg==null ? "success" : msg;
                LOG.debug("Completed {} {} on {} in {}ms result: {}",
                        context,kind,targetPojo,durationMs,rez);
            }
        }
        return returnValue;
    }

    @Override
    public long getDurationMs() {
        return durationMs;
    }

    @Override
    public long getDurationNs() {
        return durationNs;
    }
}
