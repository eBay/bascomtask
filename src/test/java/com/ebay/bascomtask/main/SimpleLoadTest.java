package com.ebay.bascomtask.main;

public class SimpleLoadTest {

    public static void main(String[] args) throws Exception {
        long ns = System.nanoTime();
        for (int i=0; i<10000; i++) {
            LoadTest.loadDiamond();
        }
        long diff = (System.nanoTime() - ns) / 1000000;
        System.out.println("Time: " + diff + "ms");
    }
}

