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

package com.delphix.session.impl.server;

import com.delphix.appliance.logger.Logger;
import com.delphix.session.control.NexusInfo;
import com.delphix.session.control.NexusStats;
import com.delphix.session.control.ServerControl;
import com.delphix.session.control.ServerInfo;
import com.delphix.session.impl.common.SessionManager;
import com.delphix.session.service.*;
import com.delphix.session.util.ObjectRegistry;

import java.util.*;

import static com.delphix.session.service.ServiceProtocol.PORT;

/**
 * This class serves as a service registry. It provides the interfaces for the registration, unregistration, and lookup
 * of session protocol services. It ensures that no two services exist with the same terminus.
 */
public class ServerManagerImpl extends SessionManager implements ServerManager, ServerControl {

    private static final Logger logger = Logger.getLogger(ServerManagerImpl.class);

    // Server registry key by the server terminus
    private final ObjectRegistry<ServiceTerminus, ServerImpl> registry = ObjectRegistry.create();

    // Transports outstanding with the server manager
    private final List<ServerTransport> transports = new ArrayList<ServerTransport>();

    private int port; // TCP port number
    private ServerConfig config; // Default service configuration

    @Override
    public void start() {
        super.start();

        logger.info("Starting server manager");
        this.port = PORT;
        this.config = new ServerConfig();

        channelManager = new ServerTransportManager(this);
    }

    @Override
    public synchronized Server register(ServerConfig config) {
        ServiceOptions options = config.getOptions();

        if (!options.isComplete()) {
            throw new IllegalArgumentException("incomplete service options");
        }

        ServerImpl server = new ServerImpl(this, config);
        registry.register(config.getTerminus(), server);

        return server;
    }

    @Override
    public synchronized void unregister(ServiceTerminus terminus) {
        registry.unregister(terminus);
    }

    @Override
    public synchronized Server locate(ServiceTerminus terminus) {
        return registry.locate(terminus);
    }

    @Override
    public Server locate(String name) {
        for (Server server : getServers()) {
            if (server.toString().equals(name)) {
                return server;
            }
        }

        return null;
    }

    @Override
    public synchronized Set<ServiceTerminus> getTermini() {
        Set<ServiceTerminus> termini = new HashSet<ServiceTerminus>();
        registry.keys(termini);
        return termini;
    }

    @Override
    public synchronized Set<Server> getServers() {
        Set<Server> servers = new HashSet<Server>();
        registry.values(servers);
        return servers;
    }

    @Override
    public synchronized boolean isEmpty() {
        return registry.isEmpty();
    }

    @Override
    public ServerConfig getServerConfig() {
        return config;
    }

    public void setServerConfig(ServerConfig config) {
        this.config = config;
    }

    @Override
    public int getServerPort() {
        return port;
    }

    @Override
    public synchronized Collection<ServiceTransport> getTransports() {
        List<ServiceTransport> xports = new ArrayList<ServiceTransport>();
        xports.addAll(transports);
        return xports;
    }

    public synchronized void addTransport(ServerTransport transport) {
        transports.add(transport);
    }

    public synchronized void removeTransport(ServerTransport transport) {
        transports.remove(transport);
    }

    @Override
    public Collection<ServerNexus> getClients() {
        List<ServerNexus> clients = new ArrayList<ServerNexus>();

        for (Server server : getServers()) {
            clients.addAll(server.getClients());
        }

        return clients;
    }

    @Override
    public ServerNexus locateClient(String name) {
        for (ServerNexus nexus : getClients()) {
            if (nexus.toString().equals(name)) {
                return nexus;
            }
        }

        return null;
    }

    @Override
    public NexusInfo getNexus(String name) {
        ServerNexus nexus = locateClient(name);

        if (nexus == null) {
            throw new NexusNotFoundException(name);
        }

        return nexus.getInfo();
    }

    @Override
    public NexusStats getStats(String name) {
        ServerNexus nexus = locateClient(name);

        if (nexus == null) {
            throw new NexusNotFoundException(name);
        }

        return nexus.getStats();
    }

    @Override
    public void resetStats(String name) {
        ServerNexus nexus = locateClient(name);

        if (nexus == null) {
            throw new NexusNotFoundException(name);
        }

        nexus.resetStats();
    }

    @Override
    public NexusInfo getPeerNexus(String name) {
        ServerNexus nexus = locateClient(name);

        if (nexus == null) {
            throw new NexusNotFoundException(name);
        }

        return nexus.getPeerInfo();
    }

    @Override
    public NexusStats getPeerStats(String name) {
        ServerNexus nexus = locateClient(name);

        if (nexus == null) {
            throw new NexusNotFoundException(name);
        }

        return nexus.getPeerStats();
    }

    @Override
    public void resetPeerStats(String name) {
        ServerNexus nexus = locateClient(name);

        if (nexus == null) {
            throw new NexusNotFoundException(name);
        }

        nexus.resetPeerStats();
    }

    @Override
    public List<String> listServers() {
        List<String> servers = new ArrayList<String>();

        for (Server server : getServers()) {
            servers.add(server.toString());
        }

        return servers;
    }

    @Override
    public List<String> listClients() {
        List<String> clients = new ArrayList<String>();

        for (ServerNexus client : getClients()) {
            clients.add(client.toString());
        }

        return clients;
    }

    @Override
    public List<String> listClients(String name) {
        Server server = locate(name);

        if (server == null) {
            throw new ServiceUnavailableException(name);
        }

        List<String> clients = new ArrayList<String>();

        for (ServerNexus client : server.getClients()) {
            clients.add(client.toString());
        }

        return clients;
    }

    @Override
    public ServerInfo getServer(String name) {
        Server server = locate(name);

        if (server == null) {
            throw new ServiceUnavailableException(name);
        }

        return server.getInfo();
    }
}
