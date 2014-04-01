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

import com.delphix.session.impl.common.SessionTransport;
import com.delphix.session.impl.common.SessionTransportHandler;
import com.delphix.session.impl.common.SessionTransportManager;
import com.delphix.session.service.ServiceOptions;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.delphix.session.service.ServiceOption.*;

/**
 * This class is responsible for the management of transport connections on behalf of a client. It interfaces with
 * the netty network framework for connection creation, IO threads set up, and channel pipeline configuration.
 */
public class ClientTransportManager extends SessionTransportManager {

    public ClientTransportManager() {
        authenticateHandler = new ClientAuthenticateHandler(this);
        negotiateHandler = new ClientNegotiateHandler(this);

        ExecutorService bossExecutor = Executors.newCachedThreadPool();
        ExecutorService workerExecutor = Executors.newCachedThreadPool();

        factory = new NioClientSocketChannelFactory(bossExecutor, workerExecutor);
    }

    @Override
    public SessionTransportHandler getConnectHandler(SessionTransport xport) {
        return new ClientConnectHandler(this, (ClientTransport) xport);
    }

    @Override
    public SessionTransportHandler getOperateHandler(SessionTransport xport) {
        return new ClientOperateHandler(this, (ClientTransport) xport);
    }

    /**
     * Establish a connection with the remote peer for the given transport.
     */
    public Channel connect(ClientTransport xport) {
        ClientBootstrap bootstrap = new ClientBootstrap();

        // Set up the channel factory - there is a single instance shared among all clients
        bootstrap.setFactory(factory);

        // Set up the pipeline factory
        SessionPipelineFactory pipelineFactory = new SessionPipelineFactory(xport);
        bootstrap.setPipelineFactory(pipelineFactory);

        // Configure the bootstrap with channel options
        ServiceOptions options = xport.getOptions();

        bootstrap.setOption("localAddress", xport.getLocalAddress());
        bootstrap.setOption("remoteAddress", xport.getRemoteAddress());
        bootstrap.setOption("reuseAddress", options.getOption(REUSE_ADDRESS));

        // Configure the connect timeout option.
        bootstrap.setOption("connectTimeoutMillis", options.getOption(CONNECT_TIMEOUT));
        bootstrap.setOption("keepAlive", options.getOption(KEEP_ALIVE));

        bootstrap.setOption("sendBufferSize", options.getOption(SOCKET_SEND_BUFFER));
        bootstrap.setOption("receiveBufferSize", options.getOption(SOCKET_RECEIVE_BUFFER));

        // NIO write buffer sizes.
        bootstrap.setOption("writeBufferHighWaterMark", options.getOption(WRITE_HIGH_WATERMARK));
        bootstrap.setOption("writeBufferLowWaterMark", options.getOption(WRITE_LOW_WATERMARK));

        return bootstrap.connect().getChannel();
    }

    private class SessionPipelineFactory implements ChannelPipelineFactory {

        private final ClientTransport xport;

        public SessionPipelineFactory(ClientTransport xport) {
            this.xport = xport;
        }

        @Override
        public ChannelPipeline getPipeline() throws Exception {
            ChannelPipeline pipeline = Channels.pipeline();

            SessionTransportHandler connect = getConnectHandler(xport);

            pipeline.addLast("encoder", getFrameEncoder());
            pipeline.addLast("decoder", getFrameDecoder());
            pipeline.addLast(connect.getName(), connect);

            return pipeline;
        }
    }
}
