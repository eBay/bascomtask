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
 * 
 * @author bremccarthy
 */
public class FlexEq {
    /**
     * Pre-defined rule that is always available. Allows for two integers to be considered
     * equal if within a specified range of each other.
     */
    @Retention(RUNTIME)
    @Target({METHOD,FIELD})
    public @interface IntegerInRange {
        /**
         * Range, non-inclusive.
         * @return
         */
        public int value();
    }
    
    private static final Rule<Integer,IntegerInRange> intRangeRule = new Rule<Integer,IntegerInRange>() {
        @Override
        public boolean eq(Integer x, Integer y, IntegerInRange ann) {
            int diff = Math.abs(x.intValue() - y.intValue());
            int range = ann.value();
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
    
    public FlexEq() {
        rule(IntegerInRange.class,false,Integer.class,intRangeRule);
    }
    
    public <T,ANN> void rule(Class<? extends Annotation> ann, boolean nullPass, Class<T> type, Rule<T,ANN> rule) {
        map.put(ann,new RuleHolder<T,ANN>(ann,nullPass,rule,type));
    }
    
    private Map<Class<? extends Annotation>,RuleHolder<?,?>> map = new HashMap<>();
    
    public static class Output {
        final boolean result;
        final String outline;
        Output(boolean result, String outline) {
            this.result = result;
            this.outline = outline;
        }
    }
    
    public boolean apxOut(Object x, Object y) {
        Output output = apx(x,y);
        if (!output.result) {
            System.err.println(output.outline);
        }
        return output.result;
    }
    
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

    private boolean apx(StringBuffer sb, int pos, Field fd, Object x, Object y) throws IllegalArgumentException, IllegalAccessException {
        boolean rez = true;
        if (x==null && y==null) {
            one(sb,pos,fd,null);
        }
        else if (x==null && y != null) {
            pair(sb,pos,fd,null,y);
            rez = false;
        }
        else if (x != null && y==null) {
            pair(sb,pos,fd,x,null);
            rez = false;
        }
        else {
            
            Class<?> xc = x.getClass();
            Class<?> yc = y.getClass();
            if (!xc.equals(yc)) {
                rez = false;
            }
            else {
                if (x instanceof Collection) {
                    Collection<?> xs = (Collection<?>)x;
                    Collection<?> ys = (Collection<?>)y;
                    int xSize = xs.size();
                    int ySize = ys.size();
                    if (xSize != ySize) {
                        // TBD print
                        return false;
                    }
                    else {
                        Iterator<?> itr = ys.iterator();
                        for (Object nextX: xs) {
                            Object nextY = itr.next();
                            rez &= apx(sb,pos+1,null,nextX,nextY);
                        }
                    }
                }
                else if (xc.getPackage().getName().startsWith("java.")) {
                    rez = compJavaLibObjects(sb,pos,fd,x,y);
                }
                else {
                    one(sb,pos,fd,'{');
                    Field[] xfs = xc.getDeclaredFields();
                    Field[] yfs = yc.getDeclaredFields();
                    for (int i=0; i<xfs.length; i++) {
                        rez &= compareField(sb,pos+2,x,xfs[i],y,yfs[i],xc);
                    }
                    one(sb,pos,null,'}');
                }
            }
        }
        return rez;
    }

    private boolean compJavaLibObjects(StringBuffer sb, int pos, Field fd, Object x, Object y) {
        Boolean eq = checkUserSuppliedEquals(fd,x,y);
        if (eq==null) {
            eq = Objects.equals(x,y);
        }
        if (eq) {
            one(sb,pos,fd,x);
        }
        else {
            pair(sb,pos,fd,x,y);
        }
        return eq;
    }

    private Boolean checkUserSuppliedEquals(Field fd, Object x, Object y) {
        if (fd != null) {
            Annotation[] anns = fd.getAnnotations();
            for (Annotation ann: anns) {
                RuleHolder<?,?> holder = map.get(ann.annotationType());
                if (holder != null) {
                    Class<?>type = holder.paramType;
                    try {
                        Method method = holder.rule.getClass().getMethod("eq",type,type,holder.ann);
                        Object[] args = {x,y,ann};
                        Object methodResult = method.invoke(holder.rule,args);
                        return (Boolean)methodResult;
                    }
                    catch (Exception e) {
                        throw new RuntimeException("Couldn't invoke 'eq' method on rule",e);
                    }
                }
            }
        }
        return null;
    }

    private boolean compareField(StringBuffer sb, int pos, Object x, Field xf, Object y, Field yf, Class<?> cls) throws IllegalAccessException {
        boolean rez = false;
        if (!Modifier.isStatic(xf.getModifiers())) {
            xf.setAccessible(true);
            yf.setAccessible(true);
            Object xv = xf.get(x);
            Object yv = yf.get(y);
            rez = apx(sb,pos+1,xf,xv,yv);
        }
        return rez;
    }

    private void pair(StringBuffer sb, int pos, Field fd, Object x, Object y) {
        one('+',sb,pos,fd,x);
        one('-',sb,pos,fd,y);
    }
    
    private void one(StringBuffer sb, int pos, Field fd, Object x) {
        one(' ',sb,pos,fd,x);
    }
    
    private void one(char c, StringBuffer sb, int pos, Field fd, Object x) {
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
        sb.append('\n');
    }
    
    private void tab(StringBuffer sb, int tab) {
        for (int i=0; i<tab; i++) {
            sb.append(' ');
        }
    }
}
