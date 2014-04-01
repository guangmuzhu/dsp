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
import com.delphix.session.impl.frame.ExchangeID;
import com.delphix.session.impl.frame.RequestFrame;
import com.delphix.session.impl.frame.ResponseFrame;
import com.delphix.session.service.ServiceOptions;
import com.delphix.session.service.ServiceTransport;
import com.delphix.session.util.ObjectRegistry;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.SocketChannelConfig;
import org.jboss.netty.handler.ssl.SslHandler;

import java.net.SocketAddress;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class SessionTransport implements ServiceTransport {

    protected static final Logger logger = Logger.getLogger(SessionTransport.class);

    // Session manager
    protected final SessionManager manager;

    // Outgoing exchange registry
    protected final ObjectRegistry<ExchangeID, SessionExchange> outgoing = ObjectRegistry.create();

    // Incoming exchange registry
    protected final ObjectRegistry<ExchangeID, SessionExchange> incoming = ObjectRegistry.create();

    /*
     * The transport guarantees weak completion ordering among the exchanges that are related to each other in the
     * form of dependencies. Specifically, if exchange A and B are both outstanding on the same transport, and A
     * depends on B, then B is always completed from the transport before A.
     *
     * Linger queue for exchanges sent while the channel is being disconnected. This is used to ensure completion
     * ordering in the case of request send failure.
     */
    protected final Queue<SessionExchange> lingerQueue = new LinkedList<SessionExchange>();

    protected Channel channel; // Transport channel

    protected ServiceOptions options; // Protocol options

    protected boolean quiesced = true; // Quiesced flag

    /*
     * == netty event and thread model ==
     *
     * As of 3.4.0, netty is now doing a much better job serializing close event and ongoing message events. It
     * does so by closing the socket in the close context but queuing the close event to be processed in the IO
     * context. Hence, the IO context is not only responsible for message events but also channel state events
     * such as close. As a result, messages are no longer delivered after the close event has been processed,
     * which has been a recipe for many ugly races. An example involves an illegal state transition exception or
     * an NPE in client transport which is caused by the reordering of close event and login response delivery.
     *
     * In summary, all channel events are now processed in serialized manner by a single IO context the channel
     * is bound to; events are delivered in the same chronological order as they occurred; and channel lifecycle
     * events, such as open and close, are delivered only once. The following illustrates channel event ordering.
     *
     *   open -> bound -> connected -> message* -> disconnected -> unbound -> close
     *
     * To quote the netty thread model design, the correctness criteria for channel events are
     *
     * "To put simply, for a channel:
     *
     *   1. Regardless of its transport and type, its all upstream (i.e. inbound) events must be fired from the
     *     thread that performs I/O for the channel (i.e. I/O thread).
     *   2. All downstream (i.e. outbound) events can be triggered from any thread including the I/O thread and
     *     non-I/O threads. However, any upstream events triggered as a side effect of the downstream event must
     *     be fired from the I/O thread. (e.g. If Channel.close() triggers channelDisconnected, channelUnbound,
     *     and channelClosed, they must be fired by the I/O thread."
     *
     * [references]
     *
     *   https://netty.io/Documentation/Thread+Model+in+Netty+4
     *   https://github.com/netty/netty/issues/140
     *   https://github.com/netty/netty/issues/187
     */

    /*
     * In netty-3.5.3, we have hit a race between the netty IO thread and the application thread issuing a close
     * on the channel. The race left a close event on the channel's event queue which should have been delivered
     * to us. Since we rely on the close event to drive the session state transitions, not seeing this event is
     * a huge problem. While the problem may have been inadvertently fixed in netty-3.5.7, we would still like to
     * have a failsafe measure which guarantees the delivery of this event due to its importance. We will keep it
     * disabled so we can examine the state if we ever get stuck again. But we keep the option open to enable it
     * before shipping pending further testing and risk assessment.
     */
    private static final long CLOSE_TIMEOUT = Long.MAX_VALUE;
    // Close notification timeout (seconds)

    private ScheduledFuture<?> closeFuture; // Close notification future
    private boolean closed; // Close notification flag

    public SessionTransport(SessionManager manager) {
        this.manager = manager;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return channel != null ? channel.getLocalAddress() : null;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return channel != null ? channel.getRemoteAddress() : null;
    }

    @Override
    public int getSendBufferSize() {
        if (channel != null) {
            SocketChannelConfig config = (SocketChannelConfig) channel.getConfig();
            return config.getSendBufferSize();
        }

        return 0;
    }

    @Override
    public int getReceiveBufferSize() {
        if (channel != null) {
            SocketChannelConfig config = (SocketChannelConfig) channel.getConfig();
            return config.getReceiveBufferSize();
        }

        return 0;
    }

    @Override
    public synchronized int getOutboundQueueDepth() {
        return outgoing.size();
    }

    @Override
    public synchronized int getInboundQueueDepth() {
        return incoming.size();
    }

    @Override
    public String getTlsProtocol() {
        if (channel != null) {
            SslHandler ssl = (SslHandler) channel.getPipeline().get("tls");

            if (ssl != null) {
                return ssl.getEngine().getSession().getProtocol();
            }
        }

        return null;
    }

    @Override
    public String getCipherSuite() {
        if (channel != null) {
            SslHandler ssl = (SslHandler) channel.getPipeline().get("tls");

            if (ssl != null) {
                return ssl.getEngine().getSession().getCipherSuite();
            }
        }

        return null;
    }

    @Override
    public boolean isConnected() {
        return channel != null ? channel.isConnected() : false;
    }

    @Override
    public void close() {
        if (channel != null) {
            synchronized (this) {
                // Return now if the close notification has already been delivered
                if (closed) {
                    return;
                }

                // Schedule close notification just in case netty fails to deliver it
                if (closeFuture == null) {
                    closeFuture = manager.schedule(new Runnable() {

                        @Override
                        public void run() {
                            logger.errorf("%s: close after timeout", SessionTransport.this);
                            notifyClosed();
                        }
                    }, CLOSE_TIMEOUT, TimeUnit.SECONDS);
                }
            }

            channel.close();
        }
    }

    @Override
    public ServiceOptions getOptions() {
        return options;
    }

    protected void setOptions(ServiceOptions options) {
        this.options = options.getTransportOptions();
    }

    /**
     * Get the session nexus to which this transport belongs.
     */
    public abstract SessionNexus getNexus();

    /**
     * Notify the transport the underlying channel has been opened.
     */
    public void notifyOpened(Channel channel) {
        this.channel = channel;
    }

    /**
     * Notify the transport the underlying channel has been connected.
     */
    public synchronized void notifyConnected() {
        quiesced = false;
    }

    /**
     * Notify the transport the underlying channel has been disconnected.
     */
    public void notifyDisconnected() {
        /*
         * == netty 3.5.3 update ==
         *
         * The occasional loss of channel disconnect event has been fixed as of netty 3.5.3 at least and possibly
         * even in an earlier version. The disconnected event is always fired even in the presence of close race.
         * However, the close event is still preferred over disconnect as far as the session level event processing
         * is concerned for simplicity and alignment with the upcoming netty 4.0.
         *
         * == netty 3.2.7 workaround ==
         *
         * Due to a bug in netty 3.2.7, there is no guarantee that a disconnected event is always delivered when a
         * previously connected channel is closed. Once in a while, netty would miss the disconnected and unbound
         * channel events before it goes right ahead with the delivery of the closed channel event. Due to the
         * unreliable nature of the disconnected channel event, it is impossible to perform transport and session
         * maintenance in the corresponding event handler. The closed channel event is the only one with guaranteed
         * delivery. Hence, all control state transitions in response to transport disconnect are driven from the
         * closed channel event handler.
         *
         * The following describes the netty race in more details. A channel may be closed explicitly by the user
         * or implicitly due to socket event. Regardless of the cause of the channel closure and the type of the
         * channel (NioClientSocketChannel or NioAcceptedSocketChannel), it all end up calling the close method of
         * the NioWorker class. With two threads calling close simultaneously, the intention is that one and only
         * one thread will be able to fire the channel events in the order of disconnected, unbound, and closed, if
         * the channel was previously connected. The synchronization inside channel.setClosed guarantees that only
         * one thread may receive the positive return value. However, the winner may not necessarily be the thread
         * that found the channel connected, in which case, the disconnected and bound events shall be missed.
         *
         *     Thread-A                     Thread-B
         *     --------                     --------
         *     connected == true
         *     state = ST_CLOSED
         *                                  connected == false
         *                                  state = ST_CLOSED
         *                                  super.setClosed == true
         *                                  !fireChannelDisconnected
         *                                  fireChannelClosed
         *     super.setClosed == false
         *
         *     void close(NioSocketChannel channel, ChannelFuture future) {
         *         boolean connected = channel.isConnected();
         *         boolean bound = channel.isBound();
         *         try {
         *             channel.socket.close();
         *             cancelledKeys ++;
         *
         *             if (channel.setClosed()) {
         *                 future.setSuccess();
         *                 if (connected) {
         *                     fireChannelDisconnected(channel);
         *                 }
         *                 if (bound) {
         *                     fireChannelUnbound(channel);
         *                 }
         *
         *                 cleanUpWriteBuffer(channel);
         *                 fireChannelClosed(channel);
         *             } else {
         *                 future.setSuccess();
         *             }
         *         } catch (Throwable t) {
         *             future.setFailure(t);
         *             fireExceptionCaught(channel, t);
         *         }
         *     }
         *
         *     @Override
         *     protected boolean setClosed() {
         *         state = ST_CLOSED;
         *         return super.setClosed();            <== only one thread may emerge as the winner
         *     }
         */
    }

    /**
     * Notify the transport the underlying channel has been closed.
     */
    public void notifyClosed() {

    }

    protected boolean shutdown() {
        Set<SessionExchange> exchangeSet;

        // Get all the outstanding exchanges to complete
        synchronized (this) {
            /*
             * Now that we added the failsafe mechanism to close notification delivery, it could race with netty
             * even though that's very unlikely to happen. The closed flag ensures we never run the notification
             * more than once.
             */
            if (closed) {
                return false;
            }

            closed = true;

            /*
             * Cancel the scheduled close future if exists. The close future won't be scheduled if the channel is
             * being closed by netty due to exceptions it encountered while processing networking events.
             */
            if (closeFuture != null) {
                closeFuture.cancel(false);
            }

            exchangeSet = new HashSet<SessionExchange>();

            outgoing.values(exchangeSet);
            outgoing.clear();

            incoming.values(exchangeSet);
            incoming.clear();
        }

        // Clear the outstanding exchanges
        reset(exchangeSet);

        // Clear the lingering exchanges
        for (;;) {
            SessionExchange exchange;

            synchronized (this) {
                exchange = lingerQueue.poll();

                if (exchange == null) {
                    quiesced = true;
                    return true;
                }
            }

            exchange.reset();
        }
    }

    /**
     * Reset the exchanges while satisfying dependency ordering.
     */
    private void reset(Set<SessionExchange> exchangeSet) {
        if (exchangeSet.isEmpty()) {
            return;
        }

        // Build a dependency exchange set
        Set<SessionExchange> dependencySet = new HashSet<SessionExchange>();

        for (SessionExchange exchange : exchangeSet) {
            SessionExchange dependency = exchange.getDependency();

            if (dependency != null) {
                dependencySet.add(dependency);
            }
        }

        // Notify the dependencies that are also part of the same set
        for (SessionExchange dependency : dependencySet) {
            if (exchangeSet.remove(dependency)) {
                dependency.setStatus(SessionTransportStatus.CONN_RESET);
                dependency.reset();
            }
        }

        dependencySet.clear();

        // Notify the remaining exchanges
        for (SessionExchange exchange : exchangeSet) {
            exchange.setStatus(SessionTransportStatus.CONN_RESET);
            exchange.reset();
        }

        exchangeSet.clear();
    }

    /**
     * Send the session request to the remote peer.
     */
    public void sendRequest(SessionExchange exchange) {
        RequestFrame request = exchange.getRequest();
        ExchangeID exchangeID = exchange.getExchangeID();

        synchronized (this) {
            if (!channel.isConnected()) {
                /*
                 * If the channel is being disconnected and outstanding exchanges completed, add this exchange to the
                 * linger queue to ensure completion ordering is always satisfied.
                 */
                if (!quiesced) {
                    lingerQueue.offer(exchange);
                    return;
                }

                throw new TransportResetException("failed to send request");
            }

            /*
             * Register the exchange if the channel is still connected. The exchange will be completed by the channel
             * IO thread via the state change notification if transport reset is hit during write.
             */
            outgoing.register(exchangeID, exchange);
        }

        channel.write(request);
    }

    /**
     * Receive the session response from the remote peer.
     */
    public void receiveResponse(ResponseFrame response) {
        ExchangeID exchangeID = response.getExchangeID();

        SessionExchange exchange;
        SessionExchange dependency;

        /*
         * For as long as the transport is still alive, the client must not unilaterally drop an outstanding request
         * over the same transport. An exception is thrown if the request is missing for the response.
         */
        synchronized (this) {
            exchange = outgoing.unregister(exchangeID);

            // Abort the dependency exchange if it hasn't been received
            dependency = exchange.getDependency();

            if (dependency != null) {
                ExchangeID dependencyID = dependency.getExchangeID();

                dependency = outgoing.locate(dependencyID);

                if (dependency != null) {
                    outgoing.unregister(dependencyID);
                }
            }
        }

        if (dependency != null) {
            dependency.setStatus(SessionTransportStatus.ABORTED);
            dependency.reset();
        }

        exchange.setStatus(SessionTransportStatus.SUCCESS);
        exchange.setResponse(response);

        exchange.receive();
    }

    /**
     * Receive the session request from the remote peer.
     */
    public void receiveRequest(RequestFrame request) {
        ExchangeID exchangeID = request.getExchangeID();

        SessionNexus nexus = getNexus();
        SessionExchange exchange = nexus.getServerChannel().createExchange(request);

        /*
         * For as long as the transport is still alive, the client must not retry the request over the same transport.
         * An exception is thrown if duplicate is found for the request.
         */
        synchronized (this) {
            incoming.register(exchangeID, exchange);
        }

        // Set the transport over which the exchange is received
        exchange.setTransport(this);

        exchange.receive();
    }

    /**
     * Send the session response to the remote peer.
     */
    public void sendResponse(SessionExchange exchange) {
        ResponseFrame response = exchange.getResponse();
        ExchangeID exchangeID = exchange.getExchangeID();

        SessionExchange dependency;

        synchronized (this) {
            if (!channel.isConnected()) {
                throw new TransportResetException("failed to send response");
            }

            incoming.unregister(exchangeID);

            // Abort the dependency exchange if it hasn't been sent
            dependency = exchange.getDependency();

            if (dependency != null) {
                ExchangeID dependencyID = dependency.getExchangeID();

                dependency = incoming.locate(dependencyID);

                if (dependency != null) {
                    incoming.unregister(dependencyID);
                }
            }
        }

        channel.write(response);
    }

    /**
     * Accept the transport after it has successfully joined the server channel. This shall enable incoming traffic
     * to flow again on the transport so that requests can be delivered.
     */
    public void notifyAccepted() {
        assert !channel.isReadable();
        channel.setReadable(true);
    }
}
