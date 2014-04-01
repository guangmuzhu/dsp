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

import com.delphix.appliance.server.test.UnitTest;
import org.springframework.core.annotation.AnnotationUtils;
import org.testng.IMethodInstance;
import org.testng.IMethodInterceptor;
import org.testng.ITestContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.core.annotation.AnnotationUtils.findAnnotation;

/**
 * Restricts the list of testng tests to run when executed in pure unit test mode (environment specific services
 * are not loaded in the spring context, and any operation relying on them would fail).
 *
 * A test is a pure unit test if either its method, class or parent class is annotated
 * with a @UnitTest annotation (which is not marked as disabled).
 *
 * A method annotation has precedence over a class annotation.
 *
 * Please refer to the testng documentation for various ways or using this interceptor:
 * http://testng.org/doc/documentation-main.html#testng-listeners
 *
 * A simple solution in Eclipse consists in using the setting in testNG plugin, and insert this class name in:
 *
 * Window -> Preference -> TestNG -> Pre Defined Listener
 */
public class UnitTestMethodInterceptor implements IMethodInterceptor {

    @Override
    public List<IMethodInstance> intercept(List<IMethodInstance> methods, ITestContext context) {
        List<IMethodInstance> out = new ArrayList<IMethodInstance>();
        for (IMethodInstance method : methods) {
            UnitTest classAnnote = findAnnotation(method.getMethod().getRealClass(), UnitTest.class);
            if (classAnnote != null && classAnnote.disabled().isEmpty()) {
                Method javaMethod = method.getMethod().getConstructorOrMethod().getMethod();
                if (javaMethod != null) {
                    UnitTest methodAnnote = AnnotationUtils.getAnnotation(javaMethod, UnitTest.class);
                    if (methodAnnote == null || methodAnnote.disabled().isEmpty()) {
                        out.add(method);
                    }
                }
            }
        }
        return out;
    }
}
