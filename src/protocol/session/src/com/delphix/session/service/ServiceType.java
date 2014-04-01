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

import java.util.UUID;

/**
 * This class describes the service type.
 *
 * Each service is of a specific type. The service type is identified by the UUID. The service type also has a name
 * and a version field which together must be unique within the local type registry. The service type implies the
 * service offering on both the forechannel and the backchannel.
 *
 * Service cardinality refers to the number of instances of the same service type one can register with the protocol
 * stack. A cardinality of one means only one instance is allowed and it is sufficient to refer to the service by the
 * service type in this case. A cardinality of more than one requires the client to refer to the service with an
 * additional instance UUID. Service type and instance discovery should be supported but is out of the scope here.
 *
 * A service type of cardinality one may use a simple name based service terminus based on the service type name and
 * optionally version. Such a service terminus is guaranteed to be unique locally.
 */
public class ServiceType {

    private static final int DEFAULT_VERSION = 1;
    private static final int DEFAULT_CARDINALITY = 1;

    private final UUID uuid;
    private final String name;
    private final int version;
    private final String description;
    private final int cardinality;

    public ServiceType(UUID uuid, String name, String description) {
        this(uuid, name, DEFAULT_VERSION, description, DEFAULT_CARDINALITY);
    }

    public ServiceType(UUID uuid, String name, int version, String description, int cardinality) {
        this.uuid = uuid;
        this.name = name;
        this.version = version;
        this.description = description;
        this.cardinality = cardinality;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public int getVersion() {
        return version;
    }

    public ServiceName getServiceName() {
        if (cardinality > 1) {
            throw new IllegalArgumentException("invalid service name");
        }

        if (version != DEFAULT_VERSION) {
            return new ServiceName(name + '.' + version, false);
        } else {
            return new ServiceName(name, false);
        }
    }

    public String getDescription() {
        return description;
    }

    public int getCardinality() {
        return cardinality;
    }
}
