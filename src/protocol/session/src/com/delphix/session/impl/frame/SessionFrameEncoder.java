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
 * Copyright (c) 2013, 2014 by Delphix. All rights reserved.
 */

package com.delphix.session.impl.frame;

import com.delphix.session.impl.common.SessionNexus;
import com.delphix.session.impl.common.SessionTransport;
import com.delphix.session.impl.common.SessionTransportManager;
import com.delphix.session.service.ServiceCodec;
import com.delphix.session.service.ServiceException;
import com.delphix.session.service.ServiceRequest;
import com.delphix.session.service.ServiceResponse;
import com.delphix.session.util.CompressMethod;
import com.delphix.session.util.ExternalObjectOutput;
import com.delphix.session.util.ProtocolVersion;
import org.jboss.netty.buffer.ByteBufferBackedChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

import java.io.IOException;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Checksum;

import static com.delphix.session.impl.frame.SessionFrame.*;
import static org.jboss.netty.buffer.ChannelBuffers.*;

/**
 * This class describes the session frame encoder. In addition to basic frame encoding, it supports a variety of
 * data filtering options such as digest and compression.
 *
 * Wire Format
 *
 * To support digest, we leverage the optional header feature provided by the general session frame wire format, as
 * illustrated below. Each digest covers a different part of the frame and may be enabled/disabled independent of
 * the others. Rather than encoding the digest values inline with the frame, they are placed in the frame header to
 * simplify the life of wire tracer.
 *
 * The order they appear in the frame header is fixed. For example, frame digest is placed before payload digest if
 * both are enabled; header digest always shows up the last in the frame header since it covers everything that comes
 * before it. As long as both sides know the set of options negotiated and the size of the option header is fixed for
 * each option, the ordering can be used to determine the offset of each option header without ambiguity.
 *
 *
 *       Byte/     0       |       1       |       2       |       3       |
 *          /              |               |               |               |
 *         |0 1 2 3 4 5 6 7|0 1 2 3 4 5 6 7|0 1 2 3 4 5 6 7|0 1 2 3 4 5 6 7|
 *         +---------------+---------------+---------------+---------------+ +---+
 *        0|      'D'      |      'S'      |      'P'      |   Reserved    |     |
 *         +---------------+---------------+---------------+---------------+     |
 *        4|     Type      |     Major     |     Minor     |   Revision    |     |
 *         +---------------+---------------+---------------+---------------+     |
 *   +--+ 8| Frame Offset  |        Protocol Data Unit Length              | <---+
 *   |     +---------------+---------------+---------------+---------------+     |
 *   |    +/                         Frame Digest                          /     |
 *   |     +---------------+---------------+---------------+---------------+     |
 *   |    +/                       Payload Digest                          /     |
 *   |     +---------------+---------------+---------------+---------------+     |
 *   |    +/                        Header Digest                          /     |
 *   +---> +---------------+---------------+---------------+---------------+     |
 *         |  Frame Type   |             Session Frame                     /     |
 *         +---------------+                                               /     |
 *        +/                                                               /     |
 *         +---------------+---------------+---------------+---------------+ +---+
 *
 *
 * Header digest is calculated over everything before it, i.e., from offset 0 to the offset of header digest itself.
 *
 * Frame digest is calculated over everything after the frame header, with the exception of service payload (request
 * or response) in the case of command frames.
 *
 * Payload digest is calculated over the service payload, optionally up to and including the length encoding of the
 * bulk data.
 *
 * Payload compression is applied over the entire service payload. When payload compression is enabled, if payload
 * digest is also enabled, it covers the entire payload as well regardless of the bulk data digest setting.
 *
 * Buffer Management
 *
 * We try to optimize buffer management and eliminate unnecessary data copy in the codec implementation. For the
 * encoder, we start off with a dynamic data buffer with an estimated initial size which is used to encode the frame
 * header, frame body, service payload except any bulk data. The buffer is dynamically expanded if there isn't enough
 * space for the encoded data. Data copy is incurred only during buffer expansion. With a proper initial size estimate,
 * we could avoid this data copy with reasonable memory overhead. In the future, we could "train" the encoder to learn
 * the optimal size over time.
 *
 * While calculating the checksum for the bulk data included in the service payload, we only need to make extra data
 * copy if the ByteBuffer is not backed up a byte array, since the checksum implementation doesn't take anything but
 * byte array. If data digest is enabled, one should use byte array as the backing store for ByteBuffer if possible.
 *
 * Unless payload compression is enabled, we always use the bulk data included in the service payload as is without
 * extra copy, which significantly reduces the memory footprint of an outstanding command.
 *
 * When payload compression is enabled and the service payload includes bulk data, we first encode the frame up to
 * but excluding the bulk data into a temporary buffer; and then compress the content of the temporary buffer and the
 * bulk data into the result buffer allocated to fit. Today the buffer size is estimated according to the worst case
 * compression ratio. In the future we can have an adaptive approach to balance memory usage and copy efficiency. To
 * mitigate the cost of copy here, we use NIO direct buffer which could reduce copies down stream.
 *
 * Whenever possible, we try to apply data filtering, such as encode, digest, and compress, in one pass to avoid
 * multiple traversals of the same data. This is done via the chaining of filter streams.
 */
