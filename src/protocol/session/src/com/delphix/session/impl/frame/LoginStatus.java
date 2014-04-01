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

package com.delphix.session.impl.frame;

/**
 * Login status enumeration.
 */
public enum LoginStatus {

    SUCCESS("login is proceeding successfully"),
    VERSION_UNSUPPORTED("protocol version not supported"),
    SERVICE_UNAVAILABLE("service not available"),
    SESSION_NONEXISTENT("session not existent"),
    SESSION_INVALID("session invalid"),
    TLS_UNSUPPORTED("TLS not supported"),
    TLS_REQUIRED("TLS required for login"),
    SASL_FAILURE("SASL authentiation failure"),
    SERVICE_UNREACHABLE("service unreachable"),
    CONNECTION_EXCEEDED("too many connections"),
    PARAMETER_UNSUPPORTED("parameter not supported");

    private final String desc;

    private LoginStatus(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
