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

package com.delphix.session.ssl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

/**
 * This describes the server side SSL context. To create a server side SSL context, use one of the SSLContextFactory
 * static methods.
 *
 * For SSL to work, the server side requires a keystore with the server's public key (wrapped in a certificate chain)
 * and private key pair. The keystore and the private key are protected with different passwords. The server sends its
 * certificate chain to the client during the initial SSL handshake and the client must verify the certificate chain
 * via the installed certificate trust manager to prevent man-in-the-middle attack.
 *
 * The server side SSL context also specifies whether a protocol client is required to use SSL for encryption. When
 * set to true, a client that chooses not to use SSL when connecting to the server shall be rejected.
 *
 * From the SSL server context, one may create instances of SSL engine to be used for the initial SSL protocol
 * negotiation and for subsequent security layer function.
 */
public class SSLServerContext {

    private final SSLServerParams params;
    private final SSLContext context;

    SSLServerContext(SSLServerParams params, SSLContext context) {
        this.params = params;
        this.context = context;
    }

    public TransportSecurityLevel getTlsLevel() {
        return params.getTlsLevel();
    }

    public void setTlsLevel(TransportSecurityLevel tlsLevel) {
        params.setTlsLevel(tlsLevel);
    }

    public String getKeyStorePath() {
        return params.getKeyStorePath();
    }

    public String getKeyStorePass() {
        return params.getKeyStorePass();
    }

    public String getKeyPass() {
        return params.getKeyPass();
    }

    public SSLEngine create() {
        SSLEngine engine = context.createSSLEngine();

        // This is for server side use only
        engine.setUseClientMode(false);

        // Set client authentication to neither required nor desired
        engine.setNeedClientAuth(false);
        engine.setWantClientAuth(false);

        return engine;
    }
}
