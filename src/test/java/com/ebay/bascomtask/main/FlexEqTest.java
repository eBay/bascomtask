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
package com.ebay.bascomtask.main;

import static org.junit.Assert.*;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import org.junit.Test;

public class FlexEqTest {

    @Test
    public void testPrimitives() {
        FlexEq feq = new FlexEq();
        assertTrue(feq.apxOut(null,null));
        assertTrue(feq.apxOut(4,4));
        final String V = "yes";
        assertTrue(feq.apxOut(V,V));
        assertFalse(feq.apxOut(V,null));
        FlexEq.Output out = feq.apx(null,V);
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
        FlexEq feq = new FlexEq();
        assertTrue(feq.apxOut(s1,s2));
        
        s2.x++;
        assertFalse(feq.apxOut(s1,s2));
    }
    
    static class Nested {
        boolean b;
        Simple s;
    }
    
    @Test
    public void testNested() {
        Nested d1 = new Nested();
        d1.s = new Simple(5,"foo");
        Nested d2 = new Nested();
        d2.s = d1.s;
        
        FlexEq feq = new FlexEq();
        assertTrue(feq.apxOut(d1,d2));
        
        d2.s = new Simple(5,"foo");
        assertTrue(feq.apxOut(d1,d2));
        
        d1.b = !d1.b;
        assertFalse(feq.apxOut(d1,d2));
        d1.b = !d1.b;
        d1.s.y = "bar";
        assertFalse(feq.apxOut(d1,d2));
    }
    
    @Retention(RUNTIME)
    @Target({METHOD,FIELD})
    public @interface LessThanNotEqual {
    }    
    
    @Test
    public void testCustomInequalityRule() {
        FlexEq feq = new FlexEq();
        
        feq.rule(LessThanNotEqual.class, false, Integer.class, new FlexEq.Rule<Integer,LessThanNotEqual>() {

            @Override
            public boolean eq(Integer x, Integer y, LessThanNotEqual ann) {
                return x.intValue() < y.intValue();
            }});
        
        class Foo {
            @LessThanNotEqual
            int x;
        }
        
        Foo f1 = new Foo();
        f1.x = 1;
        Foo f2 = new Foo();
        f2.x = f1.x;
        
        assertFalse(feq.apxOut(f1,f2)); // Above test is <, not =
        
        f2.x++;
        assertTrue(feq.apxOut(f1,f2));
    }
    
    @Test
    public void testInRange() {
        FlexEq feq = new FlexEq();

        class Foo {
            @FlexEq.IntegerInRange(2)
            int x;
        }
        
        Foo f1 = new Foo();
        f1.x = 1;
        Foo f2 = new Foo();
        f2.x = f1.x;
        
        assertTrue(feq.apxOut(f1,f2));
        f2.x++;
        assertTrue(feq.apxOut(f1,f2));
        f2.x++;
        assertFalse(feq.apxOut(f1,f2));
    }
    
    static class OneInt {
        int x;
    }
    
    @Test
    public void testList() {
        FlexEq feq = new FlexEq();

        class Foo {
            List<OneInt> bars = new ArrayList<>();
            
            void addBar(int x) {
                OneInt oneInt = new OneInt();
                oneInt.x = x;
                bars.add(oneInt);
            }
        }
        
        Foo f1 = new Foo();
        f1.addBar(3);
        f1.addBar(7);
        
        Foo f2 = new Foo();
        f2.addBar(3);
        f2.addBar(7);
        
        assertTrue(feq.apxOut(f1,f2));
        f1.bars.get(0).x++; // force inequality
        assertFalse(feq.apxOut(f1,f2));
        
        f1.bars.get(0).x--; // restore equality
        f1.addBar(11);  // Lists now different size
        assertFalse(feq.apxOut(f1,f2));
    }
    
    @Test
    public void testArray() {
        FlexEq feq = new FlexEq();

        class Foo {
            final OneInt[] arr;
            
            Foo(int size) {
                arr = new OneInt[size];
                for (int i=0; i<size; i++) {
                    arr[i] = new OneInt();
                    arr[i].x = i;
                }
            }
        }
        
        Foo f1 = new Foo(2);
        Foo f2 = new Foo(2);
        
        assertTrue(feq.apxOut(f1,f2));
        f1.arr[1].x++;
        assertFalse(feq.apxOut(f1,f2));
        
        f2 = new Foo(1);  // Arrays now different size
        assertFalse(feq.apxOut(f1,f2));
    }
    
    @Retention(RUNTIME)
    @Target({METHOD,FIELD})
    @interface NullMismatch {
    }    
    
    @Test
    public void testNullPass() {
        FlexEq feq = new FlexEq();
        
        feq.rule(NullMismatch.class, true, OneInt.class, new FlexEq.Rule<OneInt,NullMismatch>() {

            @Override
            public boolean eq(OneInt x, OneInt y, NullMismatch ann) {
                return x == null ? y != null : y==null && x != null;
            }});
        
        class Foo {
            @NullMismatch
            OneInt oneInt;
        }
        
        Foo f1 = new Foo();
        f1.oneInt = new OneInt();
        Foo f2 = new Foo();

        assertTrue(feq.apxOut(f1,f2));
        
        f2.oneInt = new OneInt();
        assertFalse(feq.apxOut(f1,f2));

    }
}
