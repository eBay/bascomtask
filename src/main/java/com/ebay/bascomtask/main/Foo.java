package com.ebay.bascomtask.main;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class Foo {

    public static void main(String[] args) {
        List<String> x = new ArrayList<>(20);
        x.add(null);
        x.add(null);
        x.add(null);
        x.set(2,"hey");
        System.out.println(x.size());
    }
}
