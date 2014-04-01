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

import com.delphix.session.ssl.TransportSecurityLevel;
import com.delphix.session.util.ProtocolVersion;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

/**
 * This class describes the initial login connect response.
 *
 *   o actVersion
 *   o maxVersion
 *
 * The server responds with the highest version that it supports as well as the active version chosen for this login.
 *
 * The active version is typically chosen as the maximum version that both the client and the server support. However,
 * for subsequent connections, server may choose the active version for the session if that falls in the version range
 * for the connection.
 *
 *   o sessionHandle
 *
 * The server returns an opaque session handle that may be used to refer to the session being logged into.
 *
 *   o saslMechanisms
 *
 * The server returns a list of SASL mechanisms that it supports for authentication. The client may choose one from
 * the list as it starts the SASL login phase.
 *
 *   o tlsLevel
 *
 * The server specifies the TLS encryption level chosen for the transport which is based on the client offer as well
 * as the server configuration.
 */
public class ConnectResponse extends LoginResponse {

    private ProtocolVersion actVersion;
    private ProtocolVersion maxVersion;

    private SessionHandle sessionHandle;

    private List<String> saslMechanisms;

    private TransportSecurityLevel tlsLevel;

    public ConnectResponse() {
        super();
    }

    @Override
    public LoginPhase getPhase() {
        return LoginPhase.CONNECT;
    }

    public ProtocolVersion getActVersion() {
        return actVersion;
    }

    public void setActVersion(ProtocolVersion actVersion) {
        this.actVersion = actVersion;
    }

    public ProtocolVersion getMaxVersion() {
        return maxVersion;
    }

    public void setMaxVersion(ProtocolVersion maxVersion) {
        this.maxVersion = maxVersion;
    }

    public SessionHandle getSessionHandle() {
        return sessionHandle;
    }

    public void setSessionHandle(SessionHandle sessionHandle) {
        this.sessionHandle = sessionHandle;
    }

    public List<String> getSaslMechanisms() {
        return saslMechanisms;
    }

    public void setSaslMechanisms(List<String> saslMechanisms) {
        this.saslMechanisms = saslMechanisms;
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

        if (status != LoginStatus.SUCCESS) {
            return;
        }

        actVersion = ProtocolVersion.deserialize(in);
        maxVersion = ProtocolVersion.deserialize(in);

        sessionHandle = SessionHandle.deserialize(in);

        saslMechanisms = new ArrayList<String>();

        int size = in.readInt();

        for (int i = 0; i < size; i++) {
            saslMechanisms.add(in.readUTF());
        }

        if (in.readBoolean()) {
            tlsLevel = TransportSecurityLevel.values()[in.readByte()];
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        if (status != LoginStatus.SUCCESS) {
            return;
        }

        actVersion.writeExternal(out);
        maxVersion.writeExternal(out);

        sessionHandle.writeExternal(out);

        out.writeInt(saslMechanisms.size());

        for (String mechanism : saslMechanisms) {
            out.writeUTF(mechanism);
        }

        if (tlsLevel != null) {
            out.writeBoolean(true);
            out.writeByte(tlsLevel.ordinal());
        } else {
            out.writeBoolean(false);
        }
    }
}
