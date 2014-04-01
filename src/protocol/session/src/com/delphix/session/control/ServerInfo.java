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

package com.delphix.session.control;

import com.delphix.session.ssl.TransportSecurityLevel;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

public class ServerInfo implements Serializable {

    private String terminus;

    private List<String> saslMechanisms;
    private TransportSecurityLevel tlsLevel;
    private List<InetAddress> endpoints;
    private Map<String, ?> options;

    private List<String> clients;

    public ServerInfo() {

    }

    public String getTerminus() {
        return terminus;
    }

    public void setTerminus(String terminus) {
        this.terminus = terminus;
    }

    public List<String> getSaslMechanisms() {
        return saslMechanisms;
    }

    public void setSaslMechanisms(List<String> saslMechanisms) {
        this.saslMechanisms = saslMechanisms;
    }

    public TransportSecurityLevel getTlsLevel() {
        return tlsLevel;
    }

    public void setTlsLevel(TransportSecurityLevel tlsLevel) {
        this.tlsLevel = tlsLevel;
    }

    public List<InetAddress> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<InetAddress> endpoints) {
        this.endpoints = endpoints;
    }

    public Map<String, ?> getOptions() {
        return options;
    }

    public void setOptions(Map<String, ?> options) {
        this.options = options;
    }

    public List<String> getClients() {
        return clients;
    }

    public void setClients(List<String> clients) {
        this.clients = clients;
    }
}
