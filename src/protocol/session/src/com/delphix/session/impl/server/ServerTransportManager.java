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

import com.delphix.session.impl.common.SessionTransport;
import com.delphix.session.impl.common.SessionTransportHandler;
import com.delphix.session.impl.common.SessionTransportManager;
import com.delphix.session.service.ServiceOptions;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import static com.delphix.session.service.ServiceOption.*;

/**
 * This class is responsible for the management of transport connections on behalf of a server. It interfaces with
 * the netty network framework for connection creation, IO threads set up, and channel pipeline configuration.
 */
public class ServerTransportManager extends SessionTransportManager {

    private final ServerBootstrap bootstrap = new ServerBootstrap();

    private SessionTransportHandler connectHandler;

    private final ServerManagerImpl manager;

    public ServerTransportManager(ServerManagerImpl manager) {
        this.manager = manager;

        // Create shared login stage handlers
        connectHandler = new ServerConnectHandler(this, manager);
        authenticateHandler = new ServerAuthenticateHandler(this);
        negotiateHandler = new ServerNegotiateHandler(this);

        // Fire up the bootstrap
        factory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool());

        bind();
    }

    private void bind() {
        // Set up the channel factory
        bootstrap.setFactory(factory);

        // Set up the pipeline factory
        SessionPipelineFactory pipelineFactory = new SessionPipelineFactory();
        bootstrap.setPipelineFactory(pipelineFactory);

        // Configure the bootstrap with parent and child channel options
        ServiceOptions options = manager.getServerConfig().getOptions();

        bootstrap.setOption("localAddress", new InetSocketAddress(manager.getServerPort()));
        bootstrap.setOption("reuseAddress", options.getOption(REUSE_ADDRESS));

        // Negotiate the maximum to account for window scaling and downsize later
        bootstrap.setOption("receiveBufferSize", SOCKET_RECEIVE_BUFFER.getMaximum());

        // Configure the connect timeout option
        bootstrap.setOption("child.connectTimeoutMillis", options.getOption(CONNECT_TIMEOUT));
        bootstrap.setOption("child.keepAlive", options.getOption(KEEP_ALIVE));

        // NIO write buffer sizes
        bootstrap.setOption("child.writeBufferHighWaterMark", options.getOption(WRITE_HIGH_WATERMARK));
        bootstrap.setOption("child.writeBufferLowWaterMark", options.getOption(WRITE_LOW_WATERMARK));

        // Bind and start to accept incoming connections
        bootstrap.bind();
    }

    @Override
    public SessionTransportHandler getConnectHandler(SessionTransport xport) {
        return connectHandler;
    }

    @Override
    public SessionTransportHandler getOperateHandler(SessionTransport xport) {
        return new ServerOperateHandler(this, (ServerTransport) xport);
    }

    public ServerManagerImpl getServerManager() {
        return manager;
    }

    private class SessionPipelineFactory implements ChannelPipelineFactory {

        public SessionPipelineFactory() {
            super();
        }

        @Override
        public ChannelPipeline getPipeline() throws Exception {
            ChannelPipeline pipeline = Channels.pipeline();

            SessionTransportHandler connect = getConnectHandler(null);

            pipeline.addLast("encoder", getFrameEncoder());
            pipeline.addLast("decoder", getFrameDecoder());
            pipeline.addLast(connect.getName(), connect);

            return pipeline;
        }
    }
}
