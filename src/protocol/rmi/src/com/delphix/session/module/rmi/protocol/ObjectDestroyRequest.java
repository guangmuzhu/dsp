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
 * Copyright (c) 2014 by Delphix. All rights reserved.
 */

package com.delphix.session.module.rmi.protocol;

import com.delphix.session.module.rmi.RmiProtocolServer;
import com.delphix.session.service.ServiceNexus;
import com.delphix.session.service.ServiceResponse;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.UUID;

public class ObjectDestroyRequest extends AbstractRmiRequest {

    private UUID objectId;

    public ObjectDestroyRequest() {
        super(ObjectDestroyRequest.class.getSimpleName());
    }

    public void setObjectId(UUID objectId) {
        this.objectId = objectId;
    }

    public UUID getObjectId() {
        return objectId;
    }

    @Override
    public ServiceResponse execute(ServiceNexus nexus) {
        return nexus.getProtocolHandler(RmiProtocolServer.class).destroyObject(this, nexus);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        objectId = UUID.fromString(in.readUTF());
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeUTF(objectId.toString());
    }

    @Override
    public String toString() {
        return String.format("%s objectId=%s", super.toString(), objectId.toString());
    }
}
