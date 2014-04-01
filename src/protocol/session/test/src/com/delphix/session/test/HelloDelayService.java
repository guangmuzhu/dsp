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

public class HelloDelayService implements HelloProtocolHandler, ProtocolHandlerFactory, Service {

    private ServiceType type;
    private long delay;
    private boolean nonIdempotent;

    public HelloDelayService(ServiceType type) {
        this(type, 1000);
    }

    public HelloDelayService(ServiceType type, long delay) {
        this.type = type;
        this.delay = delay;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    @Override
    public ServiceResponse hello(ServiceNexus nexus, ServiceRequest request) {
        if (!(request instanceof HelloRequest)) {
            throw new ServiceExecutionException("unknown request " + request);
        }

        HelloRequest hello = (HelloRequest) request;

        if (hello.getMessage().equals(HelloRequest.NON_IDEMPOTENT_TEST)) {
            if (nonIdempotent) {
                throw new ServiceExecutionException("non-idempotent request executed twice " + request);
            }

            nonIdempotent = true;
        }

        // Simulate prolonged execution
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            throw new ServiceExecutionException("execution interrupted", e);
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
    public List<? extends HelloProtocolHandler> getHandlers(ServiceTerminus terminus) {
        return ImmutableList.of(this);
    }

    @Override
    public Class<HelloProtocolHandler> getProtocolInterface() {
        return HelloProtocolHandler.class;
    }
}
