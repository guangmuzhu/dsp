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

import java.net.SocketAddress;

/**
 * This interface describes the service transport, a transport connection that provides the physical connectivity for
 * the logical service nexus.
 */
public interface ServiceTransport {

    /**
     * Get the transport address of the local peer or null if unavailable.
     */
    public SocketAddress getLocalAddress();

    /**
     * Get the transport address of the remote peer or null if unavailable.
     */
    public SocketAddress getRemoteAddress();

    /**
     * Get the socket send buffer size.
     */
    public int getSendBufferSize();

    /**
     * Get the socket receive buffer size.
     */
    public int getReceiveBufferSize();

    /**
     * Get the outbound queue depth.
     */
    public int getOutboundQueueDepth();

    /**
     * Get the inbound queue depth.
     */
    public int getInboundQueueDepth();

    /**
     * Get the name for the SSL protocol in use.
     */
    public String getTlsProtocol();

    /**
     * Get the name of the SSL cipher suite in use.
     */
    public String getCipherSuite();

    /**
     * Check if the service transport is currently connected.
     */
    public boolean isConnected();

    /**
     * Check if the service transport belongs to the client.
     */
    public boolean isClient();

    /**
     * Close the transport connection.
     */
    public void close();

    /**
     * Get the service options.
     */
    public ServiceOptions getOptions();
}
