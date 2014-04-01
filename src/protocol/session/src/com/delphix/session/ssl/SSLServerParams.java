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

/**
 * This describes the SSL server parameters.
 *
 * The server must specify key store information so the SSL runtime can locate and decipher the server certificate
 * and private key used during the SSL handshake.
 *
 * The TLS security level set here is merely the server side offer. The actual TLS security level is determined by
 * combining the client side offer with the server side offer. If both offer TLS support and at least one of them is
 * not optional, then the resulting TLS level for the transport is the maximum of the two offers. On the other hand,
 * if both offers are optional, then TLS is disabled.
 */
public class SSLServerParams {

    private String keyStorePath;
    private String keyStorePass;
    private String keyPass;

    private TransportSecurityLevel tlsLevel;

    public SSLServerParams() {
        this(null, null, null);
    }

    public SSLServerParams(String keyStorePath, String keyStorePass, String keyPass) {
        this(keyStorePath, keyStorePass, keyPass, TransportSecurityLevel.OPTIONAL);
    }

    public SSLServerParams(String keyStorePath, String keyStorePass, String keyPass, TransportSecurityLevel tlsLevel) {
        this.keyStorePath = keyStorePath;
        this.keyStorePass = keyStorePass;
        this.keyPass = keyPass;

        this.tlsLevel = tlsLevel;
    }

    public String getKeyStorePath() {
        return keyStorePath;
    }

    public void setKeyStorePath(String keyStorePath) {
        this.keyStorePath = keyStorePath;
    }

    public String getKeyStorePass() {
        return keyStorePass;
    }

    public void setKeyStorePass(String keyStorePass) {
        this.keyStorePass = keyStorePass;
    }

    public String getKeyPass() {
        return keyPass;
    }

    public void setKeyPass(String keyPass) {
        this.keyPass = keyPass;
    }

    public TransportSecurityLevel getTlsLevel() {
        return tlsLevel;
    }

    public void setTlsLevel(TransportSecurityLevel tlsLevel) {
        this.tlsLevel = tlsLevel;
    }
}