public class SessionFrameEncoder extends OneToOneEncoder {

    private static final int INITIAL_ESTIMATE = 512;

    private final SessionTransportManager manager;
    private final int estimate;

    private SessionNexus nexus;
    private SessionFrameOptions options;

    public SessionFrameEncoder(SessionTransportManager manager) {
        this(manager, INITIAL_ESTIMATE);
    }

    public SessionFrameEncoder(SessionTransportManager manager, int estimate) {
        this.manager = manager;
        this.estimate = estimate;

        options = new SessionFrameOptions();
    }

    public SessionFrameOptions getOptions() {
        return options;
    }

    public void setOptions(SessionFrameOptions options) {
        this.options = options;
    }

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        SessionFrame frame = (SessionFrame) msg;

        if (!(frame instanceof ConnectRequest || frame instanceof ConnectResponse)) {
            getNexus(ctx);
        }

        ChannelBuffer buffer = dynamicBuffer(estimate, channel.getConfig().getBufferFactory());

        // Start the frame header
        startHeader(buffer);

        // Encode the frame body
        OutputStream os = new ChannelBufferOutputStream(buffer);

        DigestMethod method = options.getFrameDigest();
        Checksum digest = method.create();

        if (digest != null) {
            os = new CheckedOutputStream(os, digest);
        }

        ObjectOutput oout = new ExternalObjectOutput(os);

        try {
            frame.writeExternal(oout);
        } finally {
            oout.close();
        }

        if (digest != null) {
            buffer.setBytes(options.getFrameDigestOffset(), method.toByteArray(digest));
        }

        // Encode the service payload
        if (frame instanceof CommandRequest) {
            buffer = encodeRequest(buffer, (CommandRequest) frame);
        } else if (frame instanceof CommandResponse) {
            buffer = encodeResponse(buffer, (CommandResponse) frame);
        }

        // Finish the frame header
        finishHeader(buffer);

