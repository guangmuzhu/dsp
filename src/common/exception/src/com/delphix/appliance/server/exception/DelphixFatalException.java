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
 * Copyright (c) 2013, 2011 by Delphix. All rights reserved.
 */

package com.delphix.appliance.server.exception;

/**
 * This class represents an unanticipated fatal exception from a Delphix component.  For more information on the overall
 * exception model, see the DelphixUserException class.
 *
 * Fatal exceptions are runtime exceptions, and should not be declared in method declarations.  They cannot be caught
 * except by the topmost error handler.  If the component has a need to throw an exception that should be caught by
 * higher level components, use a module-specific exception.  Components should not log a message when throwing
 * exceptions; the toplevel exception handler will handle that.
 */
public class DelphixFatalException extends RuntimeException {

    /**
     * Construct a fatal exception for a new exception.  The error message should be detailed enough to distinguish
     * the nature of the failure for system developers.  The log file will include the message and the stack trace.
     *
     * @param message   Developer-oriented message describing the exception.
     */
    public DelphixFatalException(String message) {
        super(message);
    }

    /**
     * This is identical to the message-only form, but can include a source exception to provide further information
     * about the problem.
     *
     * @param message   Developer-oriented message describing the exception.
     * @param cause     Source of the original exception.
     */
    public DelphixFatalException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * A fatal exception that has a 1:1 correspondence with an underlying exception, and there is no additional context
     * that can be provided through a human-readable message.
     *
     * @param cause     Source of the original exception.
     */
    public DelphixFatalException(Throwable cause) {
        super(cause);
    }
}
