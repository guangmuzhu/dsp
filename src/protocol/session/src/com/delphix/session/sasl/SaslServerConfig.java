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

package com.delphix.session.sasl;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import java.util.HashSet;
import java.util.Set;

/**
 * This class describes the SASL specific configuration for a protocol server.
 *
 * Services using SASL for authentication should each contain an instance of SaslServerConfig. The SASL server
 * configuration specifies the list of SASL mechanisms supported by the service among other things. The list is
 * communicated to the client during the initial protocol handshake prior to the SASL challenge and response
 * exchanges.
 */
public class SaslServerConfig {

    private final Set<ServerSaslMechanism> mechanisms = new HashSet<ServerSaslMechanism>();

    private final String protocol;
    private final String server;

    public SaslServerConfig(String protocol, String server) {
        this.protocol = protocol;
        this.server = server;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getServer() {
        return server;
    }

    public ServerSaslMechanism getMechanism(String name) {
        for (ServerSaslMechanism mechanism : mechanisms) {
            if (mechanism.getMechanism().equals(name)) {
                return mechanism;
            }
        }

        return null;
    }

    public Set<ServerSaslMechanism> getMechanisms() {
        return mechanisms;
    }

    public boolean addMechanism(ServerSaslMechanism mechanism) {
        return mechanisms.add(mechanism);
    }

    public boolean removeMechanism(String name) {
        ServerSaslMechanism mechanism = getMechanism(name);

        if (mechanism != null) {
            return mechanisms.remove(mechanism);
        }

        return false;
    }

    public SaslServer create(String name) throws SaslException {
        ServerSaslMechanism mechanism = getMechanism(name);

        if (mechanism == null) {
            return null;
        }

        return mechanism.create(protocol, server);
    }
}
