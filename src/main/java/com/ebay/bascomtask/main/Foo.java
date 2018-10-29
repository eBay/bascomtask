package com.ebay.bascomtask.main;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Foo {
    
    private String[] strings = new String[1000];
    {
        List<String> c = new ArrayList<>();
        for (int i=0; i<strings.length; i++) {
            c.add("s" + i);
        }
        Collections.shuffle(c);
        strings = c.toArray(strings);
    }
    
    private void map(int count) {
        Map<String,String> map = new HashMap<>();
        for (int i=0; i< count; i++) {
            String s = strings[i];
            map.put(s,s);
        }
        for (int i=0; i<count; i++) {
            String s = strings[i];
            if (map.get(s)==null) {
                System.out.println("NO_WAY");
            }
        }
    }
    
    private void list(int count) {
        List<String> list = new ArrayList<>();
        for (int i=0; i< count; i++) {
            String s = strings[i];
            list.add(s);
        }
        for (int i=0; i<count; i++) {
            String s = strings[i];
            if (!list.contains(s)) {
                System.out.println("NO_WAY");
            }
        }
    }

    public void go(int max, boolean mapElseList) {
        long mt = 0;
        for (int i=0; i<max; i++) {
            long start = System.nanoTime();
            if (mapElseList) map(i); else list(i);
            long end = System.nanoTime();
            mt += (end-start);
        }
        long avg = mt / max;
        String what = mapElseList ? "Map " : "List";
        System.out.println(what + " avg: " + avg);
    }

    public void go(int max) {
        go(max,false);
        go(max,true);
    }
    public static void main(String[] args) {
        for (int i=0; i< 100; i++) {
            new Foo().go(50);
            System.out.println("----");
        }
    }
}
