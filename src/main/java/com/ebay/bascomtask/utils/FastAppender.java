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
package com.ebay.bascomtask.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fast append-only list (though we don't call it a list since it doesn't implement standard List interface). 
 * For read times within its pre-allocated optimization range, 30% faster than ArrayList but more than more 
 * than 20x faster than a synchronized ArrayList while still remaining threadsafe. 
 * 
 * @param <T> type of list element
 */
public class FastAppender<T> {
    private final T[] fixed;  // Non-synchronized access happens here
    private volatile int lastFixedWrite = -1;  // which index was last successfully written to
    private int nvw = -1; // non-volatile lastFixedWrite for faster test on read, may lag lastFixedWrite
    private final AtomicInteger size = new AtomicInteger(0);
    private List<T> overflow = null;  // Synchronized access happens here
    
    /**
     * Creates an array using a supplied fixed storage array. All speed advantages only happen with
     * accesses that fall within the range of this array. The parameter is an array rather than a
     * integer size because we cannot create an array of parameterized type.
     * @param fixed array for storage -- initial values don't matter and should NOT be shared
     */
    public FastAppender(T[]fixed) {
        this.fixed = fixed;
    }
    
    public int size() {
        return size.get();
    }

    public void add(T t) {
        final int pos = size.incrementAndGet() - 1;
        if (pos < fixed.length) {
            waitup(pos);
            fixed[pos] = t;
            lastFixedWrite = pos;
            nvw = lastFixedWrite;
        }
        else if (pos==fixed.length) {
            synchronized (this) {
                overflow = new ArrayList<>();
                overflow.add(t);
                notifyAll();  // any thread waiting below
            }
        }
        else { // pos > fixed.length
            if (overflow==null) {
                // We've jumped ahead of the thread that's writing overflow in the pos==fixed.length case above
                synchronized(this) {
                    try {
                        wait();
                    }
                    catch (InterruptedException e) {
                        throw new RuntimeException("Internal sync error",e);
                    }
                }
            }
            synchronized(overflow) {
                overflow.add(t);
            }
        }
    }
    
    private void waitup(int index) {
        int slot = index-1;
        while (slot > nvw) { // Check non-volatile variable first because it's faster
            // lastFixedWrite may be greater (will never be less) than nvw, so check it here
            if (slot < lastFixedWrite) {
                break;
            }
            // If a thread writes a higher element jumps ahead of a thread writing a lower element,
            // sleep a bit (this should be rare).
            checkUpperBound(index);
            try {
                Thread.sleep(1);
            }
            catch (InterruptedException e) {
                throw new RuntimeException("Unexpected interrupt",e);
            }
        }
    }
    
    public T get(int index) {
        if (index < fixed.length) {
            waitup(index);
            return fixed[index];
        }
        else if (overflow==null) {
            checkUpperBound(index);
            return null; // Shouldn't happen
        }
        else synchronized (overflow) {
            int x = index-fixed.length;
            return overflow.get(x);
        }
    }
    
    private void checkUpperBound(int index) {
        int sz = size.get();
        if (index >= size.get()) {
            throw new ArrayIndexOutOfBoundsException("index " + index + " greater than size " + sz);
        }
    }
    
    /**
     * Creates a new copy of this list.
     * @return
     */
    public ArrayList<T> toList() {
        ArrayList<T> list = null;
        int sz = size();
        list = new ArrayList<>(sz);
        for (int i=0; i<sz; i++) {
            list.add(get(i));
        }
        return list;
    }
}
