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

import com.delphix.appliance.server.exception.DelphixInterruptedException;
import com.delphix.appliance.server.util.ExceptionUtil;
import com.delphix.session.module.rmi.protocol.ObjectCreateRequest;
import com.delphix.session.module.rmi.protocol.ObjectCreateResponse;
import com.delphix.session.service.ClientManager;
import com.delphix.session.service.ClientNexus;
import com.delphix.session.service.ProtocolHandler;
import com.delphix.session.service.ServerManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.Serializable;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

@ContextConfiguration(locations = {
        "/META-INF/spring/platform-external-context.xml",
        "/META-INF/spring/rmi-context.xml",
        "/META-INF/spring/mock-rmi-context.xml" })
public class RmiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private ServerManager serverManager;

    @Autowired
    private ClientManager clientManager;

    @Autowired
    private RmiFactory rmiFactory;

    @Autowired
    private RmiTestServer server;

    @Autowired
    private RmiTestConnector connector;

    private ClientNexus nexus;

    @BeforeClass
    public void init() {
        clientManager.start();
        serverManager.start();
        server.start();
        connector.start();

        nexus = connector.create("localhost", "user", "password",
                Arrays.<ProtocolHandler<?>> asList(rmiFactory.createClient()));
        connector.connect(nexus);
    }

    @AfterMethod
    public void clearObjs() {
        server.clear();
    }

    private <T> T setupProxy(Class<T> type, T mock) {
        server.add(type, mock);

        ObjectCreateRequest request = new ObjectCreateRequest();
        request.setRequest(type);

        ObjectCreateResponse response;
        try {
            response = (ObjectCreateResponse) nexus.execute(request).get();
        } catch (InterruptedException e) {
            throw new DelphixInterruptedException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(ExceptionUtil.unwrap(e));
        }

        final UUID objectId = response.getObjectId();
        return rmiFactory.createProxy(type, nexus, objectId, null, 0);
    }

    public interface SimpleTest extends RemoteService {
        void a();

        void b(int i);

        void c(String i);
    }

    private void runSimpleTest(SimpleTest o) {
        o.a();
        o.c("5");
        o.b(6);
        o.a();
        o.a();
        o.b(7);
        o.c("string");
        o.c(null);
        o.a();
        o.b(8);
        o.b(1);
    }

    @Test
    public void simpleTest() {
        SimpleTest m = createStrictMock(SimpleTest.class);
        runSimpleTest(m);
        expectLastCall();
        replay(m);

        SimpleTest p = setupProxy(SimpleTest.class, m);
        runSimpleTest(p);
        p.close();
        verify(m);
    }

    public interface MethodOverrideTest extends RemoteService {
        void a(int i);

        void a(Integer i);

        void a(String i);
    }

    private void runMethodOverrideTest(MethodOverrideTest o) {
        o.a(1);
        o.a(2);
        o.a((Integer) 3);
        o.a((Integer) 4);
        o.a("5");
        o.a(6);
        o.a((Integer) null);
        o.a((String) null);
    }

    @Test
    public void methodOverrideTest() {
        MethodOverrideTest m = createStrictMock(MethodOverrideTest.class);
        runMethodOverrideTest(m);
        expectLastCall();
        replay(m);

        MethodOverrideTest p = setupProxy(MethodOverrideTest.class, m);
        runMethodOverrideTest(p);
        p.close();
        verify(m);
    }

    public static class BaseType implements Serializable {
    }

    public static class SubClass1 extends BaseType {
        private final int value;

        public SubClass1(int value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null) {
                return false;
            } else if (!(other instanceof SubClass1)) {
                return false;
            } else {
                return ((SubClass1) other).value == this.value;
            }
        }

        @Override
        public int hashCode() {
            return value;
        }
    }

    public static class SubClass2 extends BaseType {
        private final String value;

        public SubClass2(String value) {
            assert value != null;
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null) {
                return false;
            } else if (!(other instanceof SubClass2)) {
                return false;
            } else {
                return ((SubClass2) other).value.equals(this.value);
            }
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    public interface GenericTest extends RemoteService {
        <T extends SubClass1> void a(T i);

        <T extends SubClass2> void a(T i);
    }

    private void runGenericTest(GenericTest o) {
        o.a(new SubClass1(1));
        o.a(new SubClass2("blue"));
        o.a(new SubClass1(2));
        o.a(new SubClass1(4));
        o.a(new SubClass2("green"));
        o.a(new SubClass1(8));
        o.a(new SubClass2("yellow"));
        o.a(new SubClass1(16));
    }

    @Test
    public void genericTest() {
        GenericTest m = createStrictMock(GenericTest.class);
        runGenericTest(m);
        expectLastCall();
        replay(m);

        GenericTest p = setupProxy(GenericTest.class, m);
        runGenericTest(p);
        p.close();
        verify(m);
    }

    public interface MultiArgsTest extends RemoteService {
        void a(String s, int... i);

        void a(int i, String... s);
    }

    private void runMultiArgsTest(MultiArgsTest o) {
        o.a(3, "test", null, "fish");
        o.a(6, "test", "fish");
        o.a(3, "test");
        o.a(1);
        o.a("test", 1, 2, 3);
        o.a("test", 1, 6);
        o.a("test");
    }

    @Test
    public void multiArgsTest() {
        MultiArgsTest m = createStrictMock(MultiArgsTest.class);
        runMultiArgsTest(m);
        expectLastCall();
        replay(m);

        MultiArgsTest p = setupProxy(MultiArgsTest.class, m);
        runMultiArgsTest(p);
        p.close();
        verify(m);
    }

    public interface ReturnTest extends RemoteService {
        int a();

        String b();
    }

    @Test
    public void returnTest() {
        ReturnTest m = createStrictMock(ReturnTest.class);
        expect(m.a()).andReturn(5);
        expect(m.b()).andReturn("test");
        expect(m.b()).andReturn(null);
        expect(m.a()).andThrow(new RuntimeException("a message"));
        replay(m);

        ReturnTest p = setupProxy(ReturnTest.class, m);
        assertEquals(p.a(), 5);
        assertEquals(p.b(), "test");
        assertEquals(p.b(), null);
        try {
            p.a();
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "a message");
        }
        p.close();
        verify(m);
    }
}
