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

package com.delphix.session.impl.common;

import com.delphix.appliance.logger.Logger;
import com.delphix.appliance.server.exception.DelphixFatalException;
import com.delphix.appliance.server.util.ExceptionUtil;
import com.delphix.session.control.NexusInfo;
import com.delphix.session.control.NexusStats;
import com.delphix.session.control.TransportInfo;
import com.delphix.session.impl.channel.client.SessionClientChannel;
import com.delphix.session.impl.channel.server.SessionServerChannel;
import com.delphix.session.impl.control.*;
import com.delphix.session.service.*;
import com.delphix.session.util.*;
import com.google.common.base.Throwables;

import java.util.*;
import java.util.concurrent.*;

public abstract class SessionNexus implements ServiceNexus {

    protected static final Logger logger = Logger.getLogger(SessionNexus.class);

    protected static final long CONTROL_TIMEOUT = 5000; // Control timeout
    protected static final ServiceCodec controlCodec = // Control codec
    new ExchangeCodec(ExchangeRegistry.create(ControlExchangeType.class));

    protected ProtocolVersion actVersion; // Protocol version in use

    protected final ServiceTerminus client; // Client terminus
    protected final ServiceTerminus server; // Server terminus
    protected final Service service; // Service offering
    protected final Map<Class<?>, ProtocolHandler<?>> protocolHandlerMap;

    protected final GroupCodec codec; // Session Codec

    protected SessionClientChannel clientChannel; // Client channel
    protected SessionServerChannel serverChannel; // Server channel

    protected ExecutorService executor; // Executor service
    protected SessionManager manager; // Session manager

    protected ServiceOptions options; // Negotiated options

    protected SessionCloseFuture closeFuture; // Close future

    // Client constructor
    public SessionNexus(ClientConfig spec) {
        this(spec.getClient(), spec.getServer(), spec.getService(), spec.getProtocolHandlers(), spec.getOptions());
    }

    // Server constructor
    public SessionNexus(ServiceTerminus client, Server server, Collection<? extends ProtocolHandler<?>> handlers) {
        this(client, server.getTerminus(), server.getService(), handlers, server.getConfig().getOptions());
    }

    public SessionNexus(ServiceTerminus client, ServiceTerminus server, Service service,
            Collection<? extends ProtocolHandler<?>> protocolHandlers, ServiceOptions options) {
        this.client = client;
        this.server = server;
        this.service = service;
        this.options = options.getNexusOptions();
        this.protocolHandlerMap = new HashMap<Class<?>, ProtocolHandler<?>>();

        for (ProtocolHandler<?> protocolHandler : protocolHandlers) {
            ProtocolHandler<?> oldHandler =
                    protocolHandlerMap.put(protocolHandler.getProtocolInterface(), protocolHandler);
            if (oldHandler != null) {
                throw new RuntimeException(String.format("protocol handlers %s and %s implement the same interface %s",
                        oldHandler.getClass().getCanonicalName(), protocolHandler.getClass().getCanonicalName(),
                        protocolHandler.getProtocolInterface().getCanonicalName()));
            }
        }

        // Create a session codec for internal and external use
        codec = new GroupCodec(service.getCodec(), controlCodec);
    }

    @Override
    public ServiceTerminus getClientTerminus() {
        return client;
    }

    @Override
    public ServiceTerminus getServerTerminus() {
        return server;
    }

    @Override
    public Service getService() {
        return service;
    }

    public ServiceCodec getCodec() {
        return codec;
    }

    @Override
    public <T extends ProtocolHandler<T>> T getProtocolHandler(Class<T> iface) {
        T result = iface.cast(protocolHandlerMap.get(iface));
        if (result == null) {
            throw new DelphixFatalException("no handler set for " + iface.getCanonicalName());
        }
        return result;
    }

    public ProtocolVersion getActVersion() {
        return actVersion;
    }

    @Override
    public ServiceFuture execute(ServiceRequest request) {
        return execute(request, null);
    }

    @Override
    public ServiceFuture execute(ServiceRequest request, Runnable done) {
        return execute(request, done, 0);
    }

