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
 * This describes the client side SSL context. To create a client side SSL context, use one of the SSLContextFactory
 * static methods.
 *
 * The server sends its certificate chain to the client during the initial SSL handshake and the client must verify
 * the certificate chain via its installed certificate trust manager to prevent man-in-the-middle attack. In order to
 * do that, the client must have access to a truststore containing certificates of the CAs that it trusts, a.k.a.,
 * trust anchors. One of the installed trust anchors must be at the end of the server's certificate chain for the
 * verification to be successful.
 *
 * The source of the truststore may vary. A truststore is instantiated from the source according to the following
 * precedence.
 *
 *  1) user specified truststore and truststore password
 *  2) javax.net.ssl.trustStore and javax.net.ssl.trustStorePassword
 *  3) <java-home>/lib/security/jssecacerts
 *  4) <java-home>/lib/security/cacerts
 *
 * The client may optionally include a certificate store. With the certificate store, trust management shall work to
 * its fullest extent. Specifically, it enables certificate path construction and certificate revocation check.
 *
 * In addition to the certificate related configuration, the client may also choose among several trust management
 * models that range from blind trust to the default PKIX trust manager to a delegate trust manager that is capable
 * of limited failure recovery.
 *
 * From the SSL client context, one may create instances of SSL engine to be used for the initial SSL protocol
 * negotiation and for subsequent security layer function.
 */
public class SSLClientContext {

    private SSLClientParams params;
    private SSLContext context;

    SSLClientContext(SSLClientParams params, SSLContext context) {
        this.params = params;
        this.context = context;
    }

    public String getTrustStorePath() {
        return params.getTrustStorePath();
    }

    public String getTrustStorePass() {
        return params.getTrustStorePass();
    }

    public String getLdapServer() {
        return params.getLdapServer();
    }

    public int getLdapPort() {
        return params.getLdapPort();
    }

    public boolean isBlindTrust() {
        return params.isBlindTrust();
    }

    public boolean isDelegateTrust() {
        return params.isDelegateTrust();
    }

    public TransportSecurityLevel getTlsLevel() {
        return params.getTlsLevel();
    }

    public SSLEngine create() {
        SSLEngine engine = context.createSSLEngine();

        // This is for client side use only
        engine.setUseClientMode(true);

        return engine;
    }
}
