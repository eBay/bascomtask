package com.ebay.bascomtask.main;

/**
 * A call wrapped with its actual parameters.
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
	}
	
	Call.Instance getCallInstance() {
		return callInstance;
	}
	
	private boolean ready() {
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

	Call.Instance.Firing invoke(Orchestrator orc, String context, boolean fire) {
		if (!ready()) {
			throw new RuntimeException("Invocation not ready: " + this);
		}
		called = true;
		return callInstance.invoke(orc,context,args,fire);
	}
}
