package com.ebay.bascomtask.main;

public class SimpleLoadTest {

    private static void runFor(int times) throws Exception {
        for (int i=0; i<times; i++) {
            LoadTest.loadDiamond();
        }
    }
    
    public static void main(String[] args) throws Exception {
        runFor(100);
        long ns = System.nanoTime();
        runFor(10000);
        long diff = (System.nanoTime() - ns) / 1000000;
        System.out.println("Time: " + diff + "ms");
    }
}

