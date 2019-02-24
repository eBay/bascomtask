package com.ebay.bascomtask.main;

class Binding {
    private final DataFlowSource.Instance source;
    private boolean readyToFire = false;
    private TaskMethodClosure closure = null;
    private Object output = null;
    
    //Binding(TaskMethodClosure closure, Object output) {
    Binding(DataFlowSource.Instance source) {
        this.source = source;
    }
    void setReadyToFire() {
        this.readyToFire = true;
    }
    boolean isReadyToFire() {
        return readyToFire;
    }
    
    DataFlowSource.Instance getSource() {
        return source;
    }
    
    void setValue(TaskMethodClosure closure, Object output) {
        this.closure = closure;
        this.output = output;
    }
    
    TaskMethodClosure getClosure() {
        return closure;
    }
    
    Object getOutput() {
        return output;
    }
}
