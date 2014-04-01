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

package com.delphix.session.module.remote.impl;

import com.delphix.session.module.remote.StreamProgress;
import com.delphix.session.service.ServiceNexus;
import com.delphix.session.util.LocalFileSource;

import java.io.IOException;

/**
 * This class implements the file writer task that writes data from a local file to a remote sink. It allows writing
 * from a specified offset for a desired length for resumable write.
 */
public class RemoteFileWriter extends AbstractStreamWriter {

    private final ServiceNexus nexus;
    private final StreamProgress progress;

    private LocalFileSource source;
    private StreamSender sender;

    private boolean read;
    private int tag;

    public RemoteFileWriter(String path, long offset, long length, ServiceNexus nexus) {
        this(path, offset, length, nexus, null);
    }

    public RemoteFileWriter(String path, long offset, long length, ServiceNexus nexus, StreamProgress progress) {
        this.nexus = nexus;
        this.progress = progress;

        // Create the file source
        source = new LocalFileSource(path, offset, length);
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    @Override
    public boolean isRead() {
        return read;
    }

    @Override
    public int getTag() {
        return tag;
    }

    @Override
    public void setTag(int tag) {
        this.tag = tag;
    }

    @Override
    public void startWrite() {
        super.startWrite();

        sender = new StreamSender(this, source, nexus, progress);
        sender.setBytesWanted(source.getLength());

        sender.run();
    }

    public long getBytesSent() {
        return sender.getBytesSent();
    }

    @Override
    public void close() throws IOException {
        source.close();
    }
}