        return buffer;
    }

    private ChannelBuffer encodeRequest(ChannelBuffer buffer, CommandRequest command) throws IOException {
        ServiceRequest request = command.getRequest();

        if (request.getData() != null && options.isPayloadCompressed()) {
            buffer = encodeRequestDataCompress(buffer, request);

            // Record the current write index
            int dataEnd = buffer.writerIndex();

            // Reset the index to the marker set in compressPayload()
            buffer.resetWriterIndex();

            // Calculate the size of the compressed bulk data
            command.setCompressedDataSize(dataEnd - buffer.writerIndex());

            // Set the write index back to its original position
            buffer.writerIndex(dataEnd);
        } else {
            buffer = encodeRequestNoDataCompress(buffer, request);
        }

        return buffer;
    }

    private ChannelBuffer encodeResponse(ChannelBuffer buffer, CommandResponse command) throws IOException {
        ServiceResponse response = command.getResponse();

        if (command.getStatus() != CommandStatus.SUCCESS) {
            return buffer;
        }

        if (response != null && response.getData() != null && options.isPayloadCompressed()) {
            buffer = encodeResponseDataCompress(buffer, response);
        } else {
            buffer = encodeResponseNoDataCompress(buffer, response, command.getException());
        }

        return buffer;
    }

    /**
     * Apply compression inline during encoding when there is no bulk data to be compressed. The payload except bulk
     * data is first written into a dynamic encoding buffer with optional digest and compression enabled. The bulk
     * data is then combined with the encoding buffer to avoid additional copy.
     */
    private ChannelBuffer encodeRequestNoDataCompress(ChannelBuffer buffer, ServiceRequest request)
            throws IOException {
        ServiceCodec codec = nexus.getCodec();

        OutputStream bos = new ChannelBufferOutputStream(buffer);
        OutputStream os;

        try {
            os = options.getPayloadCompress().createOutputStream(bos);
        } finally {
            bos.close();
        }

        DigestMethod method = options.getPayloadDigest();
        Checksum digest = method.create();

        if (digest != null) {
            os = new CheckedOutputStream(os, digest);
        }

        ObjectOutput oout = new ExternalObjectOutput(os);

        try {
            // Encode the service payload
            if (codec != null) {
                codec.encode(os, request);
            } else {
                oout.writeObject(request);
            }

            // Encode the service payload data
            ByteBuffer[] buffers = request.getData();

            if (buffers != null) {
                ChannelBuffer data = wrappedBuffer(buffers);
                oout.writeInt(data.readableBytes());

                // Update the payload digest separately for data
                if (options.isDigestData()) {
                    DigestMethod.updateDataDigest(buffers, digest);
                }

                buffer = wrappedBuffer(buffer, data);
            } else {
                oout.writeInt(0);
            }
        } finally {
            oout.close();
        }

        // Encode the payload digest
        if (digest != null) {
            buffer.setBytes(options.getPayloadDigestOffset(), method.toByteArray(digest));
        }

        return buffer;
    }

    private ChannelBuffer encodeResponseNoDataCompress(ChannelBuffer buffer, ServiceResponse response,
            ServiceException exception) throws IOException {
        ServiceCodec codec = nexus.getCodec();

        OutputStream bos = new ChannelBufferOutputStream(buffer);
        OutputStream os;

        try {
            os = options.getPayloadCompress().createOutputStream(bos);
        } finally {
            bos.close();
        }

        DigestMethod method = options.getPayloadDigest();
        Checksum digest = method.create();

        if (digest != null) {
            os = new CheckedOutputStream(os, digest);
        }

        ObjectOutput oout = new ExternalObjectOutput(os);

        try {
            // Encode the exception
            if (exception != null) {
                oout.writeBoolean(true);

                if (codec != null) {
                    codec.encode(os, exception);
                } else {
                    oout.writeObject(exception);
                }
            } else {
                oout.writeBoolean(false);
            }

            // Encode the service payload
            if (response != null) {
                oout.writeBoolean(true);

                if (codec != null) {
                    codec.encode(os, response);
                } else {
                    oout.writeObject(response);
                }

                // Encode the service payload data
                ByteBuffer[] buffers = response.getData();

                if (buffers != null) {
                    ChannelBuffer data = wrappedBuffer(buffers);
                    oout.writeInt(data.readableBytes());

                    // Data digest
                    if (options.isDigestData()) {
                        DigestMethod.updateDataDigest(buffers, digest);
                    }

                    buffer = wrappedBuffer(buffer, data);
                } else {
                    oout.writeInt(0);
                }
            } else {
                oout.writeBoolean(false);
            }
        } finally {
            oout.flush();
            oout.close();
        }

        // Payload digest
        if (digest != null) {
            buffer.setBytes(options.getPayloadDigestOffset(), method.toByteArray(digest));
        }

        return buffer;
    }

    /**
     * With data compression, encode the payload uncompressed first and then compress it together with bulk data.
     */
    private ChannelBuffer encodeRequestDataCompress(ChannelBuffer buffer, ServiceRequest request)
            throws IOException {
        ServiceCodec codec = nexus.getCodec();

        ByteBuffer[] buffers = request.getData();
        assert buffers != null;

        ChannelBuffer body = dynamicBuffer(buffer.capacity());
        OutputStream os = new ChannelBufferOutputStream(body);

        ObjectOutput oout = new ExternalObjectOutput(os);

        try {
            // Encode the service payload
            if (codec != null) {
                codec.encode(os, request);
            } else {
                oout.writeObject(request);
            }

            // Encode the service payload data length
            int length = 0;

            for (int i = 0; i < buffers.length; i++) {
                length += buffers[i].remaining();
            }

            oout.writeInt(length);
        } finally {
            oout.close();
        }

        // Compress the entire payload including body and data
        buffer = compressPayload(buffer, body, buffers);

        return buffer;
    }

    private ChannelBuffer encodeResponseDataCompress(ChannelBuffer buffer, ServiceResponse response)
            throws IOException {
        ServiceCodec codec = nexus.getCodec();

        ByteBuffer[] buffers = response.getData();
        assert buffers != null;

        ChannelBuffer body = dynamicBuffer(buffer.capacity());
        OutputStream os = new ChannelBufferOutputStream(body);

        ObjectOutput oout = new ExternalObjectOutput(os);

        try {
            // Encode the exception
            oout.writeBoolean(false);

            // Encode the service payload
            oout.writeBoolean(true);

            if (codec != null) {
                codec.encode(os, response);
            } else {
                oout.writeObject(response);
            }

            // Encode the service payload data length
            int length = 0;

            for (int i = 0; i < buffers.length; i++) {
                length += buffers[i].remaining();
            }

            oout.writeInt(length);
        } finally {
            oout.close();
        }

        // Compress the entire payload including body and data
        buffer = compressPayload(buffer, body, buffers);

        return buffer;
    }

    private ChannelBuffer compressPayload(ChannelBuffer buffer, ChannelBuffer srcBody, ByteBuffer[] srcBufs)
            throws IOException {
        CompressMethod compress = options.getPayloadCompress();

        // Get the total number of bytes to be compressed
        int size = srcBody.readableBytes();

        for (int i = 0; i < srcBufs.length; i++) {
            size += srcBufs[i].remaining();
        }

        // Estimate the frame length
        int length = buffer.readableBytes();
        length += compress.estimateCompressed(size);

        /*
         * We would like to use direct buffer to avoid downstream data copy. But java does not provide any way for
         * the application to free the native buffer and we can't rely on the java GC to free the buffers since they
         * aren't accounted for in the java heap.
         */
        ChannelBuffer result = buffer(length);

        // Copy the frame header and body
        result.writeBytes(buffer);

        // Configure an output stream with compression and optionally digest enabled
        OutputStream bos = new ChannelBufferOutputStream(result);
        OutputStream os;

        try {
            os = compress.createOutputStream(bos);
        } finally {
            bos.close();
        }

        DigestMethod method = options.getPayloadDigest();
        Checksum digest = method.create();

        if (digest != null) {
            os = new CheckedOutputStream(os, digest);
        }

        try {
            // Stream the "clear" payload body into the filtered stream
            srcBody.readBytes(os, srcBody.readableBytes());

            // Mark the beginning of the compressed bulk data
            result.markWriterIndex();

            // Stream the "clear" payload data into the filtered stream
            for (int i = 0; i < srcBufs.length; i++) {
                ByteBufferBackedChannelBuffer byteBuf = new ByteBufferBackedChannelBuffer(srcBufs[i]);
                byteBuf.readBytes(os, byteBuf.readableBytes());
            }
        } finally {
            os.close();
        }

        // Encode payload digest (both body and data with compression enabled)
        if (digest != null) {
            result.setBytes(options.getPayloadDigestOffset(), method.toByteArray(digest));
        }

        return result;
    }

    /**
     * Start a frame header at the beginning of the channel buffer. Frame length and optional headers are not filled
     * in until the rest of the frame has been encoded.
     */
    private void startHeader(ChannelBuffer buffer) throws IOException {
        ChannelBufferOutputStream bos = new ChannelBufferOutputStream(buffer);

        try {
            // Protocol identifier
            bos.writeInt(PROTO_IDENT);

            // Frame type and protocol version
            if (nexus != null) {
                bos.writeByte(TYPE_VERSION);
                nexus.getActVersion().write(bos);
            } else {
                bos.writeByte(TYPE_CONNECT);
                ProtocolVersion.writeReserved(bos);
            }

            // Frame offset
            int length = options.getHeaderLength();
            bos.writeByte(length);

            // Zero fill the rest for now including frame length and optional headers
            buffer.writeZero(length - bos.writtenBytes());
        } finally {
            bos.flush();
            bos.close();
        }
    }

    /**
     * Finish up the frame header after the rest of the frame has been encoded. The frame length is filled in as well
     * as the optional frame header digest.
     */
    private void finishHeader(ChannelBuffer buffer) {
        // Frame length
        buffer.setMedium(LENGTH_OFFSET, buffer.readableBytes());

        // Optional header digest
        DigestMethod method = options.getHeaderDigest();
        Checksum digest = method.create();

        if (digest != null) {
            int offset = options.getHeaderDigestOffset();
            byte[] array = new byte[offset];

            buffer.getBytes(0, array);
            digest.update(array, 0, offset);

            buffer.setBytes(offset, method.toByteArray(digest));
        }
    }

    private SessionNexus getNexus(ChannelHandlerContext ctx) {
        if (nexus == null) {
            SessionTransport xport = manager.locate(ctx.getChannel());
            nexus = xport.getNexus();
        }

        return nexus;
    }
}
