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
 * Copyright (c) 2013, 2014 by Delphix. All rights reserved.
 */

package com.delphix.appliance.logger;

import com.delphix.platform.PlatformManagerLocator;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;

/**
 * This logger should be used in place of the log4j logger. The intention is to avoid code of the form:
 *
 * if (logger.isDebugEnabled()) {
 *     logger.debug("This is a message about an object: " + object);
 * }
 *
 * The logger.isDebugEnabled() guard in this example is good practice because object.toString() may be expensive
 * enough that we do not want to call it if debug logging is not enabled, but code with many of these debug statements
 * can become hard to read. To make this code cleaner without loosing the benefit of the isDebugEnabled() guard this
 * logger provides a debugf(String, Object...) method:
 *
 * logger.debugf("This is a message about an object: %s", object);
 *
 * debugf() will do the isDebugEnabled() check internally before calling object.toString(). Note that in some cases the
 * isLoggingEnabled() call may still be necessary if the expensive part of the logging operation is not in an object's
 * toString() method:
 *
 * if (logger.isDebugEnabled()) {
 *     logger.debugf("This is a message about some data that is hard to look up: %s", doAnExpensiveStringLookup());
 * }
 *
 * TODO: This class should be moved into the com.delphix.logger package.
 */
public class Logger implements LoggerTester {
    // Fully Qualified Class Name
    private static final String LOGGER_FQCN = Logger.class.getName();

    private org.apache.log4j.Logger logger4j;

    private Logger(org.apache.log4j.Logger logger4j) {
        this.logger4j = logger4j;
    }

    public static Logger getLogger(String FQCN) {
        // Register the class so that its log level can be managed on a granular level if desired.
        PlatformManagerLocator.getLogLevelStrategy().registerFQCN(FQCN);

        // Pass through the request to log4j.
        return new Logger(org.apache.log4j.Logger.getLogger(FQCN));
    }

    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    @Override
    public void setInnerLogger(org.apache.log4j.Logger testLogger) {
        this.logger4j = testLogger;
    }

    /**
     * Log the message unequivocally.
     */
    private void forcedLog(Level level, String message, Throwable t) {
        /*
         * logger4j.callAppenders() will eat the interrupted status, so we clear the status here and restore it after
         * the logger call has completed.
         */
        boolean isInterrupted = Thread.interrupted();

        // The class name (FQCN) lets it know that the caller of this class is the one that should be printed in
        // the log message.  Otherwise the log messages would all say "com.delphix.appliance.logger.Logger..."
        logger4j.callAppenders(new LoggingEvent(LOGGER_FQCN, logger4j, level, message, t));

        if (isInterrupted)
            Thread.currentThread().interrupt();
    }

    public void trace(Object... messages) {
        if (logger4j.isTraceEnabled())
            forcedLog(Level.TRACE, StringUtils.join(messages), null);
    }

    public void trace(Throwable t, Object... messages) {
        if (logger4j.isTraceEnabled())
            forcedLog(Level.TRACE, StringUtils.join(messages), t);
    }

    public void tracef(String fmt, Object... args) {
        if (logger4j.isTraceEnabled())
            forcedLog(Level.TRACE, String.format(fmt, args), null);
    }

    public void tracef(Throwable t, String fmt, Object... args) {
        if (logger4j.isTraceEnabled())
            forcedLog(Level.TRACE, String.format(fmt, args), t);
    }

    public void debug(Object... messages) {
        if (logger4j.isDebugEnabled())
            forcedLog(Level.DEBUG, StringUtils.join(messages), null);
    }

    public void debug(Throwable t, Object... messages) {
        if (logger4j.isDebugEnabled())
            forcedLog(Level.DEBUG, StringUtils.join(messages), t);
    }

    public void debugf(String fmt, Object... args) {
        if (logger4j.isDebugEnabled())
            forcedLog(Level.DEBUG, String.format(fmt, args), null);
    }

    public void debugf(Throwable t, String fmt, Object... args) {
        if (logger4j.isDebugEnabled())
            forcedLog(Level.DEBUG, String.format(fmt, args), t);
    }

    public void error(Object... messages) {
        if (logger4j.isEnabledFor(Level.ERROR))
            forcedLog(Level.ERROR, StringUtils.join(messages), null);
    }

    public void error(Throwable t, Object... messages) {
        if (logger4j.isEnabledFor(Level.ERROR))
            forcedLog(Level.ERROR, StringUtils.join(messages), t);
    }

    public void errorf(String fmt, Object... args) {
        errorf(null, fmt, args);
    }

    public void errorf(Throwable t, String fmt, Object... args) {
        if (logger4j.isEnabledFor(Level.ERROR))
            forcedLog(Level.ERROR, String.format(fmt, args), t);
    }

    public void fatal(Object message) {
        if (logger4j.isEnabledFor(Level.FATAL))
            forcedLog(Level.FATAL, message.toString(), null);
    }

    public void fatal(Throwable t, Object... messages) {
        if (logger4j.isEnabledFor(Level.FATAL))
            forcedLog(Level.FATAL, StringUtils.join(messages), t);
    }

    public void info(Object... messages) {
        if (logger4j.isInfoEnabled())
            forcedLog(Level.INFO, StringUtils.join(messages), null);
    }

    public void info(Throwable t, Object... messages) {
        if (logger4j.isInfoEnabled())
            forcedLog(Level.INFO, StringUtils.join(messages), t);
    }

    public void infof(String fmt, Object... args) {
        if (logger4j.isInfoEnabled())
            forcedLog(Level.INFO, String.format(fmt, args), null);
    }

    public void infof(Throwable t, String fmt, Object... args) {
        if (logger4j.isEnabledFor(Level.INFO))
            forcedLog(Level.INFO, String.format(fmt, args), t);
    }

    public boolean isTraceEnabled() {
        return logger4j.isTraceEnabled();
    }

    public boolean isDebugEnabled() {
        return logger4j.isDebugEnabled();
    }

    public boolean isInfoEnabled() {
        return logger4j.isInfoEnabled();
    }

    public void warn(Object... messages) {
        if (logger4j.isEnabledFor(Level.WARN))
            forcedLog(Level.WARN, StringUtils.join(messages), null);
    }

    public void warn(Throwable t, Object... messages) {
        if (logger4j.isEnabledFor(Level.WARN))
            forcedLog(Level.WARN, StringUtils.join(messages), t);
    }

    public void warnf(String fmt, Object... args) {
        if (logger4j.isEnabledFor(Level.WARN))
            forcedLog(Level.WARN, String.format(fmt, args), null);
    }

    public void warnf(Throwable t, String fmt, Object... args) {
        if (logger4j.isEnabledFor(Level.WARN))
            forcedLog(Level.WARN, String.format(fmt, args), t);
    }
}
