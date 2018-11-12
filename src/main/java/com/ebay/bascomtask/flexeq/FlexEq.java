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
package com.ebay.bascomtask.flexeq;

import java.lang.annotation.Annotation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Provides generalized non-strict equality on objects, where the strictness is user-defined.
 * Also provides diff-style output for quickly visualizing field differences between objects.
 * 
 * Current limitations:
 *  -Doesn't handle primitive arrays at this point. 
 *  -Only checks for lists in order. 
 *  -Doesn't check for cycles.
 * 
 * @author bremccarthy
 */
public class FlexEq {
    /**
     * Pre-defined rule that allows for two integers to pass if within a specified range of each other.
     */
    @Retention(RUNTIME)
    @Target({METHOD,FIELD})
    public @interface IntegerInRange {
        /**
         * Range, non-inclusive.
         * @return maximum desired range to use in comparision
         */
        public int value();
    }
    
    /**
     * Pre-defined rule that allows for two longs to pass if within a specified range of each other.
     */
    @Retention(RUNTIME)
    @Target({METHOD,FIELD})
    public @interface LongInRange {
        /**
         * Range, non-inclusive.
         * @return maximum desired range to use in comparision
         */
        public long value();
    }
    
    private static final Rule<Integer,IntegerInRange> integerRangeRule = new Rule<Integer,IntegerInRange>() {
        @Override
        public boolean eq(Integer x, Integer y, IntegerInRange ann) {
            int diff = Math.abs(x.intValue() - y.intValue());
            int range = ann.value();
            return diff < range;
        }
    };
    
    private static final Rule<Long,LongInRange> longRangeRule = new Rule<Long,LongInRange>() {
        @Override
        public boolean eq(Long x, Long y, LongInRange ann) {
            int diff = Math.abs(x.intValue() - y.intValue());
            long range = ann.value();
            return diff < range;
        }
    };
    
    public interface Rule<T,ANN> {
        boolean eq(T x, T y, ANN ann);
    }
    
    private class RuleHolder<T,ANN> {
        final Class<? extends Annotation> ann;
        final Rule<T,ANN> rule;
        final Class<T> paramType;
        final boolean nullPass;
        RuleHolder(Class<? extends Annotation> ann, boolean nullPass, Rule<T,ANN> rule, Class<T> type) {
            this.ann = ann;
            this.rule = rule;
            this.paramType = type;
            this.nullPass = nullPass;
        }
    }
    
    private class BoundRule<T,ANN> {
        final RuleHolder<T,ANN> holder;
        final Annotation ann;
        BoundRule(RuleHolder<T,ANN> holder, Annotation ann) {
            this.holder = holder;
            this.ann = ann;
        }
    }
    
    abstract private class Member {
        abstract String getName();
        abstract Annotation[] getAnnotations();
        abstract Object getValue(Object owner);
    }
    
