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

package com.delphix.session.impl.sasl;

import com.delphix.session.sasl.SaslMechanism;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslException;

/**
 * This implements the server side of the ANONYMOUS SASL mechanism defined in RFC 4505. The mechanism consists of a
 * single message from the client to the server.
 */
public class AnonymousSaslServer extends AbstractSaslServer {

    public static final String AUTHORIZATION_ID = "anonymous";

    public AnonymousSaslServer(String protocol, String serverName, CallbackHandler cbh) {
        super(SaslMechanism.ANONYMOUS, protocol, serverName, cbh);

        authorizationId = AUTHORIZATION_ID;
    }

    @Override
    protected byte[] evaluate(byte[] message) throws SaslException {
        // Validate the SASL message
        AnonymousSasl.validate(message);

        String name = fromUTF(message);

        // Validate the authentication ID
        AnonymousSasl.validate(name);

        // Mark the SASL server completed
        setComplete();

        return null;
    }
}
