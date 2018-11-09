package com.ebay.bascomtask.main;

import static org.junit.Assert.*;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import org.junit.Test;

public class FlexEqTest {

    @Test
    public void testPrimitives() {
        FlexEq ax = new FlexEq();
        assertTrue(ax.apxOut(null,null));
        assertTrue(ax.apxOut(4,4));
        final String V = "yes";
        assertTrue(ax.apxOut(V,V));
        assertFalse(ax.apxOut(V,null));
        FlexEq.Output out = ax.apx(null,V);
        assertFalse(out.result);
        assertTrue(out.outline.indexOf('+') >= 0);
        assertTrue(out.outline.indexOf('-') >= 0);
    }
    
    static class Simple {
        int x;
        String y;
        Simple(int x, String y) {
            this.x = x;
            this.y = y;
        }
    }
    
    @Test
    public void testSimple() {
        Simple s1 = new Simple(3,"foo");
        Simple s2 = new Simple(3,"foo");
        FlexEq ax = new FlexEq();
        assertTrue(ax.apxOut(s1,s2));
        
        s2.x++;
        assertFalse(ax.apxOut(s1,s2));
    }
    
    static class Double {
        boolean b;
        Simple s;
    }
    
    @Test
    public void testDouble() {
        Double d1 = new Double();
        d1.s = new Simple(5,"foo");
        Double d2 = new Double();
        d2.s = d1.s;
        
        FlexEq ax = new FlexEq();
        assertTrue(ax.apxOut(d1,d2));
        
        d2.s = new Simple(5,"foo");
        assertTrue(ax.apxOut(d1,d2));
        
        d1.b = !d1.b;
        assertFalse(ax.apxOut(d1,d2));
        d1.b = !d1.b;
        d1.s.y = "bar";
        assertFalse(ax.apxOut(d1,d2));
    }
    
    @Retention(RUNTIME)
    @Target({METHOD,FIELD})
    public @interface LessThenNotEqual {
    }    
    
    @Test
    public void testInequality() {
        FlexEq ax = new FlexEq();
        ax.rule(LessThenNotEqual.class, false, Integer.class, new FlexEq.Rule<Integer>() {

            @Override
            public boolean eq(Integer x, Integer y) {
                return x.intValue() < y.intValue();
            }});
        
        class Foo {
            @LessThenNotEqual
            int x;
        }
        
        Foo f1 = new Foo();
        f1.x = 1;
        Foo f2 = new Foo();
        f2.x = f1.x;
        
        assertFalse(ax.apxOut(f1,f2)); // Above test is <, not =
        
        f2.x++;
        assertTrue(ax.apxOut(f1,f2));
    }
    
}