    private class FieldMember extends Member {
        private final Field field;
        FieldMember(Field field) {
            this.field = field;
        }
        @Override
        String getName() {
            return field.getName();
        }
        @Override
        Annotation[] getAnnotations() {
            return field.getAnnotations();
        }
        @Override
        Object getValue(Object owner) {
            field.setAccessible(true);
            try {
                return field.get(owner);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    private static final Annotation[] EMPTY_ANNS = new Annotation[0];
    
    /**
     * Wrapper for a Field when it exists, but also allows map keys and list indexes
     * to be used in the same way.
     */
    private class FauxMember extends Member {
        private final Object name;
        private final Object value;
        
        FauxMember(Object name, Object value) {
            this.name = name;
            this.value = value;
        }

        @Override
        String getName() {
            return name.toString();
        }

        @Override
        Annotation[] getAnnotations() {
            return EMPTY_ANNS;
        }

        @Override
        Object getValue(Object owner) {
            return value;
        }
        
    }
    
    public FlexEq() {
        // Add pre-defined rules that are always available for use
        rule(IntegerInRange.class,false,Integer.class,integerRangeRule);
        rule(LongInRange.class,false,Long.class,longRangeRule);
    }
    
    /**
     * Defines a comparison rule. If there is more than one the choice such rule for a field then
     * one will be selected at random.
     * @param ann annotation to look for
     * @param nullPass if false, consider result false if either value is null -- this is the usual value
     * @param type of field
     * @param rule to invoke
     * @param <T> type of arguments to eq function
     * @param <ANN> type of annotation
     */
    public <T,ANN> void rule(Class<? extends Annotation> ann, boolean nullPass, Class<T> type, Rule<T,ANN> rule) {
        map.put(ann,new RuleHolder<T,ANN>(ann,nullPass,rule,type));
    }
    
    private Map<Class<? extends Annotation>,RuleHolder<?,?>> map = new HashMap<>();
    
    public static class Output {
        final public boolean result;
        final public String outline;
        Output(boolean result, String outline) {
            this.result = result;
            this.outline = outline;
        }
    }
    
    /**
     * Compare and if false then print diff to stderr.
     * @param x first object to compare
     * @param y second object to compare
     * @return true iff deep equals except where custom rules apply
     */
    public boolean apxOut(Object x, Object y) {
        Output output = apx(x,y);
        if (!output.result) {
            System.err.println(output.outline);
        }
        return output.result;
    }
    
    /**
     * Compare two values.
     * @param x first object to compare
     * @param y second object to compare
     * @return boolean result and string diff
     */
    public Output apx(Object x, Object y) {
        StringBuffer sb = new StringBuffer();
        sb.append("///////////////////////\n");
        boolean check = false;
        try {
            check = apx(sb,0,null,x,y);
        }
        catch (Exception e) {
            throw new RuntimeException("Bad apx",e);
        }
        return new Output(check,sb.toString());
    }

    private boolean apx(StringBuffer sb, int pos, Member fd, Object x, Object y) throws IllegalArgumentException, IllegalAccessException {
        BoundRule<?,?> boundRule = findRuleDefinedOn(fd);
        boolean rez = true;
        if (x==null && y==null) {
            normal(sb,pos,fd,null);
        }
        else if (x==null || y==null) {
            if (boundRule != null && !boundRule.holder.nullPass) {
                rez = false;
            }
            else {
                // Check right away so we don't have to worry about nulls after this point
                rez = Boolean.TRUE.equals(checkUserSuppliedEquals(boundRule,x,y));
            }
            if (!rez) {
                miss(sb,pos,fd,x,y);
            }
        }
        else {
            Class<?> xc = x.getClass();
            Class<?> yc = y.getClass();
            if (!xc.equals(yc)) {
                rez = false;
            }
            else {
                rez = compareInstancesOfSameClass(sb,pos,fd,x,y,boundRule,xc);
            }
        }
        return rez;
    }

    private boolean compareInstancesOfSameClass(StringBuffer sb, int pos, Member fd, Object x, Object y,
            BoundRule<?, ?> boundRule, Class<?> cls) throws IllegalAccessException {
        boolean rez = true;
        if (cls.isArray()) {
            Object[] xa = (Object[])x;
            Object[] ya = (Object[])y;
            rez = compareArrays(sb,pos,fd,xa,ya);
        }
        else if (x instanceof Collection) {
            normal(sb,pos,fd,'{',"list");
            Collection<?> xs = (Collection<?>)x;
            Collection<?> ys = (Collection<?>)y;
            rez = compareCollections(sb,pos+1,fd,xs,ys);
            normal(sb,pos,null,'}');
        }
        else if (x instanceof Map) {
            normal(sb,pos,fd,'{',"map");
            Map<?,?> xs = (Map<?,?>)x;
            Map<?,?> ys = (Map<?,?>)y;
            rez = compareMaps(sb,pos+1,fd,xs,ys);
            normal(sb,pos,null,'}');
        }
        else if (cls.getPackage().getName().startsWith("java.") || boundRule != null) {
            rez = compareDirectly(sb,pos,fd,boundRule,x,y);
        }
        else {
            normal(sb,pos,fd,'{');
            do {
                rez &= compareInstances(sb,pos,x,y,cls);
                cls = cls.getSuperclass();
            }
            while (cls != null && cls != Object.class);
            normal(sb,pos,null,'}');
        }
        return rez;
    }
    
    private boolean compareInstances(StringBuffer sb, int pos, Object x, Object y, Class<?> cls) throws IllegalAccessException {
        boolean rez = true;
        Field[] xfs = cls.getDeclaredFields();
        for (int i=0; i<xfs.length; i++) {
            Field fieldOfObject = xfs[i];
            if (!Modifier.isStatic(fieldOfObject.getModifiers())) {                        
                Member mx = new FieldMember(fieldOfObject);
                rez &= compareField(sb,pos+1,x,mx,y,mx,cls);
            }
        }
        return rez;
    }

    /**
     * Internal utility for representing a size that is not directly a string.
     */
    private static class Say {
        final int size;
        @Override public String toString() {
            return "<size="+size+'>';
        }
        Say(int size) {
            this.size = size;
        }
    }
    
    private boolean compareMaps(StringBuffer sb, int pos, Member fd, Map<?, ?> xs, Map<?, ?> ys) throws IllegalAccessException {
        boolean rez = true;
        
        for (Map.Entry<?,?>nextX: xs.entrySet()) {
            Object xKey = nextX.getKey();
            Object xValue = nextX.getValue();
            Object yValue = ys.get(xKey);
            Member fm = new FauxMember(xKey,null);            
            if (yValue==null) {
                oneLine('+',sb,pos,fm,xValue);
                rez = false;
            }
            else {
                rez &= apx(sb,pos+1,fm,xValue,yValue);
            }
        }        
        for (Map.Entry<?,?>nextY: ys.entrySet()) {
            Object yKey = nextY.getKey();
            Object yValue = nextY.getValue();
            Object xValue = xs.get(yKey);
            if (xValue==null) {
                Member fm = new FauxMember(yKey,null);
                oneLine('-',sb,pos,fm,yValue);
            }
        }        
        return rez;
    }

    private boolean compareCollections(StringBuffer sb, int pos, Member fd, Collection<?> xs, Collection<?> ys)
            throws IllegalAccessException {
        boolean rez = true;
        int count = 0;
        
        Iterator<?> yItr = ys.iterator();
        for (Object nextX: xs) {
            String key = String.valueOf(count++);
            Member fm = new FauxMember(key,null);
            if (!yItr.hasNext()) {
                oneLine('+',sb,pos,fm,nextX);
                rez = false;
            }
            else {
                Object nextY = yItr.next();
                rez &= apx(sb,pos+1,fm,nextX,nextY);
            }
        }
        if (ys.size() > xs.size()) {
            yItr = ys.iterator();
            for (int i=0; i<xs.size(); i++) {
                yItr.next();
            }
            while (yItr.hasNext()) {
                String key = String.valueOf(count++);
                Member fm = new FauxMember(key,null);
                Object nextY = yItr.next();
                oneLine('-',sb,pos,fm,nextY);
                rez = false;
            }
        }
        return rez;
    }

    private boolean compareArrays(StringBuffer sb, int pos, Member fd, Object[]xa, Object[]ya)
            throws IllegalAccessException {
        boolean rez = true;
        int xSize = xa.length;
        int ySize = ya.length;
        if (xSize != ySize) {
            miss(sb,pos,fd,new Say(xSize),new Say(ySize));
            rez = false;
        }
        else {
            for (int i=0; i<xSize; i++) {
                Object nextX = xa[i];
                Object nextY = ya[i];
                rez &= apx(sb,pos+1,null,nextX,nextY);
            }
        }
        return rez;
    }

    private boolean compareDirectly(StringBuffer sb, int pos, Member fd, BoundRule<?,?> boundRule, Object x, Object y) {
        Boolean eq = checkUserSuppliedEquals(boundRule,x,y);
        if (eq==null) {
            eq = Objects.equals(x,y);
        }
        if (eq) {
            normal(sb,pos,fd,x);
        }
        else {
            miss(sb,pos,fd,x,y);
        }
        return eq;
    }
    
    private BoundRule<?,?> findRuleDefinedOn(Member fd) {
        if (fd != null) {
            Annotation[] anns = fd.getAnnotations();
            for (Annotation ann: anns) {
                RuleHolder<?,?> holder = map.get(ann.annotationType());
                if (holder != null) {
                    return new BoundRule<>(holder,ann);
                }
            }
        }
        return null;
    }

    private Boolean checkUserSuppliedEquals(BoundRule<?,?> boundRule, Object x, Object y) {
        if (boundRule != null) {
            Class<?>type = boundRule.holder.paramType;
            Object[] args = {x,y,boundRule.ann};
            Method method = null;
            try {
                method = boundRule.holder.rule.getClass().getMethod("eq",type,type,boundRule.holder.ann);                
                Object methodResult = method.invoke(boundRule.holder.rule,args);
                return (Boolean)methodResult;
            }
            catch (Exception e) {
                throw new RuntimeException("Couldn't invoke method " + method,e);
            }
        }
        return null;
    }

    private boolean compareField(StringBuffer sb, int pos, Object x, Member xf, Object y, Member yf, Class<?> cls) throws IllegalAccessException {
        Object xv = xf.getValue(x);
        Object yv = yf.getValue(y);
        return apx(sb,pos+1,xf,xv,yv);
    }

    private void miss(StringBuffer sb, int pos, Member fd, Object x, Object y) {
        oneLine('+',sb,pos,fd,x);
        oneLine('-',sb,pos,fd,y);
    }
    
    private void normal(StringBuffer sb, int pos, Member fd, Object x) {
        normal(sb,pos,fd,x,null);
    }

    private void normal(StringBuffer sb, int pos, Member fd, Object x, String comment) {
        oneLine(' ',sb,pos,fd,x,comment);
    }
    
    private void oneLine(char c, StringBuffer sb, int pos, Member fd, Object x) {
        oneLine(c,sb,pos,fd,x,null);
    }
    
    private void oneLine(char c, StringBuffer sb, int pos, Member fd, Object x, String comment) {
        sb.append(c);
        tab(sb,2 + pos*2 - 1);
        if (fd != null) {
            String fn = fd.getName();
            sb.append(fn);
            sb.append('=');
        }
        if (x instanceof String) {
            sb.append('"');
            sb.append(x);
            sb.append('"');
        }
        else {
            sb.append(x);
        }
        if (comment != null) {
            sb.append("   // ");
            sb.append(comment);
        }
        sb.append('\n');
    }
    
    private void tab(StringBuffer sb, int tab) {
        for (int i=0; i<tab; i++) {
            sb.append(' ');
        }
    }
}
