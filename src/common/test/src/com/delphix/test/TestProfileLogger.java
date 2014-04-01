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

package com.delphix.test;

import com.google.common.base.Predicate;
import com.google.common.collect.*;
import com.google.common.primitives.Longs;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;

import java.util.Map;

import static org.apache.commons.lang.StringUtils.repeat;

/**
 * This class provides log output during test runs. This informs test implementors about the cost of adding their tests
 * to precommit and discourages writing tests which take more than a few seconds to run.
 */
public class TestProfileLogger extends TestListenerAdapter {

    private static final String CONTEXT_TASK_NAME = "TOTAL for context";
    private static final String OUTPUT_FORMAT = "%6s  %-60.60s  %6s  %8s  %8s  %10s\n";

    private Map<String, Long> taskStartTimes = Maps.newHashMap();
    private Multimap<String, Task> tasks = ArrayListMultimap.create();

    private enum TaskType {
        Test, Config
    };

    private class Task {
        TaskType type;
        String name;
        int numRuns;
        long minRuntime;
        long maxRuntime;
        long totalRuntime;

        public Task(String name, TaskType type, long runtime) {
            this.name = name;
            this.type = type;
            this.numRuns = 1;
            this.minRuntime = runtime;
            this.maxRuntime = runtime;
            this.totalRuntime = runtime;
        }

        public void addRuntime(long runtime) {
            numRuns++;
            minRuntime = Math.min(runtime, minRuntime);
            maxRuntime = Math.max(runtime, maxRuntime);
            totalRuntime += runtime;
        }
    }

    private void taskStarted(ITestResult result, TaskType type) {
        taskStarted(result.getName(), type, result.getStartMillis());
    }

    private void taskStarted(String name, TaskType type, long startMillis) {
        taskStartTimes.put(name, startMillis);
        if (type == TaskType.Test) {
            System.out.printf("Running %s ...", name);
            System.out.flush();
        }
    }

    private long taskFinished(ITestResult result, TaskType type) {
        return taskFinished(result.getName(), type, result.getEndMillis());
    }

    private long taskFinished(String name, TaskType type, long endMillis) {
        long runtime = endMillis - taskStartTimes.remove(name);

        if (type == TaskType.Test) {
            // Tests can only be run once, so add every one separately.
            tasks.put(name, new Task(name, type, runtime));
            System.out.printf(" done (%d ms).\n", runtime);
        } else {
            // Configuration methods can be run multiple times, so check if we have run one with this name yet.
            Task configTask = Iterables.getFirst(Iterables.filter(tasks.get(name), new Predicate<Task>() {
                @Override
                public boolean apply(Task t) {
                    return t.type == TaskType.Config;
                }
            }), null);

            // If no config task with this name exists, create one. Otherwise, add a new runtime to the existing one.
            if (configTask == null) {
                tasks.put(name, new Task(name, type, runtime));
            } else {
                configTask.addRuntime(runtime);
            }
        }

        return runtime;
    }

    /**
     * Test methods can only be run one at a time and are never re-run.
     */
    @Override
    public void onTestStart(ITestResult result) {
        taskStarted(result, TaskType.Test);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        taskFinished(result, TaskType.Test);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        taskFinished(result, TaskType.Test);
    }

    /**
     * Configuration methods can only be run one at a time but may be run multiple times (once per test, once per test
     * class, etc) so we account for that time as well.
     */
    @Override
    public void beforeConfiguration(ITestResult result) {
        taskStarted(result, TaskType.Config);
    }

    @Override
    public void onConfigurationSuccess(ITestResult result) {
        taskFinished(result, TaskType.Config);
    }

    @Override
    public void onConfigurationFailure(ITestResult result) {
        taskFinished(result, TaskType.Config);
    }

    /**
     * We want to print out the full runtime attributable to each configuration method at the end of the execution, as
     * well as the full runtime of the tests.
     */
    @Override
    public void onStart(ITestContext context) {
        taskStarted(CONTEXT_TASK_NAME, TaskType.Config, context.getStartDate().getTime());
    }

    @Override
    public void onFinish(ITestContext context) {
        System.out.printf(OUTPUT_FORMAT, "TYPE", "NAME", "# RUNS", "MIN (ms)", "MAX (ms)", "TOTAL (ms)");
        System.out.printf(OUTPUT_FORMAT, repeat("-", 6), repeat("-", 60), repeat("-", 6), repeat("-", 8),
                repeat("-", 8), repeat("-", 10));

        // Sort the tasks in increasing time order.
        Ordering<Task> order = new Ordering<Task>() {
            @Override
            public int compare(Task left, Task right) {
                return Longs.compare(left.totalRuntime, right.totalRuntime);
            }
        };

        for (Task t : order.immutableSortedCopy(tasks.values())) {
            boolean multiple = t.numRuns > 1;
            boolean config = t.type == TaskType.Config;
            System.out.printf(OUTPUT_FORMAT, t.type, t.name, config ? t.numRuns : "-", multiple ? t.minRuntime : "-",
                    multiple ? t.maxRuntime : "-", t.totalRuntime);
        }

        long totalRuntime = taskFinished(CONTEXT_TASK_NAME, TaskType.Config, context.getEndDate().getTime());
        System.out.printf(OUTPUT_FORMAT, "", "", "", "", "", repeat("-", 10));
        System.out.printf(OUTPUT_FORMAT, "", "", "", "", "", totalRuntime);
    }
}
