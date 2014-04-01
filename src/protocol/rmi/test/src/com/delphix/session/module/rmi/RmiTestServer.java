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

package com.delphix.session.module.rmi;

import com.delphix.session.sasl.AnonymousServer;
import com.delphix.session.sasl.SaslServerConfig;
import com.delphix.session.service.ProtocolHandler;
import com.delphix.session.service.ProtocolHandlerFactory;
import com.delphix.session.service.ServerManager;
import com.delphix.session.service.ServiceTerminus;
import com.delphix.session.util.AbstractServer;
import com.delphix.session.util.TaggedRequestExecutor;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.MutableClassToInstanceMap;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static com.delphix.session.service.ServiceProtocol.PROTOCOL;

public class RmiTestServer extends AbstractServer {

    private final ClassToInstanceMap<Object> objs = MutableClassToInstanceMap.create();

    @Autowired
    private RmiFactory rmiFactory;

    public RmiTestServer() {
        super(RmiTestServiceType.getInstance(), RmiTestExchangeType.class);
    }

    @Autowired
    public void setServerManager(ServerManager manager) {
        this.manager = manager;
    }

    @Override
    protected ProtocolHandlerFactory getProtocolHandlerFactory() {
        return new ProtocolHandlerFactory() {
            @Override
            public List<? extends ProtocolHandler<?>> getHandlers(ServiceTerminus terminus) {
                return ImmutableList.of(rmiFactory.createServer(new ObjectCreator() {
                    @Override
                    public <T> T create(Class<T> type) {
                        T obj = objs.getInstance(type);
                        if (obj == null) {
                            throw new RuntimeException("test did not add() object of type " + type.getCanonicalName());
                        }
                        return obj;
                    }
                }));
            }
        };
    }

    @Override
    protected SaslServerConfig getSaslConfig() {
        SaslServerConfig sasl = new SaslServerConfig(PROTOCOL, SERVER);
        sasl.addMechanism(new AnonymousServer());
        return sasl;
    }

    @Override
    protected ExecutorService getProtocolExecutor() {
        return new TaggedRequestExecutor();
    }

    public <T> void add(Class<T> iface, T obj) {
        objs.put(iface, obj);
    }

    public void clear() {
        objs.clear();
    }
}
