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

import com.delphix.session.util.ObjectRegistry;

/**
 * This class implements a service type registry to keep track of the registered service types.  Any application which
 * uses the DSP should register it's service type with this registry. The key used to identify the service type depends
 * on the cardinality of the service.  For services that have a cardinality of one the service is keyed solely by the
 * service name, otherwise the key is the concatenation of the service name and service UUID. For more information
 * about service cardinality see the ServiceType class.
 */
public class ServiceTypeRegistry {

    private static final ObjectRegistry<String, ServiceType> registry = ObjectRegistry.create();

    private static String registryKey(ServiceType serviceType) {
        String key = serviceType.getName();

        if (serviceType.getCardinality() > 1) {
            key += serviceType.getUuid();
        }

        return key;
    }

    public static synchronized void register(ServiceType serviceType) {
        registry.register(registryKey(serviceType), serviceType);
    }

    public static synchronized void unregister(ServiceType serviceType) {
        registry.unregister(registryKey(serviceType));
    }

    public static synchronized ServiceType locate(String serviceName) {
        return locate(serviceName, null);
    }

    public static ServiceType locate(String serviceName, String uuid) {
        String key = serviceName;

        if (uuid != null) {
            key += uuid;
        }

        return registry.locate(key);
    }
}
