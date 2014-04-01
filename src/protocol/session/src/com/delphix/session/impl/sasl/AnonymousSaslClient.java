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
import javax.security.auth.callback.NameCallback;
import javax.security.sasl.SaslException;

/**
 * This implements the client side of the ANONYMOUS SASL mechanism defined in RFC 4505. The mechanism consists of a
 * single message from the client to the server.
 */
public class AnonymousSaslClient extends AbstractSaslClient {

    public AnonymousSaslClient(String protocol, String serverName, String authorizationId, CallbackHandler cbh) {
        super(SaslMechanism.ANONYMOUS, protocol, serverName, authorizationId, cbh);
    }

    @Override
    public boolean hasInitialResponse() {
        return true;
    }

    @Override
    protected byte[] evaluate(byte[] message) throws SaslException {
        if (message != null && message.length > 0) {
            throw new SaslException("unexpected sasl challenge");
        }

        byte[] response = getResponse();

        // Mark the SASL client completed
        setComplete();

        return response;
    }

    private byte[] getResponse() throws SaslException {
        String prompt = getMechanismName() + " authentication ID: ";

        NameCallback nc;

        if (authorizationId != null) {
            nc = new NameCallback(prompt, authorizationId);
        } else {
            nc = new NameCallback(prompt);
        }

        invokeCallbacks(nc);

        String name = nc.getName();

        // Validate the authentication ID
        AnonymousSasl.validate(name);

        return toUTF(name);
    }
}
