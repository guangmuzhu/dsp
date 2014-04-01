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

package com.delphix.session.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ScatteringByteChannel;

/**
 * This interface represents a data source that supports a scatter gather style reading.
 */
public interface DataSource extends ScatteringByteChannel {

    /**
     * Return the minimum bytes to be read before returning from a read call.
     */
    public int getMinBytesRead();

    /**
     * Set the minimum bytes to be read before returning from a read call.
     */
    public void setMinBytesRead(int minBytesRead);

    /**
     * Return the maximum single byte buffer size to allocate.
     */
    public int getMaxBufferSize();

    /**
     * Set the maximum single byte buffer size to allocate.
     */
    public void setMaxBufferSize(int maxBufferSize);

    /**
     * Try to read the expected size from the source channel. It will return null if there is no more data left
     * in the source channel; otherwise, it will return a byte buffer array that contains the data read. It will
     * read at least the minimum bytes read before returning unless EOF has been reached.
     */
    public ByteBuffer[] read(int expected) throws IOException;

    /**
     * Check if the data source has encountered an exception.
     */
    public void check();

    /**
     * Start the data source.
     */
    public void start();
}
