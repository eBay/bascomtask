package com.ebay.bascomtask.main;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ebay.bascomtask.main.Call.Param;

abstract class DataFlowSource {
    
    /**
     * Class of POJO.
     */
    final Class<?> producesClass;
    
    /**
     * De-duped set of supers+interface tasks, including taskClass but excluding Object.class
     */
    final Class<?>[] ancestry;
    
    abstract String getShortName();
    abstract Task getTask();
    abstract Object chooseOutput(Object targetPojo, Object methodResult);

    DataFlowSource(Class<?> producesClass) {
        this.producesClass = producesClass;
        Set<Class<?>> ancestrySet = new HashSet<>();
        computeAncestors(producesClass,ancestrySet);
        this.ancestry = new Class<?>[ancestrySet.size()];
        ancestrySet.toArray(ancestry);
    }

    private void computeAncestors(Class<?> clazz, Set<Class<?>> set) {
        if (clazz != null && clazz != Object.class && clazz != Void.TYPE) {
            set.add(clazz);
            for (Class<?> next: clazz.getInterfaces()) {
                computeAncestors(next,set);
            }
            clazz = clazz.getSuperclass();
            computeAncestors(clazz,set);
        }
    }

    abstract class Instance extends Completable {
        
        private int orderAddedIndex = -1;
        
        int getOrderAddedIndex() {
            return orderAddedIndex;
        }
        
        void setOrderAddedIndex(int ix) {
            orderAddedIndex = ix;
        }
        
        DataFlowSource getSource() {return DataFlowSource.this;}
        
        abstract String getShortName();
        abstract Task.Instance getTaskInstance();
        abstract Iterable<Call.Instance> calls();
        
        /**
         * All parameters of all calls that have the type of our targetTask
         */
        final List<Param.Instance> backList = new ArrayList<>();

        /**
         * Allows subclasses to optionally specify, in place of this, which source to be used 
         * in calculating completion thresholds.
         * @return instance to be used in this calculation
         */
        Instance getCompletableSource() {
            return this;
        }
    }
}