    @Override
    public ServiceFuture execute(ServiceRequest request, Runnable done, long timeout) {
        return clientChannel.execute(request, done, timeout);
    }

    @Override
    public ServiceOptions getOptions() {
        return options;
    }

    @Override
    public CloseFuture close() {
        return close(null);
    }

    @Override
    public synchronized boolean isClosed() {
        return closeFuture != null;
    }

    /**
     * Close the session as it is not possible to continue any more due to the specified exception.
     */
    protected CloseFuture close(Throwable t) {
        synchronized (this) {
            if (closeFuture != null) {
                return closeFuture;
            }

            closeFuture = new SessionCloseFuture(this, t);
        }

        // Execute the close future to stop the session
        execute(closeFuture);

        return closeFuture;
    }

    public abstract void stop();

    public SessionClientChannel getClientChannel() {
        return clientChannel;
    }

    public SessionServerChannel getServerChannel() {
        return serverChannel;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public SessionManager getManager() {
        return manager;
    }

    public ScheduledExecutorService getScheduler() {
        return manager.getScheduleManager();
    }

    public ScheduledFuture<?> schedule(Runnable task, long delay) {
        return manager.schedule(task, delay, TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return manager.schedule(task, delay, unit);
    }

    public void execute(Runnable task) {
        manager.execute(task);
    }

    public EventManager getEventManager() {
        return manager.getEventManager();
    }

    public abstract EventSource getEventSource();

    @Override
    public NexusStats getStats() {
        NexusStats result = new NexusStats();

        Map<String, Object> stats = new HashMap<String, Object>();

        if (clientChannel != null) {
            stats.putAll(clientChannel.getStats());
        }

        if (serverChannel != null) {
            stats.putAll(serverChannel.getStats());
        }

        result.setStats(stats);

        return result;
    }

    @Override
    public void resetStats() {
        if (clientChannel != null) {
            clientChannel.resetStats();
        }

        if (serverChannel != null) {
            serverChannel.resetStats();
        }
    }

    @Override
    public NexusInfo getInfo() {
        NexusInfo info = new NexusInfo();

        info.setClientTerminus(getClientTerminus().toString());
        info.setServerTerminus(getServerTerminus().toString());

        info.setConnected(isConnected());
        info.setDegraded(isDegraded());
        info.setClosed(isClosed());

        info.setOptions(getOptions().values());

        List<TransportInfo> xports = new ArrayList<TransportInfo>();

        for (ServiceTransport xport : getTransports()) {
            TransportInfo xportInfo = new TransportInfo();

            xportInfo.setLocalAddress(xport.getLocalAddress());
            xportInfo.setRemoteAddress(xport.getRemoteAddress());
            xportInfo.setSendBufferSize(xport.getSendBufferSize());
            xportInfo.setReceiveBufferSize(xport.getReceiveBufferSize());
            xportInfo.setTlsProtocol(xport.getTlsProtocol());
            xportInfo.setCipherSuite(xport.getCipherSuite());
            xportInfo.setOptions(xport.getOptions().values());

            xports.add(xportInfo);
        }

        info.setTransports(xports);

        return info;
    }

    @Override
    public NexusStats getPeerStats() {
        GetPeerStatsRequest request = new GetPeerStatsRequest();
        GetPeerStatsResponse response = control(request);
        return response.getStats();
    }

    @Override
    public void resetPeerStats() {
        ResetPeerStatsRequest request = new ResetPeerStatsRequest();
        control(request);
    }

    @Override
    public NexusInfo getPeerInfo() {
        GetPeerInfoRequest request = new GetPeerInfoRequest();
        GetPeerInfoResponse response = control(request);
        return response.getInfo();
    }

    @SuppressWarnings("unchecked")
    private <T extends ServiceResponse> T control(ServiceRequest request) {
        ServiceFuture future = execute(request);
        ServiceResponse response;

        try {
            response = future.await(CONTROL_TIMEOUT, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw Throwables.propagate(ExceptionUtil.unwrap(e));
        } catch (TimeoutException e) {
            throw Throwables.propagate(e);
        }

        return (T) response;
    }
}
