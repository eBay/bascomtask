package com.ebay.bascomtask.main;

class Fired {
    private final DataFlowSource.Instance source;
    private final TaskMethodClosure closure;
    
    Fired(DataFlowSource.Instance source, TaskMethodClosure closure) {
        this.source = source;
        this.closure = closure;
    }
    
    DataFlowSource.Instance getSource() {
        return source;
    }
    TaskMethodClosure getClosure() {
        return closure;
    }
}
