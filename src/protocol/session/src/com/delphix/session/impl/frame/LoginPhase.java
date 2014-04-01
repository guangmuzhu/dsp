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
 * Login phase enumeration. Refer to the LoginRequest for a description of each of the login phases.
 */
public enum LoginPhase {

    CONNECT("initiate login connect"),
    ENCRYPT("encrypt transport connection"),
    AUTHENTICATE("authenticate client principal"),
    NEGOTIATE("negotiate operational parameters");

    private final String desc;

    private LoginPhase(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
