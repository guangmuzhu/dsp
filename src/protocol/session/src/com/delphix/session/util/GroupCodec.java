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

import com.delphix.session.service.ServiceCodec;
import com.delphix.session.service.ServiceException;
import com.delphix.session.service.ServiceRequest;
import com.delphix.session.service.ServiceResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * This class describes a group of sub-codecs that are joined together to form a higher level codec. A service
 * exchange is passed by each codec in the group and is claimed by one of them that understands how to encode and
 * decode it. To avoid conflict, the group index is prefixed to the wire encoding of the service exchanges of the
 * same group.
 */
public class GroupCodec implements ServiceCodec {

    private final ServiceCodec[] codecGroup;

    public GroupCodec(ServiceCodec... codec) {
        checkArgument(codec.length <= Byte.MAX_VALUE, "codec group overflow");
        codecGroup = Arrays.copyOf(codec, codec.length);
    }

    @Override
    public void encode(OutputStream out, ServiceRequest request) throws IOException {
        for (int i = 0; i < codecGroup.length; i++) {
            ServiceCodec codec = codecGroup[i];

            if (codec.claims(request)) {
                out.write(i);
                codec.encode(out, request);
                return;
            }
        }

        throw new IOException("unknown service request " + request.getClass().getName());
    }

    @Override
    public void encode(OutputStream out, ServiceResponse response) throws IOException {
        for (int i = 0; i < codecGroup.length; i++) {
            ServiceCodec codec = codecGroup[i];

            if (codec.claims(response)) {
                out.write(i);
                codec.encode(out, response);
                return;
            }
        }

        throw new IOException("unknown service response " + response.getClass().getName());
    }

    @Override
    public void encode(OutputStream out, ServiceException exception) throws IOException {
        for (int i = 0; i < codecGroup.length; i++) {
            ServiceCodec codec = codecGroup[i];

            if (codec.claims(exception)) {
                out.write(i);
                codec.encode(out, exception);
                return;
            }
        }

        throw new IOException("unknown service exception " + exception.getClass().getName());
    }

    @Override
    public ServiceRequest decodeRequest(InputStream in) throws IOException, ClassNotFoundException {
        ServiceCodec codec;

        try {
            codec = codecGroup[in.read()];
        } catch (IndexOutOfBoundsException e) {
            throw new IOException("unknown codec");
        }

        return codec.decodeRequest(in);
    }

    @Override
    public ServiceResponse decodeResponse(InputStream in) throws IOException, ClassNotFoundException {
        ServiceCodec codec;

        try {
            codec = codecGroup[in.read()];
        } catch (IndexOutOfBoundsException e) {
            throw new IOException("unknown codec");
        }

        return codec.decodeResponse(in);
    }

    @Override
    public ServiceException decodeException(InputStream in) throws IOException, ClassNotFoundException {
        ServiceCodec codec;

        try {
            codec = codecGroup[in.read()];
        } catch (IndexOutOfBoundsException e) {
            throw new IOException("unknown codec");
        }

        return codec.decodeException(in);
    }

    @Override
    public boolean claims(ServiceRequest request) {
        for (ServiceCodec codec : codecGroup) {
            if (codec.claims(request)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean claims(ServiceResponse response) {
        for (ServiceCodec codec : codecGroup) {
            if (codec.claims(response)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean claims(ServiceException exception) {
        for (ServiceCodec codec : codecGroup) {
            if (codec.claims(exception)) {
                return true;
            }
        }

        return false;
    }
}
