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

import com.delphix.appliance.logger.Logger;
import org.testng.ITestContext;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;

import java.util.concurrent.TimeUnit;

public class DelphixLoggingListener extends TestListenerAdapter {

    private static final String SERVER_PREFIX = "com.delphix.appliance.server.";
    private static final int SERVER_PREFIX_LENGTH = SERVER_PREFIX.length();

    private static final String DELPHIX_PREFIX = "com.delphix.";
    private static final int DELPHIX_PREFIX_LENGTH = DELPHIX_PREFIX.length();

    private static final Logger logger = Logger.getLogger(DelphixLoggingListener.class);

    /**
     * The time in nanoseconds that the current test started.
     */
    private long testStartTime = 0;

    /**
     * The time in nanoseconds that the current test group started.
     */
    private long contextStartTime = 0;

    /**
     * The time in milliseconds that we've actually been executing test code (as opposed to configuration) for the
     * current test group.
     */
    private long contextDurationMS = 0;

    static long getElapsedTimeMS(long startTime) {
        return (System.nanoTime() - startTime) / TimeUnit.MILLISECONDS.toNanos(1);
    }

    private void logMessage(ITestResult tr, String label, String message) {
        ITestNGMethod m = tr.getMethod();

        String classname = m.getTestClass().getName();
        if (classname.startsWith(SERVER_PREFIX)) {
            classname = classname.substring(SERVER_PREFIX_LENGTH);
        } else if (classname.startsWith(DELPHIX_PREFIX)) {
            classname = classname.substring(DELPHIX_PREFIX_LENGTH);
        }

        logger.errorf("=== %s %s.%s === %s", label, classname, m.getMethodName(), message);
    }

    @Override
    public void onTestStart(ITestResult tr) {
        logMessage(tr, "START", "");
        testStartTime = System.nanoTime();
    }

    @Override
    public void onTestSuccess(ITestResult tr) {
        assert testStartTime != 0;

        long durationMS = getElapsedTimeMS(testStartTime);

        logMessage(tr, "END", String.format("took %d MS", durationMS));

        testStartTime = 0;
        contextDurationMS += durationMS;
    }

    @Override
    public void onStart(ITestContext context) {
        contextStartTime = System.nanoTime();
        contextDurationMS = 0;
    }

    @Override
    public void onFinish(ITestContext context) {
        long durationMS = getElapsedTimeMS(contextStartTime);

        logger.errorf("=== Unit test context %s completed with %d MS test time %d MS total (test + setup) ===",
                context.getName(), contextDurationMS, durationMS);
    }
}
