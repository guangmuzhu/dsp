/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Copyright (c) 2014 by Delphix. All rights reserved.
 */

package com.delphix.session.module.rmi.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A utility method to provide a consistent ordering of methods in an interface for use with DSP's
 * RMI module. Since both sides of the communication agree on the absolute ordering of methods in the
 * interface they can communicate an integer instead of method names and parameter types.
 */
public class RmiMethodOrdering {

    private List<Method> methods = Lists.newArrayList();
    private Map<Method, Integer> placement = Maps.newHashMap();

    public RmiMethodOrdering(Class<?> clazz) {
        Map<String, Method> methodMap = Maps.newHashMap();
        for (Method m : clazz.getMethods()) {
            List<String> paramNames = Lists.newArrayList();
            for (Class<?> paramType : m.getParameterTypes()) {
                paramNames.add(paramType.getCanonicalName());
            }
            String str = String.format("%s(%s)", m.getName(), StringUtils.join(paramNames, ", "));
            methodMap.put(str, m);
        }

        List<String> sortedNames = new ArrayList<String>(methodMap.keySet());
        Collections.sort(sortedNames);

        for (String name : sortedNames) {
            Method m = methodMap.get(name);
            placement.put(m, methods.size());
            methods.add(m);
        }
    }

    public Method getMethod(int placement) {
        return methods.get(placement);
    }

    public int getPlacement(Method m) {
        return placement.get(m);
    }
}
