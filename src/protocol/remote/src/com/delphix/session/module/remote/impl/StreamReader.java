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

package com.delphix.session.module.remote.impl;

import java.nio.ByteBuffer;

/**
 * This interface describes a stream reader task, which reads data made available from a remote source. It supports
 * multiple sub-streams as long as each is identified by a distinct type.
 */
public interface StreamReader extends StreamTask {

    /**
     * Read the data at the specified offset to the stream identified by the given type. The sync flag indicates
     * whether it is desirable to flush the stream to stable storage up to and including the current read. Return
     * true if the stream has been flushed and false otherwise.
     */
    public boolean read(long offset, ByteBuffer[] data, boolean sync, int type);

    /**
     * Indicate the stream identified by the given type has reached EOF. The sync flag indicates whether it is
     * desirable to flush the stream upon EOF. Return true if the stream has been flushed and false otherwise.
     */
    public boolean setEof(long offset, boolean sync, int type);
}
