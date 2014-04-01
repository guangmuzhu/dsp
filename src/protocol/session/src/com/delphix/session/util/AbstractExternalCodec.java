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

package com.delphix.session.util;

import com.delphix.appliance.server.util.ExceptionUtil;
import com.delphix.session.service.*;
import org.apache.commons.lang.exception.ExceptionUtils;

import java.io.*;

/**
 * This is a convenience utility for implementing a service codec based on the Java Externalizable mechanism. In most
 * cases, one should use the ExchangeCodec that extends this class and uses the ExchangeRegistry for type mapping.
 * But occasionally, it may be desirable to have a custom type mapping facility.
 */
public abstract class AbstractExternalCodec implements ServiceCodec {

    /**
     * Encode the service request into the output stream.
     */
    @Override
    public void encode(OutputStream out, ServiceRequest request) throws IOException {
        encodeExchange(out, request);
    }

    /**
     * Encode the service response into the output stream.
     */
    @Override
    public void encode(OutputStream out, ServiceResponse response) throws IOException {
        encodeExchange(out, response);
    }

    /**
     * Encode the service exception into the output stream.
     */
    @Override
    public void encode(OutputStream out, ServiceException exception) throws IOException {
        @SuppressWarnings("resource")
        ObjectOutput oout = new ExternalObjectOutput(out);

        /*
         * If the ServiceException is not serializable then we need to convert it to a new service exception
         * that we can encode.
         */
        if (isThrowableSerializable(exception)) {
            oout.writeObject(exception);
        } else {
            oout.writeObject(new ServiceException(ExceptionUtils.getStackTrace(exception)));
        }
    }

    /**
     * Decode the service request from the input stream.
     */
    @Override
    public ServiceRequest decodeRequest(InputStream in) throws IOException, ClassNotFoundException {
        return (ServiceRequest) decodeExchange(in);
    }

    /**
     * Decode the service response from the input stream.
     */
    @Override
    public ServiceResponse decodeResponse(InputStream in) throws IOException, ClassNotFoundException {
        return (ServiceResponse) decodeExchange(in);
    }

    /**
     * Decode the service exception from the input stream.
     */
    @Override
    public ServiceException decodeException(InputStream in) throws IOException, ClassNotFoundException {
        @SuppressWarnings("resource")
        ObjectInput oin = new ExternalObjectInput(in);
        return (ServiceException) oin.readObject();
    }

    protected ServiceExchange createExchange(Class<? extends ServiceExchange> objectClass) throws IOException {
        // newInstance uses reflection but not class loading
        ServiceExchange exchange;

        try {
            exchange = objectClass.newInstance();
        } catch (Exception e) {
            throw new IOException(e);
        }

        return exchange;
    }

    @Override
    public boolean claims(ServiceRequest request) {
        return claimsExchange(request);
    }

    @Override
    public boolean claims(ServiceResponse response) {
        return claimsExchange(response);
    }

    @Override
    public boolean claims(ServiceException exception) {
        return true;
    }

    /**
     * Encode the given service exchange to the output stream.
     */
    protected abstract void encodeExchange(OutputStream out, ServiceExchange exchange) throws IOException;

    /**
     * Decode the service exchange from the input stream.
     */
    protected abstract ServiceExchange decodeExchange(InputStream in) throws IOException, ClassNotFoundException;

    /**
     * Return true if the exchange is owned by the codec.
     */
    protected abstract boolean claimsExchange(ServiceExchange exchange);

    /**
     * A convenience routine that tests to see whether the given Throwable is serializable. We have found
     * various JSON exceptions that are not serializable, which breaks the current replication DSP encoder.
     *
     * List of known problematic exceptions:
     *
     * Any exception extending JsonProcessingException, due to the embedded JsonLocation
     */
    public static boolean isThrowableSerializable(Throwable cause) throws IOException {
        return isThrowableSerializable(cause, 0);
    }

    /**
     * Same as above except with length limit check.
     * @throws IOException
     */
    public static boolean isThrowableSerializable(Throwable cause, int limit) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        ObjectOutputStream output;

        try {
            output = new ObjectOutputStream(byteStream);
            output.writeObject(cause);
            return (limit <= 0 || byteStream.toByteArray().length <= limit);
        } catch (NotSerializableException e) {
            return false;
        } finally {
            ExceptionUtil.closeIgnoreExceptions(byteStream);
        }
    }
}
