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

package com.delphix.session.service;

import org.apache.commons.lang.builder.HashCodeBuilder;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * This class describes a unique identifier for a service nexus.
 */
public class ServiceNexusID implements Externalizable {

    private ServiceTerminus client;
    private ServiceTerminus server;

    private ServiceNexusID() {

    }

    public ServiceNexusID(ServiceNexusID id) {
        this(id.client, id.server);
    }

    public ServiceNexusID(ServiceTerminus client, ServiceTerminus server) {
        this.client = client;
        this.server = server;
    }

    public ServiceTerminus getClient() {
        return client;
    }

    public ServiceTerminus getServer() {
        return server;
    }

    @Override
    public String toString() {
        return "nexus:" + client + "-" + server;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof ServiceNexusID)) {
            return false;
        }

        ServiceNexusID id = (ServiceNexusID) obj;

        return id.client.equals(client) && id.server.equals(server);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(client).append(server).toHashCode();
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        client = AbstractTerminus.deserialize(in);
        server = AbstractTerminus.deserialize(in);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        client.writeExternal(out);
        server.writeExternal(out);
    }

    public ServiceNexusID deserialize(ObjectInput in) throws IOException, ClassNotFoundException {
        ServiceNexusID nexusID = new ServiceNexusID();
        nexusID.readExternal(in);
        return nexusID;
    }
}
