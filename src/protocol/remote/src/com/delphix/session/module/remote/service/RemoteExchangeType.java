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

package com.delphix.session.module.remote.service;

import com.delphix.session.module.remote.protocol.*;
import com.delphix.session.service.ServiceExchange;
import com.delphix.session.util.ExchangeType;

public enum RemoteExchangeType implements ExchangeType {

    READ_FILE_REQUEST(ReadFileRequest.class),
    READ_FILE_RESPONSE(ReadFileResponse.class),
    WRITE_FILE_REQUEST(WriteFileRequest.class),
    WRITE_FILE_RESPONSE(WriteFileResponse.class),
    EXECUTE_COMMAND_REQUEST(ExecuteCommandRequest.class),
    EXECUTE_COMMAND_RESPONSE(ExecuteCommandResponse.class),
    STREAM_START_REQUEST(StreamStartRequest.class),
    STREAM_START_RESPONSE(StreamStartResponse.class),
    READ_DATA_REQUEST(ReadDataRequest.class),
    WRITE_DATA_REQUEST(WriteDataRequest.class),
    STREAM_DATA_RESPONSE(StreamDataResponse.class);

    private Class<? extends ServiceExchange> objectClass;

    private RemoteExchangeType(Class<? extends ServiceExchange> objectClass) {
        this.objectClass = objectClass;
    }

    @Override
    public Class<? extends ServiceExchange> getObjectClass() {
        return objectClass;
    }
}
