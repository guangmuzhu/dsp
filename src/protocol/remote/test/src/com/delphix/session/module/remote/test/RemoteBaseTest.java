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

package com.delphix.session.module.remote.test;

import com.delphix.appliance.logger.Logger;
import com.delphix.appliance.server.test.UnitTest;
import com.delphix.session.module.remote.RemoteFactory;
import com.delphix.session.module.remote.RemoteManager;
import com.delphix.session.module.remote.RemoteProtocolClient;
import com.delphix.session.module.remote.service.RemoteConnector;
import com.delphix.session.module.remote.service.RemoteServer;
import com.delphix.session.service.ClientManager;
import com.delphix.session.service.ClientNexus;
import com.delphix.session.service.ProtocolHandler;
import com.delphix.session.service.ServerManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import java.util.Arrays;
import java.util.List;

@ContextConfiguration(locations = {
        "/META-INF/spring/platform-external-context.xml",
        "/META-INF/spring/remote-context.xml",
        "/META-INF/spring/session-context.xml"
})
@UnitTest
public class RemoteBaseTest extends AbstractTestNGSpringContextTests {

    protected static final Logger logger = Logger.getLogger(RemoteTest.class);

    @Autowired
    private ServerManager serverManager;

    @Autowired
    private ClientManager clientManager;

    @Autowired
    protected RemoteFactory remoteFactory;

    @Autowired
    protected RemoteServer remoteServer;

    @Autowired
    protected RemoteConnector remoteConnector;

    protected ClientNexus nexus;
    protected RemoteManager remoteManager;

    @BeforeClass
    public void init() {
        // Fire up protocol client and server manager
        clientManager.start();
        serverManager.start();

        // Fire up file protocol server and connector
        remoteServer.start();
        remoteConnector.start();

        // Create a file client and connect to the server
        nexus = createNexus();
    }

    @AfterClass
    public void fini() {
        closeNexus(nexus);

        remoteConnector.stop();
        remoteServer.stop();

        clientManager.stop();
        serverManager.stop();
    }

    private ClientNexus createNexus() {
        RemoteProtocolClient remoteClient = remoteFactory.createClient(remoteConnector.getServiceExecutor());
        List<ProtocolHandler<?>> protocolHandlers = Arrays.<ProtocolHandler<?>> asList(remoteClient);

        ClientNexus nexus = remoteConnector.create("localhost", "bumblebee", "autobot", protocolHandlers);

        remoteConnector.connect(nexus);
        remoteManager = remoteFactory.createManager(remoteClient, nexus);

        return nexus;
    }

    private void closeNexus(ClientNexus nexus) {
        RemoteConnector.close(nexus);
    }
}
