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

import com.delphix.session.sasl.AuthenticateCallback;
import com.delphix.session.sasl.SaslMechanism;
import org.apache.commons.lang.ArrayUtils;

import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.SaslException;

/**
 * This implements the server side of the PLAIN SASL mechanism defined in RFC 4616. The mechanism consists of a
 * single message from the client to the server.
 */
public class PlainSaslServer extends AbstractSaslServer {

    public PlainSaslServer(String protocol, String server, CallbackHandler cbh) {
        super(SaslMechanism.PLAIN, protocol, server, cbh);
    }

    @Override
    protected byte[] evaluate(byte[] message) throws SaslException {
        // Parse the SASL message
        String[] userInfo = parse(message);

        // Perform authentication
        String prompt = getMechanismName() + " authentication ID: ";
        NameCallback nc = new NameCallback(prompt, userInfo[1]);
        AuthenticateCallback ac = new AuthenticateCallback(userInfo[2]);

        invokeCallbacks(nc, ac);

        if (!ac.isAuthenticated()) {
            throw new SaslException("sasl authentication failed");
        }

        // Perform authorization
        AuthorizeCallback az = new AuthorizeCallback(userInfo[1], userInfo[0]);

        invokeCallbacks(az);

        if (az.isAuthorized()) {
            authorizationId = az.getAuthorizedID();
        } else {
            throw new SaslException();
        }

        // Mark the SASL server completed
        setComplete();

        return null;
    }

    public String[] parse(byte[] message) throws SaslException {
        // Validate the SASL message
        PlainSasl.validate(message);

        // Append separator to the end of the message
        message = ArrayUtils.add(message, PlainSasl.SEPARATOR_BYTE);

        // Parse the user info formatted as value + SEPARATOR
        String[] userInfo = new String[3];

        byte[] segment;
        int beginIndex = 0;
        int endIndex;

        for (int i = 0; i < userInfo.length; i++) {
            endIndex = ArrayUtils.indexOf(message, PlainSasl.SEPARATOR_BYTE, beginIndex);

            if (endIndex < 0) {
                throw new SaslException("invalid sasl message");
            } else {
                segment = ArrayUtils.subarray(message, beginIndex, endIndex);
                userInfo[i] = fromUTF(segment);
            }

            beginIndex = endIndex + 1;
        }

        // Check if there is anything else beyond the last separator
        if (beginIndex < message.length) {
            throw new SaslException("invalid sasl message");
        }

        // Validate the user info
        PlainSasl.validate(userInfo);

        return userInfo;
    }
}
