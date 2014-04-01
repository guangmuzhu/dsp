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
 * Copyright (c) 2013 by Delphix. All rights reserved.
 */

package com.delphix.test;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang.StringUtils;
import org.testng.IMethodInstance;
import org.testng.IMethodInterceptor;
import org.testng.ITestContext;
import org.testng.TestListenerAdapter;

import java.util.List;
import java.util.regex.Pattern;

import static com.google.common.collect.Lists.newArrayList;

public class TestngPatternInterceptor extends TestListenerAdapter implements IMethodInterceptor {

    private final Pattern pattern;

    public TestngPatternInterceptor() {
        this(System.getProperty("test.filter.pattern"));
    }

    public TestngPatternInterceptor(String patternStr) {
        if ("${test.filter.pattern}".equals(patternStr) || StringUtils.isEmpty(patternStr)) {
            pattern = null;
        } else {
            pattern = Pattern.compile(String.format("^.*%s.*$", patternStr));
        }
    }

    @Override
    public List<IMethodInstance> intercept(List<IMethodInstance> list, ITestContext arg1) {
        if (pattern == null) {
            return list;
        }

        List<IMethodInstance> out = newArrayList();
        for (IMethodInstance method : list) {
            String methodName = method.getMethod().getMethodName();
            String className = method.getMethod().getTestClass().getRealClass().getCanonicalName();
            if (testPattern(methodName, className)) {
                out.add(method);
            }
        }
        return out;
    }

    @VisibleForTesting
    public boolean testPattern(String methodName, String className) {
        return pattern.matcher(String.format("%s.%s", className, methodName)).matches();
    }
}
