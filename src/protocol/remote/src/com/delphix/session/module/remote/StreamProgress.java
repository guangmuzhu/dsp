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

package com.delphix.session.module.remote;

/**
 * This interface provides stream progress update support. An object implementing this interface should be passed
 * to the remote manager API if progress update is desired. The update() and sync() methods are invoked as stream
 * transfer progresses.
 *
 * Specifically, for file write, sync() is invoked for each StreamDataRequest before the data is sent to see whether
 * stream sync should be requested on the remote target; and update() after the corresponding StreamDataResponse is
 * received. For file read, sync() is invoked for each StreamDataRequest as it arrives to see whether stream sync
 * should be requested locally; and update() after the data has been written out to the stream.
 */
public interface StreamProgress {

    /**
     * Update the stream progress with the given offset in the stream, the length of data sent in the last request,
     * the stream EOF indicator, and whether data has been flushed to stable storage.
     */
    public void update(long offset, long length, boolean eof, boolean sync);

    /**
     * Return true if stream sync is desired at the given offset.
     */
    public boolean sync(long offset, boolean eof);
}
