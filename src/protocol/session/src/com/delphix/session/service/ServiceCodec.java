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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This interface describes a serialization facility for service payload, including request, response, and exception.
 * A service may provide an implementation of this interface for custom serialization support. If a custom codec is
 * not provided, then the service payload must implement either Externalizable or Serializable interface.
 */
public interface ServiceCodec {

    /**
     * Encode the service request into the output stream.
     */
    public void encode(OutputStream out, ServiceRequest request) throws IOException;

    /**
     * Encode the service response into the output stream.
     */
    public void encode(OutputStream out, ServiceResponse response) throws IOException;

    /**
     * Encode the service exception into the output stream.
     */
    public void encode(OutputStream out, ServiceException exception) throws IOException;

    /**
     * Decode the service request from the input stream.
     */
    public ServiceRequest decodeRequest(InputStream in) throws IOException, ClassNotFoundException;

    /**
     * Decode the service response from the input stream.
     */
    public ServiceResponse decodeResponse(InputStream in) throws IOException, ClassNotFoundException;

    /**
     * Decode the service exception from the input stream.
     */
    public ServiceException decodeException(InputStream in) throws IOException, ClassNotFoundException;

    /**
     * Return true if the codec is capable of encoding the request.
     */
    public boolean claims(ServiceRequest request);

    /**
     * Return true if the codec is capable of encoding the response.
     */
    public boolean claims(ServiceResponse response);

    /**
     * Return true if the codec is capable of encoding the exception.
     */
    public boolean claims(ServiceException exception);
}
