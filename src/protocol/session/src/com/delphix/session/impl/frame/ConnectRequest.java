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

package com.delphix.session.impl.frame;

import com.delphix.session.service.AbstractTerminus;
import com.delphix.session.service.ServiceTerminus;
import com.delphix.session.ssl.TransportSecurityLevel;
import com.delphix.session.util.ProtocolVersion;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * This class describes the initial login connect request.
 *
 *   o minVersion
 *   o maxVersion
 *
 * The client specifies a version range defined by the minimum and maximum version inclusive that it speaks.
 *
 * The client must specify the same version range over all connections of the same session. As a result, the server
 * should return the same active version. If the server returns a different active version for each connection, the
 * login must be aborted immediately.
 *
 *   o client
 *   o server
 *
 * The client specifies the protocol end points of the session. The server end point will be used to look up the
 * service registered with the protocol stack and the client end point for session state management.
 *
 *   o sessionHandle
 *
 * Session handle refers to a short-hand identifier for the data structure instantiated on the server to represent
 * the session state.
 *
 * The initial login connect request is processed by the server depending on the session handle and the client and
 * server instance as follows.
 *
 *   if (session handle == null) {
 *       if (session exists for client and service instance) {
 *           session reinstatement
 *       } else {
 *           instantiate new session
 *       }
 *   } else if (session exists by the handle) {
 *       if (session refers to the same client and service instance) {
 *           add the connection
 *       } else {
 *           fail the login due to invalid session handle
 *       }
 *   } else {
 *       fail the login attempt due to invalid session handle
 *   }
 *
 *   o tlsLevel
 *
 * The client specifies whether it desires to protect the connection with TLS encryption and the level of encryption
 * minimally acceptable to the client.
 */
public class ConnectRequest extends LoginRequest {

    private ProtocolVersion minVersion;
    private ProtocolVersion maxVersion;

    private ServiceTerminus client;
    private ServiceTerminus server;

    private SessionHandle sessionHandle;

    private TransportSecurityLevel tlsLevel;

    public ConnectRequest() {
        super();
    }

    @Override
    public LoginPhase getPhase() {
        return LoginPhase.CONNECT;
    }

    public ProtocolVersion getMinVersion() {
        return minVersion;
    }

    public void setMinVersion(ProtocolVersion minVersion) {
        this.minVersion = minVersion;
    }

    public ProtocolVersion getMaxVersion() {
        return maxVersion;
    }

    public void setMaxVersion(ProtocolVersion maxVersion) {
        this.maxVersion = maxVersion;
    }

    public ServiceTerminus getClient() {
        return client;
    }

    public void setClient(ServiceTerminus client) {
        this.client = client;
    }

    public ServiceTerminus getServer() {
        return server;
    }

    public void setServer(ServiceTerminus server) {
        this.server = server;
    }

    public SessionHandle getSessionHandle() {
        return sessionHandle;
    }

    public void setSessionHandle(SessionHandle sessionHandle) {
        this.sessionHandle = sessionHandle;
    }

    public TransportSecurityLevel getTlsLevel() {
        return tlsLevel;
    }

    public void setTlsLevel(TransportSecurityLevel tlsLevel) {
        this.tlsLevel = tlsLevel;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        minVersion = ProtocolVersion.deserialize(in);
        maxVersion = ProtocolVersion.deserialize(in);

        client = AbstractTerminus.deserialize(in);
        server = AbstractTerminus.deserialize(in);

        if (in.readBoolean()) {
            sessionHandle = SessionHandle.deserialize(in);
        }

        if (in.readBoolean()) {
            tlsLevel = TransportSecurityLevel.values()[in.readByte()];
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        minVersion.writeExternal(out);
        maxVersion.writeExternal(out);

        client.writeExternal(out);
        server.writeExternal(out);

        if (sessionHandle != null) {
            out.writeBoolean(true);
            sessionHandle.writeExternal(out);
        } else {
            out.writeBoolean(false);
        }

        if (tlsLevel != null) {
            out.writeBoolean(true);
            out.writeByte(tlsLevel.ordinal());
        } else {
            out.writeBoolean(false);
        }
    }
}
