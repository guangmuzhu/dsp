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

package com.delphix.session.service;

/**
 * This class describes a login aborted exception. It occurs when the nexus is being closed before it has succeeded in
 * login.
 */
public class LoginAbortedException extends NexusLoginException {

    public LoginAbortedException() {
        super();
    }

    public LoginAbortedException(String message) {
        super(message);
    }

    public LoginAbortedException(Throwable cause) {
        super(cause);
    }

    public LoginAbortedException(String message, Throwable cause) {
        super(message, cause);
    }
}
