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

import com.delphix.session.module.remote.protocol.*;
import com.delphix.session.service.ProtocolHandler;
import com.delphix.session.service.ServiceNexus;

/**
 * Remote protocol server interface. This is the service contract offered by the server to the client. It includes each
 * and every request defined in the application protocol that may be initiated by the client over the fore channel.
 * Therefore, it is implicitly defined when the application protocol is specified. The server must implement this
 * interface to enable service request dispatching.
 */
public interface RemoteProtocolServer extends ProtocolHandler<RemoteProtocolServer> {

    /**
     * Read the specified file.
     */
    public ReadFileResponse readFile(ReadFileRequest request, ServiceNexus nexus);

    /**
     * Write to the specified file.
     */
    public WriteFileResponse writeFile(WriteFileRequest request, ServiceNexus nexus);

    /**
     * Execute the specified command.
     */
    public ExecuteCommandResponse executeCommand(ExecuteCommandRequest request, ServiceNexus nexus);

    /**
     * Write the data for the specified stream.
     */
    public StreamDataResponse writeData(WriteDataRequest request, ServiceNexus nexus);
}
