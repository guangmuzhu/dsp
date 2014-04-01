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
import com.delphix.session.control.ServerInfo;
import com.delphix.session.impl.common.SessionEventDispatcher;
import com.delphix.session.impl.common.SessionEventSource;
import com.delphix.session.impl.frame.SessionHandle;
import com.delphix.session.net.NetServerConfig;
import com.delphix.session.sasl.SaslServerConfig;
import com.delphix.session.sasl.ServerSaslMechanism;
import com.delphix.session.service.*;
import com.delphix.session.ssl.SSLServerContext;
import com.delphix.session.util.Event;
import com.delphix.session.util.ObjectRegistry;

import javax.net.ssl.SSLEngine;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * This class represents a session protocol service. It manages a collection of sessions that are currently connected
 * to the service. It serves as a conduit that delivers session state change events to the interested parties.
 */
public class ServerImpl implements Server, SessionEventSource {

    private static final Logger logger = Logger.getLogger(ServerImpl.class);

    // Session registry keyed by the client terminus
    private final ObjectRegistry<ServiceTerminus, ServerSession> registry = ObjectRegistry.create();

    // Session registry keyed by the session handle
    private final ObjectRegistry<SessionHandle, ServerSession> handles = ObjectRegistry.create();

    // Active sessions that have not yet logged in
    private final List<ServerSession> active = new ArrayList<ServerSession>();

    private final SessionEventDispatcher dispatcher; // Event dispatcher

    private final ServiceTerminus terminus; // Server terminus
    private final Service service; // Service offering
    private final ProtocolHandlerFactory handlerFactory;

    private final ExecutorService executor; // Service executor

    private ServerConfig config; // Service configuration

    private final ServerManagerImpl manager; // Session server manager

    public ServerImpl(ServerManagerImpl manager, ServerConfig config) {
        this.manager = manager;
        this.config = config;

        terminus = config.getTerminus();
        service = config.getService();
        handlerFactory = config.getProtocolHandlerFactory();

        // We will use the default executor if the service did not bother to specify its own
        ExecutorService executor = config.getExecutor();

        if (executor == null) {
            executor = manager.getExecutionManager();
        }

        this.executor = executor;

        dispatcher = new SessionEventDispatcher(this, manager.getEventManager());
    }

    public synchronized void attach(ServerSession session) {
        active.add(session);
    }

    public synchronized void detach(ServerSession session) {
        active.remove(session);
    }

    @Override
    public synchronized Set<ServerNexus> getClients() {
        Set<ServerNexus> sessions = new HashSet<ServerNexus>();
        registry.values(sessions);
        return sessions;
    }

    @Override
    public synchronized Set<ServiceTerminus> getTermini() {
        Set<ServiceTerminus> termini = new HashSet<ServiceTerminus>();
        registry.keys(termini);
        return termini;
    }

    @Override
    public synchronized boolean isEmpty() {
        return registry.isEmpty();
    }

    @Override
    public synchronized ServerSession locate(ServiceTerminus terminus) {
        return registry.locate(terminus);
    }

    @Override
    public ServerNexus locate(String name) {
        for (ServerNexus nexus : getClients()) {
            if (nexus.toString().equals(name)) {
                return nexus;
            }
        }

        return null;
    }

    public synchronized ServerSession locate(SessionHandle handle) {
        return handles.locate(handle);
    }

    public synchronized ServerSession register(ServerSession session) {
        ServiceTerminus client = session.getClientTerminus();
        SessionHandle handle = session.getHandle();

        // Detach the session from the active list
        active.remove(session);

        // Register the session by the handle
        handles.register(handle, session);

        // Locate stale handle by the client terminus and reinstate it if found
        ServerSession stale = registry.locate(client);

        if (stale != null) {
            registry.unregister(client);
            handles.unregister(stale.getHandle());
        }

        registry.register(client, session);

        return stale;
    }

    public synchronized void unregister(ServerSession session) {
        registry.unregister(session.getClientTerminus());

        handles.unregister(session.getHandle());

        if (handles.isEmpty()) {
            notifyAll();
        }
    }

    @Override
    public ServiceTerminus getTerminus() {
        return terminus;
    }

    @Override
    public Service getService() {
        return service;
    }

