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
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@UnitTest
public class TestngPatternInterceptorTest {

    @Test
    public void testExactMethodName() {
        TestngPatternInterceptor interceptor = new TestngPatternInterceptor("testMethod");
        assertTrue(interceptor.testPattern("testMethod", "testClass"));
        assertFalse(interceptor.testPattern("testSomethingElse", "testClass"));
    }

    @Test
    public void testFuzzyMethodName() {
        TestngPatternInterceptor interceptor = new TestngPatternInterceptor("testP");
        assertTrue(interceptor.testPattern("testPattern", "testClass"));
        assertFalse(interceptor.testPattern("testSomethingElse", "testClass"));
    }

    @Test
    public void testExactClassName() {
        TestngPatternInterceptor interceptor = new TestngPatternInterceptor("TestngPatternInterceptorTest");
        assertTrue(interceptor.testPattern("", TestngPatternInterceptorTest.class.getCanonicalName()));
        assertFalse(interceptor.testPattern("", TestngPatternInterceptor.class.getCanonicalName()));
    }

    @Test
    public void testPackageName() {
        TestngPatternInterceptor interceptor = new TestngPatternInterceptor("com\\.delphix");
        assertTrue(interceptor.testPattern("", TestngPatternInterceptorTest.class.getCanonicalName()));
        assertFalse(interceptor.testPattern("", String.class.getCanonicalName()));
    }

    @Test
    public void testRegexp() {
        TestngPatternInterceptor interceptor = new TestngPatternInterceptor("d[a-z]lphix");
        assertTrue(interceptor.testPattern("", TestngPatternInterceptorTest.class.getCanonicalName()));
        assertFalse(interceptor.testPattern("", String.class.getCanonicalName()));
    }
}
