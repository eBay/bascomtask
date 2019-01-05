package com.ebay.bascomtask.main;

class Binding {
    final TaskMethodClosure closure;
    final Object output;
    Binding(TaskMethodClosure closure, Object output) {
        this.closure = closure;
        this.output = output;
    }
}
