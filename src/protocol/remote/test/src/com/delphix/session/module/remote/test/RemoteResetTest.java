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
 * Copyright (c) 2014 by Delphix. All rights reserved.
 */

package com.delphix.session.module.remote.test;

import com.delphix.session.service.NexusResetException;
import com.delphix.session.service.ServiceTransport;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Iterator;

public class RemoteResetTest extends RemoteBaseTest {

    /**
     * This test ensures that when a remote command is issued while the nexus is in all path down (APD) state and
     * subsequently reset the command will fail instead of getting stuck.
     */
    @Test
    public void executeReset() throws Exception {
        // Put the nexus into a temporary all path down state
        Collection<ServiceTransport> xports = nexus.getTransports();
        Iterator<ServiceTransport> iter = xports.iterator();

        while (iter.hasNext()) {
            iter.next().close();
        }

        xports.clear();

        Thread.sleep(250);

        // Execute the remote command
        String[] args = new String[] { "cat" };
        Process remote = remoteManager.executeCommand(args, null, null, false, null);

        Thread.sleep(250);

        // Reset the nexus to fail any outstanding remote exchange
        nexus.close().get();

        // The remote process should be terminated shortly
        try {
            remote.waitFor();
        } catch (NexusResetException e) {
            // Exception ignored
        }

        remote.destroy();
    }
}
