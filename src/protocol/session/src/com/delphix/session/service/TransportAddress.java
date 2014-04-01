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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import static com.delphix.session.service.ServiceProtocol.PORT;

/**
 * This class describes the transport address specification. It includes a remote address which is the destination
 * of the transport connection and optionally a local address which is the desired source. If local address is not
 * explicitly specified, one is chosen automatically by the system.
 */
public class TransportAddress {

    private final SocketAddress remoteAddress;
    private final SocketAddress localAddress;

    public TransportAddress(String remoteAddress) throws UnknownHostException {
        this(InetAddress.getByName(remoteAddress));
    }

    public TransportAddress(String remoteAddress, int remotePort) throws UnknownHostException {
        this(InetAddress.getByName(remoteAddress), remotePort);
    }

    public TransportAddress(String remoteAddress, int remotePort, int localPort) throws UnknownHostException {
        this(InetAddress.getByName(remoteAddress), remotePort, localPort);
    }

    public TransportAddress(String remoteAddress, int remotePort, String localAddress, int localPort)
            throws UnknownHostException {
        this(InetAddress.getByName(remoteAddress), remotePort, InetAddress.getByName(localAddress), localPort);
    }

    public TransportAddress(InetAddress remoteAddress) {
        this(remoteAddress, PORT);
    }

    public TransportAddress(InetAddress remoteAddress, int remotePort) {
        this(new InetSocketAddress(remoteAddress, remotePort));
    }

    public TransportAddress(InetAddress remoteAddress, int remotePort, int localPort) {
        this(new InetSocketAddress(remoteAddress, remotePort), localPort);
    }

    public TransportAddress(InetAddress remoteAddress, int remotePort, InetAddress localAddress, int localPort) {
        this(new InetSocketAddress(remoteAddress, remotePort), new InetSocketAddress(localAddress, localPort));
    }

    public TransportAddress(SocketAddress remoteAddress) {
        this(remoteAddress, null);
    }

    public TransportAddress(SocketAddress remoteAddress, int localPort) {
        this(remoteAddress, new InetSocketAddress(localPort));
    }

    public TransportAddress(SocketAddress remoteAddress, SocketAddress localAddress) {
        this.remoteAddress = remoteAddress;
        this.localAddress = localAddress;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    @Override
    public String toString() {
        return String.format("%s-%s", localAddress != null ? localAddress : "<>", remoteAddress);
    }
}
