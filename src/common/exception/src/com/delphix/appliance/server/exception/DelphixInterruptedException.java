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
 * This class can be used by consumers to indicate that an operation was interrupted.  It differs from the standard
 * InterruptedException because it is a runtime exception and therefore does not need to be explicitly handled.
 */
public class DelphixInterruptedException extends DelphixFatalException {

    public DelphixInterruptedException() {
        this("");
    }

    public DelphixInterruptedException(String message) {
        super("operation interrupted: " + message);
    }

    public DelphixInterruptedException(Throwable cause) {
        super(cause);
    }
}
