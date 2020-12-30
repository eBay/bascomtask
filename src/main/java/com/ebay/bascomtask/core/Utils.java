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
package com.ebay.bascomtask.core;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

class Utils {
    static <T extends Annotation> T getAnnotation(Object x, Method method, Class<T> annotationType) {
        if (x != null) {
            Class<?> clazz = x.getClass();
            try {
                return getAnnotation(clazz, method, annotationType);
            } catch (NoSuchMethodException e) {
                return null;
            } catch (Exception e) {
                throw new RuntimeException("Unable to read annotations",e);
            }
        }
        return null;
    }

    private static <T extends Annotation> T getAnnotation(Class<?> clazz, Method method, Class<T> annotationType) throws NoSuchMethodException {
        if (clazz != null) {
            Method localMethod = clazz.getMethod(method.getName(), method.getParameterTypes());
            T result = localMethod.getAnnotation(annotationType);
            if (result != null) {
                return result;
            } else {
                Class<?> superclass = clazz.getSuperclass();
                return getAnnotation(superclass, method, annotationType);
            }
        }
        return null;
    }

    /**
     * Formats the supplied arguments, in parens, after the supplied base. CompletableFutures
     * have a '+' or '-' prefix indicating whether or not they are complete (if not complete
     * there is no value, so only '-' will be displayed).
     * @param sb to add to
     * @param base prefix
     * @param args arguments
     */
    static void formatFullSignature(StringBuilder sb, String base, Object[] args) {
        sb.append(base);
        sb.append('(');
        for (int i = 0; i < args.length; i++) {
            Object next = args[i];
            String pre = "";
            Object actual = next;
            if (next instanceof CompletableFuture<?>) {
                CompletableFuture<?> v = (CompletableFuture<?>) next;
                if (v.isDone()) {
                    pre = "+";
                    try {
                        actual = v.get();
                    } catch (Exception e) {
                        throw new RuntimeException("Unexpected 'get' on CF exception", e);
                    }
                }
                else {
                    pre = "-";
                    actual = "";
                }
            }
            if (i > 0) {
                sb.append(',');
            }
            sb.append(pre);
            sb.append(actual == null ? "null" : actual);
        }
        sb.append(')');
    }
}
