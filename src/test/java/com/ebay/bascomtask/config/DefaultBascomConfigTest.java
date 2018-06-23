package com.ebay.bascomtask.config;

import static org.junit.Assert.*;

import org.junit.Test;

import com.ebay.bascomtask.main.TaskThreadStat;

public class DefaultBascomConfigTest {
    
    private class TestTaskThreadStat extends TaskThreadStat {

        protected TestTaskThreadStat(int value, TaskThreadStat root) {
            super(null,value,value,root);
        }
    }
    
    private TaskThreadStat create(int...values) {
        return createAt(values.length-1,values);
    }
    
    private TaskThreadStat createAt(int ix, int...values) {
        TaskThreadStat root;
        if (ix > 0) {
            root = createAt(ix-1,values);
        }
        else {
            // Simulates the way orchestrator works, which creates a root with id==0
            root = new TestTaskThreadStat(0,null);
        }
        return new TestTaskThreadStat(values[ix],root);
    }
    
    private static final String PFX = DefaultBascomConfig.THREAD_ID_PREFIX;
    private static final String TID = "123";
    private static final String TNAME = "abc";
    private static final String FULL_NAME = PFX + TID + ':' + TNAME + '#';
    private static final String PART_NAME = PFX + TID + '#';
    

    @Test
    public void partName() {
        final int v1 = 11;
        TaskThreadStat threadStat = create(v1);
        String got = DefaultBascomConfig.constructThreadId(threadStat,TID,null,20);
        assertEquals(PART_NAME+v1,got);
    }
    
    @Test
    public void fullName() {
        final int v1 = 11;
        TaskThreadStat threadStat = create(v1);
        String got = DefaultBascomConfig.constructThreadId(threadStat,TID,TNAME,20);
        assertEquals(FULL_NAME+v1,got);
    }
    
    @Test
    public void twoDeep() {
        final int v1 = 11;
        final int v2 = 22;
        TaskThreadStat threadStat = create(v1,v2);
        String got = DefaultBascomConfig.constructThreadId(threadStat,TID,TNAME,20);
        assertEquals(FULL_NAME+v1+'.'+v2,got);
    }
    
    @Test
    public void threeDeepLong() {
        final int v1 = 11;
        final int v2 = 22;
        TaskThreadStat threadStat = create(v1,v2);
        String got = DefaultBascomConfig.constructThreadId(threadStat,TID,TNAME,12);
        assertEquals(FULL_NAME+".."+v2,got);
    }

    @Test
    public void onlyOneWithShortMax() {
        final int v1 = 11;
        TaskThreadStat threadStat = create(v1);
        String got = DefaultBascomConfig.constructThreadId(threadStat,TID,null,1);
        assertEquals(PART_NAME+v1,got);
    }
}
