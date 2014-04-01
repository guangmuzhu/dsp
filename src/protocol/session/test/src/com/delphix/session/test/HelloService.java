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

package com.delphix.session.test;

import com.delphix.session.service.*;
import com.google.common.collect.ImmutableList;

import java.util.List;

public class HelloService implements HelloProtocolHandler, ProtocolHandlerFactory, Service {

    private ServiceType type;

    public HelloService(ServiceType type) {
        this.type = type;
    }

    @Override
    public ServiceResponse hello(ServiceNexus nexus, ServiceRequest request) {
        if (!(request instanceof HelloRequest)) {
            throw new ServiceExecutionException("unknown request " + request);
        }
        return new HelloResponse();
    }

    @Override
    public ServiceType getType() {
        return type;
    }

    @Override
    public ServiceCodec getCodec() {
        return HelloCodec.getInstance();
    }

    @Override
    public List<HelloProtocolHandler> getHandlers(ServiceTerminus terminus) {
        return ImmutableList.<HelloProtocolHandler> of(this);
    }

    @Override
    public Class<HelloProtocolHandler> getProtocolInterface() {
        return HelloProtocolHandler.class;
    }
}
