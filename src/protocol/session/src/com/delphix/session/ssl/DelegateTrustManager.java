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

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * This is the delegate trust manager. As the name implies, it delegates the main trust management functionality to
 * another (the default) trust manager. It provides alternative authentication logic only if the default fails. For
 * example, it may be used to handle dynamic keystore updates. When a checkClientTrusted or checkServerTrusted test
 * fails and does not establish a trusted certificate chain, the required trusted certificate may be added to the
 * truststore.
 */
public class DelegateTrustManager implements X509TrustManager {

    private X509TrustManager trustManager;

    public DelegateTrustManager(X509TrustManager trustManager) {
        this.trustManager = trustManager;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        try {
            trustManager.checkClientTrusted(chain, authType);
        } catch (CertificateException e) {
            // Nothing to do since we don't authenticate the client
            throw e;
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        try {
            trustManager.checkServerTrusted(chain, authType);
        } catch (CertificateException e) {
            /*
             * Handle server certificate chain verification failure. For example, we may recover from the failure by
             * refreshing the trust store via a user provided hook and therefore the trust manager.
             *
             * For now, just rethrow.
             */
            throw e;
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return trustManager.getAcceptedIssuers();
    }

    public static TrustManager[] getTrustManagers(X509TrustManager trustManager) {
        return new TrustManager[] { new DelegateTrustManager(trustManager) };
    }
}