    public ProtocolHandlerFactory getProtocolHandlerFactory() {
        return handlerFactory;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public ServerManagerImpl getManager() {
        return manager;
    }

    @Override
    public ServerConfig getConfig() {
        return config;
    }

    public void setConfig(ServerConfig config) {
        this.config = config;
    }

    /**
     * Create an SSL engine to be used for the TLS/SSL handshake or null if TLS is not supported or server not
     * yet located.
     */
    public SSLEngine createSslEngine() {
        SSLServerContext ssl = config.getSslContext();

        if (ssl == null) {
            return null;
        }

        return ssl.create();
    }

    @Override
    public void addListener(NexusListener listener) {
        dispatcher.addListener(listener);
    }

    @Override
    public void removeListener(NexusListener listener) {
        dispatcher.removeListener(listener);
    }

    @Override
    public void notify(NexusListener listener) {
        // Listener registration is ignored due to session event ordering constraints
    }

    @Override
    public void notify(NexusListener listener, Event event) {
        ((NexusEvent) event).notify(listener);
    }

    public void post(ServerSession session, ServerSessionState oldState, ServerSessionState newState) {
        dispatcher.post(new StateChangeEvent(session, oldState, newState));
    }

    public void post(ServerSession predecessor, ServerSession successor) {
        dispatcher.post(new SessionReinstatementEvent(predecessor, successor));
    }

    public void post(ServerSession session) {
        dispatcher.post(new SessionLogoutEvent(session));
    }

    private class StateChangeEvent extends NexusEvent {

        private final ServerSessionState oldState;
        private final ServerSessionState newState;
        private final ServerSession session;

        public StateChangeEvent(ServerSession session, ServerSessionState oldState, ServerSessionState newState) {
            super(ServerImpl.this);

            this.oldState = oldState;
            this.newState = newState;
            this.session = session;
        }

        @Override
        public void run() {
            dispatcher.dispatch(this);
        }

        @Override
        public void notify(NexusListener listener) {
            session.notifyListener(listener, oldState, newState);
        }
    }

    private class SessionReinstatementEvent extends NexusEvent {

        private final ServerSession predecessor;
        private final ServerSession successor;

        public SessionReinstatementEvent(ServerSession predecessor, ServerSession successor) {
            super(ServerImpl.this);

            this.predecessor = predecessor;
            this.successor = successor;
        }

        @Override
        public void run() {
            dispatcher.dispatch(this);
        }

        @Override
        public void notify(NexusListener listener) {
            listener.nexusReinstated(predecessor, successor);
        }
    }

    private class SessionLogoutEvent extends NexusEvent {

        private final ServerSession session;

        public SessionLogoutEvent(ServerSession session) {
            super(ServerImpl.this);

            this.session = session;
        }

        @Override
        public void run() {
            dispatcher.dispatch(this);
        }

        @Override
        public void notify(NexusListener listener) {
            listener.nexusLogout(session);
        }
    }

    @Override
    public boolean isTerminal() {
        return manager.locate(terminus) != this;
    }

    @Override
    public void shutdown() {
        logger.infof("%s: service shutdown", this);

        // Unregister the service
        manager.unregister(terminus);

        // Force close all the sessions
        Set<ServerNexus> sessions = getClients();

        for (ServerNexus session : sessions) {
            CloseFuture future = session.close();

            try {
                future.get();
            } catch (InterruptedException e) {
                logger.errorf("%s: interrupted while closing", session);
            } catch (ExecutionException e) {
                logger.errorf(e, "%s: failed to close", session);
            }
        }

        // Wait for all sessions to report in
        synchronized (this) {
            while (!handles.isEmpty()) {
                logger.infof("%s: wait for sessions %s", this, handles);

                try {
                    wait(1000);
                } catch (InterruptedException e) {
                    // Do nothing
                }
            }
        }

        // Flush pending events and destroy event source
        manager.getEventManager().flush(this, true);

        logger.infof("%s: service disposed", this);
    }

    @Override
    public String toString() {
        return "svc:" + terminus;
    }

    @Override
    public ServerInfo getInfo() {
        ServerInfo info = new ServerInfo();
        info.setTerminus(getTerminus().toString());

        ServerConfig config = getConfig();

        SaslServerConfig saslConfig = config.getSaslConfig();
        List<String> mechanisms = new ArrayList<String>();

        for (ServerSaslMechanism sasl : saslConfig.getMechanisms()) {
            mechanisms.add(sasl.getMechanism());
        }

        info.setSaslMechanisms(mechanisms);

        SSLServerContext sslConfig = config.getSslContext();

        if (sslConfig != null) {
            info.setTlsLevel(sslConfig.getTlsLevel());
        }

        NetServerConfig netConfig = config.getNetConfig();

        if (netConfig != null) {
            List<InetAddress> endpoints = new ArrayList<InetAddress>();

            for (InetAddress address : netConfig.getAll()) {
                endpoints.add(address);
            }

            info.setEndpoints(endpoints);
        }

        info.setOptions(config.getOptions().values());

        List<String> clients = new ArrayList<String>();

        for (ServerNexus client : getClients()) {
            clients.add(client.toString());
        }

        info.setClients(clients);

        return info;
    }
}
