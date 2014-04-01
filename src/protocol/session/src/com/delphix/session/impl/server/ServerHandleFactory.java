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

package com.delphix.session.impl.server;

import com.delphix.session.impl.frame.SessionHandle;

/**
 * This class describes the session handle factory. This is used on the server side to create a handle to a newly
 * created session. Currently we use a simple integer sequence for generating the content of the handle.
 */
public class ServerHandleFactory {

    private static final int MAXIMUM_HANDLE = 0x00ffffff;
    private static final byte HANDLE_MAGIC = 0x53;

    private static int handleSequence = 0;

    public static SessionHandle create() {
        int seq = allocate();

        byte[] handle = new byte[4];

        handle[0] = HANDLE_MAGIC;
        handle[1] = (byte) (seq & 0xff);
        handle[2] = (byte) ((seq >> 8) & 0xff);
        handle[3] = (byte) ((seq >> 16) & 0xff);

        return new SessionHandle(handle);
    }

    private static synchronized int allocate() {
        int seq = handleSequence;

        if (handleSequence == MAXIMUM_HANDLE) {
            handleSequence = 0;
        } else {
            handleSequence++;
        }

        return seq;
    }
}
