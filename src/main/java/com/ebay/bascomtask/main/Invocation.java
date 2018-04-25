package com.ebay.bascomtask.main;

/**
 * A call with its formal parameters that knows when it is ready to be called.
 * @author brendanmccarthy
 */
class Invocation {

	private final Call.Instance callInstance;
	private final Object[] args;
	private boolean called = false;
	
	private static Object[] EMPTY_ARGS = new Object[0];
	
	Invocation(Call.Instance callInstance, Object[] args) {
		this.callInstance = callInstance;
		if (args==null) {
			this.args = null;
		}
		else {
			this.args = new Object[args.length];
			System.arraycopy(args,0,this.args,0,args.length);
		}
	}
	
	@Override
	public String toString() {
		if (callInstance == null) {
			return "<<no call instance>>";
		}
		else {
			String what = called?"called@":ready()?"ready@":"not-ready@";
			return what + callInstance.formatState();
		}
		/*
		String cn = "???";
		String mn = "?";
		if (callInstance != null) {
			Call call = callInstance.getCall();
			cn = call.getTask().getName();
			mn = call.getMethodName();
		}
		StringBuilder sb = new StringBuilder();
		sb.append(called?"called@":ready()?"ready@":"not-ready@");
		sb.append(cn);
		sb.append('.');
		sb.append(mn);
		sb.append('(');
		if (args==null) {
			sb.append("???");
		}
		else for (int i=0; i< args.length; i++) {
			if (i>0) sb.append(',');
			sb.append(args[i]==null ? '-' : '+');
			if (callInstance != null) {
				sb.append(callInstance.paramInstances[i].getTypeName());
			}
		}
		sb.append(')');
		return sb.toString();
		*/
	}
	
	Call.Instance getCallInstance() {
		return callInstance;
	}
	
	boolean ready() {
		if (called) return false;
		if (args == null) return false;
		for (Object next: args) {
			if (next==null) return false;
		}
		return true;
	}
	
	Object[] copyArgs() {
		if (args==EMPTY_ARGS) {
			return args;
		}
		else {
			Object[] copy = new Object[args.length];
			System.arraycopy(args,0,copy,0,args.length);
			return copy;
		}
	}

	boolean invoke(Orchestrator orc, String context) {
		if (ready()) {
			called = true;
			return callInstance.invoke(orc,context,args);
		}
		return false;
	}
}
