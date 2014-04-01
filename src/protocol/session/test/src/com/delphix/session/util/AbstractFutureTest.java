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

import static org.testng.Assert.*;

/**
 * The goal of this class is to test the state transitions for AbstractFuture and company.
 */
@UnitTest
public class AbstractFutureTest {

    private InterruptedException interruptedException = new InterruptedException();
    private RuntimeException runtimeException = new RuntimeException();
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                fail();
            }
        }
    };

    @Test
    public void testSetResultRecurringTask() {
        AbstractFuture<?> f = new ThreadFuture<Object>(runnable, true);
        f.state = AsyncFutureState.ABORTING;
        f.setResult(null);
        assertTrue(f.state == AsyncFutureState.COMPLETED);

        f.state = AsyncFutureState.ACTIVE;
        f.setResult(null);
        assertTrue(f.state == AsyncFutureState.INITIAL);
    }

    @Test
    public void testSetResultOneShotTask() {
        AbstractFuture<?> f = new ThreadFuture<Object>(runnable, false);
        f.state = AsyncFutureState.ABORTING;
        f.setResult(null);
        assertTrue(f.state == AsyncFutureState.COMPLETED);

        f.state = AsyncFutureState.ABORTING;
        f.setResult(null);
        assertTrue(f.state == AsyncFutureState.COMPLETED);
    }

    @Test
    public void testSetExceptionRecurringTask() {
        AbstractFuture<?> f = new ThreadFuture<Object>(runnable, true);

        // ABORTING task that was interrupted
        f.state = AsyncFutureState.ABORTING;
        f.setException(new InterruptedException());
        assertTrue(f.state == AsyncFutureState.ABORTED);
        assertNull(f.exception);

        // ABORTING task that was not interrupted
        f.state = AsyncFutureState.ABORTING;
        f.setException(runtimeException);
        assertTrue(f.state == AsyncFutureState.COMPLETED);
        assertEquals(f.exception, runtimeException);

        // ACTIVE task that was interrupted
        f.state = AsyncFutureState.ACTIVE;
        f.setException(interruptedException);
        assertTrue(f.state == AsyncFutureState.INITIAL);
        assertNull(f.exception);
    }

    @Test
    public void testSetExceptionOneShotTask() {
        AbstractFuture<?> f = new ThreadFuture<Object>(runnable, false);

        // ABORTING task that was interrupted
        f.state = AsyncFutureState.ABORTING;
        f.setException(new InterruptedException());
        assertTrue(f.state == AsyncFutureState.ABORTED);
        assertNull(f.exception);

        // ABORTING task that was not interrupted
        f.state = AsyncFutureState.ABORTING;
        f.setException(runtimeException);
        assertTrue(f.state == AsyncFutureState.COMPLETED);
        assertEquals(f.exception, runtimeException);

        // ACTIVE task that was interrupted
        f.state = AsyncFutureState.ACTIVE;
        f.setException(interruptedException);
        assertTrue(f.state == AsyncFutureState.COMPLETED);
        assertEquals(f.exception, runtimeException);
    }
}
