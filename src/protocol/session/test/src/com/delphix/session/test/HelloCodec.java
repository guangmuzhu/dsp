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

import com.delphix.session.service.ServiceCodec;
import com.delphix.session.service.ServiceException;
import com.delphix.session.service.ServiceRequest;
import com.delphix.session.service.ServiceResponse;
import com.delphix.session.util.ExternalObjectInput;
import com.delphix.session.util.ExternalObjectOutput;

import java.io.*;

public class HelloCodec implements ServiceCodec {

    private static HelloCodec codec = new HelloCodec();

    public static HelloCodec getInstance() {
        return codec;
    }

    private HelloCodec() {

    }

    @Override
    public void encode(OutputStream out, ServiceRequest request) throws IOException {
        HelloRequest hello = (HelloRequest) request;
        ObjectOutput oout = new ExternalObjectOutput(out);

        hello.writeExternal(oout);
    }

    @Override
    public void encode(OutputStream out, ServiceResponse response) throws IOException {
        HelloResponse hello = (HelloResponse) response;
        ObjectOutput oout = new ExternalObjectOutput(out);

        hello.writeExternal(oout);
    }

    @Override
    public void encode(OutputStream out, ServiceException exception) throws IOException {
        @SuppressWarnings("resource")
        ObjectOutput oout = new ExternalObjectOutput(out);
        oout.writeObject(exception);
    }

    @Override
    public ServiceRequest decodeRequest(InputStream in) throws IOException, ClassNotFoundException {
        ExternalObjectInput oin = new ExternalObjectInput(in);
        HelloRequest request = new HelloRequest();
        request.readExternal(oin);
        return request;
    }

    @Override
    public ServiceResponse decodeResponse(InputStream in) throws IOException, ClassNotFoundException {
        ExternalObjectInput oin = new ExternalObjectInput(in);
        HelloResponse response = new HelloResponse();
        response.readExternal(oin);
        return response;
    }

    @Override
    public ServiceException decodeException(InputStream in) throws IOException, ClassNotFoundException {
        ObjectInputStream oin = new ObjectInputStream(in);
        return (ServiceException) oin.readObject();
    }

    @Override
    public boolean claims(ServiceRequest request) {
        return request instanceof HelloRequest;
    }

    @Override
    public boolean claims(ServiceResponse response) {
        return response instanceof HelloResponse;
    }

    @Override
    public boolean claims(ServiceException exception) {
        return true;
    }
}
