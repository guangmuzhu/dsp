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

package com.delphix.session.control;

import java.io.Serializable;
import java.net.SocketAddress;
import java.util.Map;

public class TransportInfo implements Serializable {

    private SocketAddress localAddress;
    private SocketAddress remoteAddress;
    private int sendBufferSize;
    private int receiveBufferSize;
    private String tlsProtocol;
    private String cipherSuite;
    private Map<String, ?> options;

    public TransportInfo() {

    }

    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    public void setLocalAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(SocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    public void setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public void setReceiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public String getTlsProtocol() {
        return tlsProtocol;
    }

    public void setTlsProtocol(String tlsProtocol) {
        this.tlsProtocol = tlsProtocol;
    }

    public String getCipherSuite() {
        return cipherSuite;
    }

    public void setCipherSuite(String cipherSuite) {
        this.cipherSuite = cipherSuite;
    }

    public Map<String, ?> getOptions() {
        return options;
    }

    public void setOptions(Map<String, ?> options) {
        this.options = options;
    }
}
