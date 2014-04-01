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

package com.delphix.appliance.server.util;

import com.delphix.appliance.server.exception.DelphixFatalException;
import com.delphix.appliance.server.exception.DelphixInterruptedException;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;

/**
 * Methods to help with exception handling.
 */
public class ExceptionUtil {

    public static final int INTERNAL_ERROR_EXIT_CODE = 128;

    /**
     * If runtime exception leave it unchanged, otherwise wrap it in a DelphixFatalException.
     */
    public static RuntimeException getDelphixException(Throwable t) {
        if (t instanceof RuntimeException) {
            return (RuntimeException) t;
        } else {
            return new DelphixFatalException(t);
        }
    }

    /**
     * ExeuctionExceptions are commonly used to propagate exceptions from async tasks to other threads
     * which are waiting for the async tasks to finish. When the waiting thread catches the ExceutionException
     * it traditionally re-throws e.getCause(). This is to preserve the type of the exception. For example
     * if the async task threw a NexusResetException and we want to propogate that to the waiting thread we
     * cannot throw the ExecutionException.
     *
     * Unfortunately calling e.getCause() and discarding the wrapping ExecutionException also discards
     * stack trace information about were the exception came from, we only get the stack trace of the original
     * exception executing in the asynchronous context.
     *
     * To prevent this we use this method to "unwrap" execution exceptions. This method will return t.getCause(),
     * but it will insert a place-holder UnwrappedExecutionException as t.getCause()'s cause as a place holder
     * for the stack trace information we would otherwise use.
     *
     * Using this method is always preferable to re-throwing e.getCause() on ExecutionExceptions directly.
     */
    public static Throwable unwrap(ExecutionException t) {
        Throwable main = t.getCause();

        UnwrappedExecutionException replaced = new UnwrappedExecutionException(main.getClass(), main.getCause());
        replaced.setStackTrace(main.getStackTrace());

        main.setStackTrace(t.getStackTrace());
        try {
            Field causeField = Throwable.class.getDeclaredField("cause");
            causeField.setAccessible(true);
            causeField.set(main, replaced);
        } catch (NoSuchFieldException e) {
            throw new DelphixFatalException(e);
        } catch (SecurityException e) {
            throw new DelphixFatalException(e);
        } catch (IllegalArgumentException e) {
            throw new DelphixFatalException(e);
        } catch (IllegalAccessException e) {
            throw new DelphixFatalException(e);
        }

        return main;
    }

    /**
     * Determine if the given exception is due to an interrupt.  One would expect that such exceptions would always be
     * modeled as an InterruptedException.  Unfortunately, that is not the case:
     *
     *  - We have our own versions in the Delphix stack (see DelphixInterruptedException)
     *  - Some consumers invent their own variations on this exceptions
     *  - Some consumers will wrap interrupt exceptions in other more inscrutable exceptions
     *
     * This method will pick apart the exception and determine if it's ultimately due to an interrupt.
     *
     * @param t     Source exception
     * @return      True if this exception was due to an interrupt, false otherwise
     */
    public static boolean isInterrupt(Throwable t) {
        // Make it easier on generic consumers
        if (t == null)
            return false;

        do {
            if (t instanceof InterruptedException ||
                    t instanceof InterruptedIOException ||
                    t instanceof DelphixInterruptedException) {
                return true;
            }

            /*
             * When derby takes an interrupt, it can sometimes recover without affecting the connection (propagated
             * as InterruptedException), but other times it requires the connection to be discarded.  For the
             * purposes of this method, these are equivalent, so treat any SQLExceptions with state "08000"
             * (CONN_INTERRUPTED) as an interrupt.
             */
            if (t instanceof SQLException) {
                SQLException s = (SQLException) t;
                String sqlState = s.getSQLState();
                // SQLState can sometimes be null with Derby
                if (sqlState != null && sqlState.equals("08000")) {
                    return true;
                }
            }

        } while ((t = t.getCause()) != null);

        return false;
    }

    /**
     * Closes the given stream and ignores all IOExceptions thrown by the close operation. For streams which buffer
     * data, the close operation may cause the data to be flushed. This may result in an IOException during closing,
     * which actually indicates that some data from a previous write was not fully written. If this is a concern, the
     * stream should be explicitly flushed with OutputStream#flush() before calling this method.
     *
     * The normal use of this function is to ignore errors while closing streams in finally blocks where IOExceptions
     * are not expected:
     *
     * OutputStream out = new SomeStream();
     * try {
     *     out.write(data);
     *     out.flush();
     * } catch (IOException e) {
     *     ExceptionUtil.closeIgnoreExceptions(out);
     * }
     *
     * This method properly avoids swallowing InterruptedIOExceptions received during the close.
     */
    public static void closeIgnoreExceptions(Closeable stream) {
        if (stream == null)
            return;

        try {
            stream.close();
        } catch (InterruptedIOException e) {
            /*
             * We simply re-interrupt the thread so that the interrupt will be handled later by the caller.
             *
             * We could throw a DelphixInterruptedException, but this method will frequently be called in finally blocks
             * where we want to avoid the need for nested finally blocks to make sure all cleanup happens.
             *
             * For example in this case if closing stream1 throws a DelphixInterruptedException stream2 would not be
             * closed:
             *
             * } finally {
             *     ExceptionUtil.closeIgnoreExceptions(stream1);
             *     ExceptionUtil.closeIgnoreExceptions(stream2);
             * }
             */
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            /*
             * Ignore other IOExceptions.
             */
        }
    }
}
