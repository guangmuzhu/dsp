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

package com.delphix.session.module.rmi.impl;

import com.delphix.session.module.rmi.ObjectCreator;
import com.delphix.session.module.rmi.Referable;
import com.delphix.session.module.rmi.RmiProtocolServer;

import com.delphix.session.module.rmi.protocol.*;
import com.delphix.session.service.ServiceNexus;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RmiProtocolServerImpl implements RmiProtocolServer {

    private final ObjectCreator objectCreator;
    private final ConcurrentMap<UUID, ExportedObjectInfo> exportedObjects =
            new ConcurrentHashMap<UUID, ExportedObjectInfo>();

    private static class ExportedObjectInfo {
        private Object value;
        private RmiMethodOrdering ifm;
    }

    public RmiProtocolServerImpl(ObjectCreator objectCreator) {
        this.objectCreator = objectCreator;
    }

    @Override
    public ObjectCreateResponse createObject(ObjectCreateRequest request, ServiceNexus nexus) {
        ExportedObjectInfo info = new ExportedObjectInfo();
        info.value = objectCreator.create(request.getRequest());
        info.ifm = new RmiMethodOrdering(request.getRequest());
        UUID objectId;
        do {
            objectId = UUID.randomUUID();
        } while (exportedObjects.putIfAbsent(objectId, info) != null);

        ObjectCreateResponse response = new ObjectCreateResponse();
        response.setObjectId(objectId);
        return response;
    }

    @Override
    public MethodCallResponse callMethod(MethodCallRequest request, ServiceNexus nexus) {
        ExportedObjectInfo info = exportedObjects.get(request.getObjectId());
        if (info == null) {
            throw new RuntimeException("no object exported:  " + request.getObjectId());
        }

        Method method = info.ifm.getMethod(request.getMethod());
        MethodCallResponse response = new MethodCallResponse();
        try {
            Object value = method.invoke(info.value, request.getArguments());
            if (value instanceof Referable) {
                ExportedObjectInfo refInfo = new ExportedObjectInfo();
                refInfo.value = value;
                refInfo.ifm = new RmiMethodOrdering(method.getReturnType());
                UUID objectId;
                do {
                    objectId = UUID.randomUUID();
                } while (exportedObjects.putIfAbsent(objectId, refInfo) != null);
                ((Referable) value).setObjectId(objectId);
            }
            response.setValue(value);
            response.setException(false);
            return response;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            response.setValue(e.getCause());
            response.setException(true);
            return response;
        }
    }

    @Override
    public ObjectDestroyResponse destroyObject(ObjectDestroyRequest request, ServiceNexus nexus) {
        Object obj = exportedObjects.remove(request.getObjectId());
        if (obj == null) {
            throw new RuntimeException("no object exported:  " + request.getObjectId());
        }
        return new ObjectDestroyResponse();
    }

    @Override
    public Class<RmiProtocolServer> getProtocolInterface() {
        return RmiProtocolServer.class;
    }
}
