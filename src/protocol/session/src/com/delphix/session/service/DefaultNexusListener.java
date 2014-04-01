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

package com.delphix.session.service;

/**
 * A default nexus listener that ignores all events. A custom nexus listener may extend the default and only override
 * the event callbacks of interest.
 */
public class DefaultNexusListener implements NexusListener {

    @Override
    public void nexusEstablished(ServiceNexus nexus) {

    }

    @Override
    public void nexusClosed(ServiceNexus nexus) {

    }

    @Override
    public void nexusRestored(ServiceNexus nexus) {

    }

    @Override
    public void nexusLost(ServiceNexus nexus) {

    }

    @Override
    public void nexusReinstated(ServiceNexus existing, ServiceNexus replacement) {
        assert !existing.isClient() && !replacement.isClient();
    }

    @Override
    public void nexusLogout(ServiceNexus nexus) {
        assert !nexus.isClient();
    }

    @Override
    public boolean isOneShot() {
        return false;
    }
}
