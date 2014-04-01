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

import com.delphix.session.module.remote.StreamProgress;
import com.delphix.session.module.remote.protocol.ReadDataRequest;
import com.delphix.session.module.remote.protocol.StreamDataRequest;
import com.delphix.session.module.remote.protocol.StreamDataResponse;
import com.delphix.session.module.remote.protocol.WriteDataRequest;
import com.delphix.session.service.ServiceNexus;
import com.delphix.session.service.ServiceRequest;
import com.delphix.session.service.ServiceResponse;
import com.delphix.session.util.AbstractDataSender;
import com.delphix.session.util.ByteBufferUtil;
import com.delphix.session.util.DataSource;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * This class implements a stream data sender.
 */
public class StreamSender extends AbstractDataSender {

    private final StreamWriter task;
    private final StreamProgress progress;

    private int type;

    public StreamSender(StreamWriter task, DataSource source, ServiceNexus nexus) {
        this(task, source, nexus, null);
    }

    public StreamSender(StreamWriter task, DataSource source, ServiceNexus nexus, StreamProgress progress) {
        super(source, nexus);

        this.task = task;
        this.progress = progress;
    }

    public void setType(int type) {
        this.type = type;
    }

    @Override
    protected ServiceRequest createDataRequest(ByteBuffer[] data, long offset, boolean eof) {
        StreamDataRequest request;

        if (task.isRead()) {
            request = new ReadDataRequest();
        } else {
            request = new WriteDataRequest();
        }

        request.setTask(task.getTag());
        request.setType(type);

        request.setOffset(offset);
        request.setEof(eof);
        request.setData(data);

        if (progress != null) {
            request.setSync(progress.sync(offset, eof));
        }

        return request;
    }

    @Override
    protected int getRequestOverhead() {
        return StreamDataRequest.MAX_REQUEST_OVERHEAD;
    }

    @Override
    protected File getDebugFile() {
        return null;
    }

    @Override
    protected void updateProgress(ServiceRequest serviceRequest, ServiceResponse serviceResponse) {
        if (progress == null) {
            return;
        }

        StreamDataRequest request = (StreamDataRequest) serviceRequest;
        StreamDataResponse response = (StreamDataResponse) serviceResponse;

        ByteBuffer[] data = request.getData();
        long length = 0;

        if (data != null) {
            ByteBufferUtil.rewind(data);
            length = ByteBufferUtil.remaining(data);
        }

        progress.update(request.getOffset(), length, request.isEof(), response.isSync());
    }
}
