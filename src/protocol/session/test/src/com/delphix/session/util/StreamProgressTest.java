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

package com.delphix.session.util;

import com.delphix.appliance.server.test.UnitTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

@UnitTest
public class StreamProgressTest {

    private static final long GB = 1024L * 1024L * 1024L;

    @Test
    public void streamProgressTest() {
        AbstractStreamProgress progress = new AbstractStreamProgress() {

            @Override
            protected void updateProgress() {
                // Log the active stream stats
                for (StreamStat stat : active.values()) {
                    logger.infof("%s", stat);
                }

                // Log the aggregate stats
                logger.infof("%s", aggr);
            }
        };

        progress.setTotalLength(2 * 16 * GB);

        progress.start("test1");

        for (int i = 0; i < 16; i++) {
            progress.transfer("test1", GB);
        }

        progress.stop("test1");

        assertEquals(progress.getPercentComplete(), 50);
        assertEquals(progress.getBytesXferred(), 16 * GB);

        progress.start("test2");

        for (int i = 0; i < 16; i++) {
            progress.transfer("test2", GB);
        }

        progress.stop("test2");

        assertEquals(progress.getPercentComplete(), 100);
        assertEquals(progress.getBytesXferred(), 2 * 16 * GB);
    }
}
