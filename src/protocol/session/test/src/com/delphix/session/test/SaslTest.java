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

package com.delphix.session.test;

import com.delphix.appliance.logger.Logger;
import com.delphix.session.sasl.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.security.sasl.*;

import java.util.Enumeration;

import static com.delphix.session.service.ServiceProtocol.PROTOCOL;
import static org.testng.Assert.fail;

public class SaslTest extends SessionBaseTest {

    private static final Logger logger = Logger.getLogger(SaslTest.class);

    private static String SERVER = "server.domain";

    private UserDatabase userDB;
    private PasswordAuthenticator authenticator;

    private SaslServerConfig config;

    private SaslServer server;
    private SaslClient client;

    @BeforeClass
    public void init() {
        initClient();
        initServer();
    }

    @AfterClass
    public void fini() {

    }

    public void initClient() {
        // List all SASL mechanisms supported by the client providers
        Enumeration<SaslClientFactory> factories = Sasl.getSaslClientFactories();

        while (factories.hasMoreElements()) {
            SaslClientFactory factory = factories.nextElement();

            String[] mechanisms = factory.getMechanismNames(null);
            for (int i = 0; i < mechanisms.length; i++) {
                logger.infof("%s: %s", factory.getClass().getName(), mechanisms[i]);
            }
        }
    }

    public void initServer() {
        // List all SASL mechanisms supported by the server providers
        Enumeration<SaslServerFactory> factories = Sasl.getSaslServerFactories();

        while (factories.hasMoreElements()) {
            SaslServerFactory factory = factories.nextElement();

            String[] mechanisms = factory.getMechanismNames(null);
            for (int i = 0; i < mechanisms.length; i++) {
                logger.infof("%s: %s", factory.getClass().getName(), mechanisms[i]);
            }
        }

        // Create a server with multiple SASL mechanisms
        config = new SaslServerConfig(PROTOCOL, SERVER);

        userDB = new UserDatabase();
        authenticator = new PasswordAuthenticator() {

            @Override
            public boolean authenticate(String username, String password) {
                return username.equals(TEST_USERNAME) && password.equals(TEST_PASSWORD);
            }
        };

        config.addMechanism(new DigestServer(userDB, userDB, userDB.getRealms()));
        config.addMechanism(new CramServer(userDB, userDB));
        config.addMechanism(new AnonymousServer());
        config.addMechanism(new PlainServer(authenticator, userDB));
    }

    @Test
    public void testAnonymous() throws SaslException {
        ClientSaslMechanism auth = new AnonymousClient("megatron@autobot.com");
        authenticate(auth);
    }

    @Test
    public void testAnonymousInvalidUser() throws SaslException {
        ClientSaslMechanism auth = new AnonymousClient("megatron@autobot");

        try {
            authenticate(auth);
            fail("exception expected but not thrown");
        } catch (Exception e) {
            // authentication exception expected
        }
    }

    @Test
    public void testPlain() throws SaslException {
        ClientSaslMechanism auth = new PlainClient(TEST_USERNAME, TEST_PASSWORD);
        authenticate(auth);
    }

    @Test
    public void testPlainInvalidUser() throws SaslException {
        ClientSaslMechanism auth = new PlainClient(TEST_INVALID_USERNAME, TEST_PASSWORD);

        try {
            authenticate(auth);
            fail("exception expected but not thrown");
        } catch (Exception e) {
            // authentication exception expected
        }
    }

    @Test
    public void testCram() throws SaslException {
        ClientSaslMechanism auth = new CramClient("username", "password");
        authenticate(auth);
    }

    @Test
    public void testDigest() throws SaslException {
        ClientSaslMechanism auth = new DigestClient("username", "password", "server.realm");
        authenticate(auth);
    }

    @Test
    public void testDigestOtherDomain() throws SaslException {
        ClientSaslMechanism auth = new DigestClient("foo", "foo", "foo.realm");
        authenticate(auth);
    }

    @Test
    public void testDigestInvalidDomain() throws SaslException {
        ClientSaslMechanism auth = new DigestClient("username", "password", "duh.realm");

        try {
            authenticate(auth);
            fail("exception expected but not thrown");
        } catch (Exception e) {
            // authentication exception expected
        }
    }

    @Test
    public void testDigestInvalidUser() throws SaslException {
        ClientSaslMechanism auth = new DigestClient("duh", "duh", "server.realm");

        try {
            authenticate(auth);
            fail("exception expected but not thrown");
        } catch (Exception e) {
            // authentication exception expected
        }
    }

    private void authenticate(ClientSaslMechanism auth) throws SaslException {
        client = auth.create(PROTOCOL, SERVER, null);

        if (client == null) {
            throw new SaslException("client mechanism not supported");
        }

        // Generate the first SASL response if any
        byte[] response = client.hasInitialResponse() ? client.evaluateChallenge(new byte[0]) : new byte[0];

        // Send the selected mechanism and the first response if any and wait for the server message
        SaslMessage message = sendInitialResponse(client.getMechanismName(), response);

        while (message.getException() == null) {
            // Client is not expecting any challenge from server if it is complete
            if (client.isComplete()) {
                if (message.getChallenge() != null) {
                    throw new SaslException("unexpected server sasl challenge");
                }

                return;
            }

            // Regardless if the server is complete or not, challenge must not be null if the client is incomplete
            if (message.getChallenge() == null) {
                throw new SaslException("missing server sasl challenge");
            }

            response = client.evaluateChallenge(message.getChallenge());

            // Client must be complete and must not generate any response if the server is complete
            if (message.isSuccess()) {
                if (response != null) {
                    throw new SaslException("unexpected client sasl response");
                }

                if (!client.isComplete()) {
                    throw new SaslException("incomplete client sasl negotiation");
                }

                return;
            }

            // Regardless if the client is complete or not, send the response if the server is incomplete
            message = sendResponse(response);
        }

        if (message.getException() != null) {
            throw message.getException();
        }
    }

    private SaslMessage sendInitialResponse(String mechanism, byte[] response) {
        try {
            // Create a SASL server with the client selected mechanism
            server = config.create(mechanism);

            if (server == null) {
                return new SaslMessage(new SaslException("unsupported sasl mechanism"));
            }
        } catch (SaslException e) {
            return new SaslMessage(e);
        }

        // Process the client SASL response
        return sendResponse(response);
    }

    private SaslMessage sendResponse(byte[] response) {
        byte[] challenge;

        // The client SASL response must not be null
        if (response == null) {
            return new SaslMessage(new SaslException("missing client sasl response"));
        }

        try {
            challenge = server.evaluateResponse(response);
        } catch (SaslException e) {
            return new SaslMessage(e);
        }

        if (server.isComplete()) {
            return new SaslMessage(challenge, true);
        }

        return new SaslMessage(challenge, false);
    }

    private class SaslMessage {

        private byte[] challenge;
        private boolean success;
        private SaslException exception;

        public SaslMessage(SaslException exception) {
            this.exception = exception;
        }

        public SaslMessage(byte[] challenge, boolean success) {
            this.challenge = challenge;
            this.success = success;
        }

        public SaslException getException() {
            return exception;
        }

        public boolean isSuccess() {
            return success;
        }

        public byte[] getChallenge() {
            return challenge;
        }
    }
}
