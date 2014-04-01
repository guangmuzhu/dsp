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

import com.delphix.appliance.server.exception.DelphixInterruptedException;
import com.delphix.appliance.server.util.ExceptionUtil;
import com.delphix.session.module.rmi.ObjectCreator;
import com.delphix.session.module.rmi.RmiFactory;
import com.delphix.session.module.rmi.RmiProtocolClient;
import com.delphix.session.module.rmi.RmiProtocolServer;
import com.delphix.session.module.rmi.protocol.MethodCallRequest;
import com.delphix.session.module.rmi.protocol.MethodCallResponse;
import com.delphix.session.module.rmi.protocol.ObjectDestroyRequest;
import com.delphix.session.service.ServiceNexus;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class RmiFactoryImpl implements RmiFactory, ApplicationContextAware {

    private ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        this.context = context;
    }

    @Override
    public RmiProtocolClient createClient() {
        return new RmiProtocolClientImpl();
    }

    @Override
    public RmiProtocolServer createServer(ObjectCreator objectCreator) {
        return new RmiProtocolServerImpl(objectCreator);
    }

    @Override
    public RmiProtocolServer createServer() {
        return createServer(new ObjectCreator() {

            @Override
            public <T> T create(Class<T> type) {
                String[] names = context.getBeanNamesForType(type);
                if (names.length == 0) {
                    throw new RuntimeException("there is no spring bean of type " + type.getCanonicalName());
                } else if (names.length > 1) {
                    throw new RuntimeException("there is more than one spring bean of type " + type.getCanonicalName());
                }

                if (!context.isPrototype(names[0])) {
                    throw new RuntimeException(String.format("the spring bean '%s' of type '%s' is not a prototype",
                            names[0], type.getCanonicalName()));
                }

                return type.cast(context.getBean(names[0]));
            }
        });
    }

    @Override
    public <T> T createProxy(final Class<T> type, final ServiceNexus nexus, final UUID objectId) {
        Object proxy = Proxy.newProxyInstance(RmiFactoryImpl.class.getClassLoader(), new Class[] { type },
                new InvocationHandler() {
                    private RmiMethodOrdering ifm = new RmiMethodOrdering(type);

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (args == null) {
                            args = new Object[0];
                        }

                        if (method.getName().equals("close") && args.length == 0) {
                            ObjectDestroyRequest request = new ObjectDestroyRequest();
                            request.setObjectId(objectId);

                            try {
                                nexus.execute(request).get();
                            } catch (InterruptedException e) {
                                throw new DelphixInterruptedException(e);
                            } catch (ExecutionException e) {
                                throw new RuntimeException(ExceptionUtil.unwrap(e));
                            }
                            return null;
                        } else {
                            MethodCallRequest request = new MethodCallRequest();
                            request.setObjectId(objectId);
                            request.setMethod(ifm.getPlacement(method));
                            request.setArguments(args);

                            MethodCallResponse response;
                            try {
                                response = (MethodCallResponse) nexus.execute(request).get();
                            } catch (InterruptedException e) {
                                throw new DelphixInterruptedException(e);
                            } catch (ExecutionException e) {
                                throw new RuntimeException(ExceptionUtil.unwrap(e));
                            }

                            if (response.getException()) {
                                throw ExceptionUtil.unwrap(new ExecutionException((Throwable) response.getValue()));
                            } else {
                                return response.getValue();
                            }
                        }
                    }
                });

        return type.cast(proxy);
    }
}
