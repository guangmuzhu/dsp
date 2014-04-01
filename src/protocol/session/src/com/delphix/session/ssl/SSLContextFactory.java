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

import javax.net.ssl.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.*;

/**
 * This is the factory class for the server and client SSL context.
 */
public class SSLContextFactory {

    private static final String KEY_STORE_TYPE = "JKS";
    private static final String CERT_STORE_TYPE = "LDAP";
    private static final String SSL_PROTOCOL = "TLS";

    /**
     * Create SSL server context with the specified parameters.
     * @throws IOException
     * @throws GeneralSecurityException
     */
    public static SSLServerContext getServerContext(SSLServerParams params)
            throws GeneralSecurityException, IOException {
        KeyStore keyStore = loadKeyStore(params.getKeyStorePath(), params.getKeyStorePass());
        KeyManager[] keyManagers = getKeyManagers(keyStore, params.getKeyPass());

        SSLContext context = SSLContext.getInstance(SSL_PROTOCOL);
        context.init(keyManagers, null, null);

        return new SSLServerContext(params, context);
    }

    /**
     * Create SSL client context with the specified parameters.
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public static SSLClientContext getClientContext(SSLClientParams params)
            throws GeneralSecurityException, IOException {
        // Create a client context with blind trust management
        if (params.isBlindTrust()) {
            return getClientContext(BlindTrustManager.getTrustManagers(), params);
        }

        /*
         * Create SSL client context using system defined truststore if truststore path is not specified.
         *
         *  - javax.net.ssl.trustStore and javax.net.ssl.trustStorePassword
         *  - <java-home>/lib/security/jssecacerts
         *  - <java-home>/lib/security/cacerts
         *
         * A truststore is selected in the order above. If the system property javax.net.ssl.trustStore is defined
         * but a truststore doesn't exist, a default trust manager is created with an empty truststore instead.
         */
        KeyStore trustStore = null;

        if (params.getTrustStorePath() != null) {
            trustStore = loadKeyStore(params.getTrustStorePath(), params.getTrustStorePass());
        }

        /*
         * Create a LDAP based certificate store if specified. With the certificate store, trust management shall
         * work to its fullest extent. In particular, it will enable certificate path construction and certificate
         * revocation check.
         */
        CertStore certStore = null;

        if (params.getLdapServer() != null) {
            certStore = loadCertStore(params.getLdapServer(), params.getLdapPort());
        }

        // Get the default trust manager with the truststore and certstore
        TrustManager[] trustManagers = getTrustManagers(trustStore, certStore);

        // Create a delegate trust manager if requested
        if (params.isDelegateTrust()) {
            TrustManager[] delegateManagers = null;

            for (TrustManager trustManager : trustManagers) {
                if (trustManager instanceof X509TrustManager) {
                    delegateManagers = DelegateTrustManager.getTrustManagers((X509TrustManager) trustManager);
                    break;
                }
            }

            if (delegateManagers != null) {
                trustManagers = delegateManagers;
            }
        }

        return getClientContext(trustManagers, params);
    }

    private static SSLClientContext getClientContext(TrustManager[] tms, SSLClientParams params)
            throws GeneralSecurityException {
        SSLContext context = SSLContext.getInstance(SSL_PROTOCOL);
        context.init(null, tms, null);

        return new SSLClientContext(params, context);
    }

    private static TrustManager[] getTrustManagers(KeyStore trustStore, CertStore certStore)
            throws GeneralSecurityException {
        PKIXParameters pkixParams = new PKIXBuilderParameters(trustStore, new X509CertSelector());

        // Specify that revocation checking is to be enabled if a certstore is provided
        if (certStore != null) {
            pkixParams.addCertStore(certStore);
            pkixParams.setRevocationEnabled(true);
        }

        // Wrap them as trust manager parameters
        ManagerFactoryParameters trustParams = new CertPathTrustManagerParameters(pkixParams);

        // Create TrustManagerFactory for PKIX-compliant trust managers
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        // Pass parameters to factory to be passed to CertPath implementation
        factory.init(trustParams);

        return factory.getTrustManagers();
    }

    private static KeyManager[] getKeyManagers(KeyStore keyStore, String keyPass) throws GeneralSecurityException {
        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore, keyPass.toCharArray());
        return factory.getKeyManagers();
    }

    private static KeyStore loadKeyStore(String keyStorePath, String keyStorePass)
            throws GeneralSecurityException, IOException {
        InputStream kis = open(keyStorePath);

        KeyStore keyStore;

        try {
            keyStore = KeyStore.getInstance(KEY_STORE_TYPE);
            keyStore.load(kis, keyStorePass.toCharArray());
        } finally {
            kis.close();
        }

        return keyStore;
    }

    private static CertStore loadCertStore(String server, int port) throws GeneralSecurityException {
        LDAPCertStoreParameters ldapParams = new LDAPCertStoreParameters(server, port);
        return CertStore.getInstance(CERT_STORE_TYPE, ldapParams);
    }

    private static InputStream open(String path) throws IOException {
        URL url;

        try {
            url = new URL(path);
            return url.openStream();
        } catch (MalformedURLException e) {
            // Do nothing
        }

        try {
            File file = new File(path);

            if (file.exists() && file.isFile()) {
                url = file.toURI().toURL();
                return url.openStream();
            }
        } catch (MalformedURLException e) {
            // Do nothing
        }

        url = Thread.currentThread().getContextClassLoader().getResource(path);

        if (url != null) {
            return url.openStream();
        }

        throw new MalformedURLException(path);
    }
}
