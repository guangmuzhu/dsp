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

package com.delphix.session.module.remote.service;

import com.delphix.session.service.ServiceType;
import com.delphix.session.service.ServiceTypeRegistry;

import java.util.UUID;

public class RemoteServiceType extends ServiceType {

    private static final RemoteServiceType instance = new RemoteServiceType();

    private static final String REMOTE_SERVICE_NAME = "Remote";
    private static final String REMOTE_SERVICE_DESC = "Remote Service";
    private static final UUID REMOTE_SERVICE_UUID = UUID.fromString("71ce958f-dc5a-4cc1-8a7f-d6a862d2ce8f");

    private RemoteServiceType() {
        super(REMOTE_SERVICE_UUID, REMOTE_SERVICE_NAME, REMOTE_SERVICE_DESC);
    }

    static {
        // Register the file service type with the service type registry
        ServiceTypeRegistry.register(instance);
    }

    public static ServiceType getInstance() {
        return instance;
    }
}
