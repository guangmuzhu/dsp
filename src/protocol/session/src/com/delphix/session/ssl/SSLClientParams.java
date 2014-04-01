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
 * This describes the SSL client parameters. All of the parameters is this class are optional.
 *
 * If no trust store is specified, the system-wide trust store will be referred to in the order defined by the java
 * runtime.
 *
 * If an ldap based certificate store is specified, it can enable sophisticated certificate validation including
 * online revocation checking.
 *
 * If blind trust is set, then no trust store or certificate store information will be used since no certificate
 * validation is necessary.
 *
 * The TLS security level set here is merely the client side offer. The actual TLS security level is determined by
 * combining the client side offer with the server side offer. If both offer TLS support and at least one of them is
 * not optional, then the resulting TLS level for the transport is the maximum of the two offers. On the other hand,
 * if both offers are optional, then TLS is disabled.
 */
public class SSLClientParams {

    private String trustStorePath;
    private String trustStorePass;

    private String ldapServer;
    private int ldapPort;

    private boolean blindTrust;
    private boolean delegateTrust;

    private TransportSecurityLevel tlsLevel;

    public SSLClientParams() {
        this(TransportSecurityLevel.OPTIONAL);
    }

    public SSLClientParams(TransportSecurityLevel tlsLevel) {
        this.setTlsLevel(tlsLevel);
    }

    public String getTrustStorePath() {
        return trustStorePath;
    }

    public void setTrustStorePath(String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

    public String getTrustStorePass() {
        return trustStorePass;
    }

    public void setTrustStorePass(String trustStorePass) {
        this.trustStorePass = trustStorePass;
    }

    public String getLdapServer() {
        return ldapServer;
    }

    public void setLdapServer(String ldapServer) {
        this.ldapServer = ldapServer;
    }

    public int getLdapPort() {
        return ldapPort;
    }

    public void setLdapPort(int ldapPort) {
        this.ldapPort = ldapPort;
    }

    public boolean isBlindTrust() {
        return blindTrust;
    }

    public void setBlindTrust(boolean blindTrust) {
        this.blindTrust = blindTrust;
    }

    public boolean isDelegateTrust() {
        return delegateTrust;
    }

    public void setDelegateTrust(boolean delegateTrust) {
        this.delegateTrust = delegateTrust;
    }

    public TransportSecurityLevel getTlsLevel() {
        return tlsLevel;
    }

    public void setTlsLevel(TransportSecurityLevel tlsLevel) {
        this.tlsLevel = tlsLevel;
    }
}
