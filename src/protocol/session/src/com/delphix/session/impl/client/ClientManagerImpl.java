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

package com.delphix.session.impl.client;

import com.delphix.platform.PlatformManagerLocator;
import com.delphix.session.control.ClientControl;
import com.delphix.session.control.NexusInfo;
import com.delphix.session.control.NexusStats;
import com.delphix.session.impl.common.SessionManager;
import com.delphix.session.service.*;
import com.delphix.session.util.ObjectRegistry;

import java.util.*;

/**
 * This class serves as a client session registry. It provides the interfaces for the creation of new sessions and
 * the location of existing ones. It ensures that no two sessions exist with the same termini.
 */
public class ClientManagerImpl extends SessionManager implements ClientManager, ClientControl {

    private ServiceTerminus terminus; // Default client terminus

    // Session client registry keyed by the nexus ID
    private final ObjectRegistry<ServiceNexusID, ClientSession> registry = ObjectRegistry.create();

    // Zombie sessions waiting to go away
    private final List<ClientSession> zombie = new ArrayList<ClientSession>();

    @Override
    public void start() {
        super.start();

        UUID uuid = PlatformManagerLocator.getUUIDStrategy().getUUID();
        terminus = new ServiceUUID(uuid, false, null);

        // Bootstrap the transport manager
        channelManager = new ClientTransportManager();
    }

    public void connect(ClientTransport xport) {
        ((ClientTransportManager) channelManager).connect(xport);
    }

    @Override
    public ServiceTerminus getTerminus() {
        return terminus;
    }

    @Override
    public synchronized ClientNexus create(ClientConfig spec) {
        List<TransportAddress> addrs = spec.getAddresses();

        // At least one transport address must be provided in the service spec
        if (addrs.isEmpty()) {
            throw new IllegalArgumentException("no transport addresses specified");
        }

        ServiceOptions options = spec.getOptions();

        if (!options.isComplete()) {
            throw new IllegalArgumentException("incomplete service options");
        }

        ServiceNexusID id = new ServiceNexusID(spec.getClient(), spec.getServer());
        ClientSession client = new ClientSession(this, spec);

        registry.register(id, client);

        return client;
    }

    @Override
    public synchronized void remove(ServiceTerminus client, ServiceTerminus server) {
        ServiceNexusID id = new ServiceNexusID(client, server);
        ClientSession session = registry.locate(id);

        registry.unregister(id);

        // Add the session to the zombie list until it is ready to be disposed of
        zombie.add(session);
    }

    @Override
    public synchronized ClientNexus locate(ServiceTerminus client, ServiceTerminus server) {
        return registry.locate(new ServiceNexusID(client, server));
    }

    @Override
    public ClientNexus locate(String name) {
        for (ClientNexus nexus : getClients()) {
            if (nexus.toString().equals(name)) {
                return nexus;
            }
        }

        return null;
    }

    @Override
    public synchronized Set<ClientNexus> getClients() {
        Set<ClientNexus> clients = new HashSet<ClientNexus>();
        registry.values(clients);
        return clients;
    }

    @Override
    public synchronized boolean isEmpty() {
        return registry.isEmpty();
    }

    public synchronized void dispose(ClientSession client) {
        zombie.remove(client);
    }

    @Override
    public NexusInfo getNexus(String name) {
        ClientNexus nexus = locate(name);

        if (nexus == null) {
            throw new NexusNotFoundException(name);
        }

        return nexus.getInfo();
    }

    @Override
    public NexusStats getStats(String name) {
        ClientNexus nexus = locate(name);

        if (nexus == null) {
            throw new NexusNotFoundException(name);
        }

        return nexus.getStats();
    }

    @Override
    public void resetStats(String name) {
        ClientNexus nexus = locate(name);

        if (nexus == null) {
            throw new NexusNotFoundException(name);
        }

        nexus.resetStats();
    }

    @Override
    public NexusInfo getPeerNexus(String name) {
        ClientNexus nexus = locate(name);

        if (nexus == null) {
            throw new NexusNotFoundException(name);
        }

        return nexus.getPeerInfo();
    }

    @Override
    public NexusStats getPeerStats(String name) {
        ClientNexus nexus = locate(name);

        if (nexus == null) {
            throw new NexusNotFoundException(name);
        }

        return nexus.getPeerStats();
    }

    @Override
    public void resetPeerStats(String name) {
        ClientNexus nexus = locate(name);

        if (nexus == null) {
            throw new NexusNotFoundException(name);
        }

        nexus.resetPeerStats();
    }

    @Override
    public List<String> listClients() {
        List<String> clients = new ArrayList<String>();

        for (ClientNexus nexus : getClients()) {
            clients.add(nexus.toString());
        }

        return clients;
    }
}
