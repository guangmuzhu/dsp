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

package com.delphix.appliance.host.example.client;

import com.delphix.session.module.remote.RemoteFactory;
import com.delphix.session.module.remote.RemoteManager;
import com.delphix.session.module.remote.RemoteProtocolClient;
import com.delphix.session.module.remote.service.RemoteConnector;
import com.delphix.session.service.ClientManager;
import com.delphix.session.service.ClientNexus;
import com.delphix.session.service.ProtocolHandler;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

public class ExampleClient {
    @Autowired
    private ClientManager clientManager;

    @Autowired
    private RemoteConnector remoteConnector;

    @Autowired
    private RemoteFactory remoteFactory;

    private ClientNexus nexus;
    private RemoteManager remoteManager;

    public void init() {
        final RemoteProtocolClient remoteClient = remoteFactory.createClient(remoteConnector.getServiceExecutor());
        List<ProtocolHandler<?>> protocolHandlers = Arrays.<ProtocolHandler<?>> asList(remoteClient);

        nexus = remoteConnector.create("localhost", "bumblebee", "autobot", protocolHandlers);
        remoteConnector.connect(nexus);
        remoteManager = remoteFactory.createManager(remoteClient, nexus);
    }

    public void executeRemoteCommand() {
        // Execute remote command
        String[] remoteArgs = { "ls", "-l", "/" };
        Process remote = remoteManager.executeCommand(remoteArgs, null, null, false, null);

        int exitCode;
        try {
            exitCode = remote.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (exitCode != 0)
            throw new RuntimeException("Remote command execution failed");

        // Parse remote output
        try {
            InputStream remoteStdout = remote.getInputStream();
            BufferedReader remoteReader = new BufferedReader(new InputStreamReader(remoteStdout, "UTF-8"));
            String line;
            while ((line = remoteReader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        remote.destroy();
    }

    public void fini() {
        RemoteConnector.close(nexus);
        remoteConnector.stop();
        clientManager.stop();
    }
}
