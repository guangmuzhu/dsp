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

package com.delphix.appliance.host.example.server;

import com.delphix.appliance.logger.Logger;
import com.delphix.session.module.remote.service.RemoteServer;
import com.delphix.session.service.ServerManager;
import org.springframework.beans.factory.annotation.Autowired;

public class ExampleServer {
    public static final Logger logger = Logger.getLogger(ExampleServer.class);

    @Autowired
    private ServerManager serverManager;

    @Autowired
    private RemoteServer remoteServer;

    public void init() {
        logger.infof("Server started on port %d.", serverManager.getServerPort());
    }

    public void fini() {
        remoteServer.stop();
        serverManager.stop();
    }
}
