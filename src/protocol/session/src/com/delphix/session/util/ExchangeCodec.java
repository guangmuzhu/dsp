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

package com.delphix.session.util;

import com.delphix.session.service.ServiceExchange;

import java.io.*;

/**
 * This is a concrete implementation of a service exchange codec based on the Java Externalizable mechanism. It
 * relies on a user specified exchange type registry for mappings between internal Java class and wire type.
 */
public class ExchangeCodec extends AbstractExternalCodec {

    private ExchangeRegistry<?> registry;

    public ExchangeCodec(ExchangeRegistry<?> registry) {
        this.registry = registry;
    }

    @Override
    protected void encodeExchange(OutputStream out, ServiceExchange exchange) throws IOException {
        ObjectOutput oout = new ExternalObjectOutput(out);

        // Encode the object type
        oout.writeInt(registry.getObjectType(exchange.getClass()));

        // Encode the object content
        Externalizable command = (Externalizable) exchange;
        command.writeExternal(oout);
    }

    @Override
    protected ServiceExchange decodeExchange(InputStream in) throws IOException, ClassNotFoundException {
        ObjectInput oin = new ExternalObjectInput(in);

        // Decode the object type
        ServiceExchange exchange = createExchange(registry.getObjectClass(oin.readInt()));

        // Decode the object content
        Externalizable command = (Externalizable) exchange;
        command.readExternal(oin);

        return exchange;
    }

    @Override
    protected boolean claimsExchange(ServiceExchange exchange) {
        try {
            registry.getObjectType(exchange.getClass());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
