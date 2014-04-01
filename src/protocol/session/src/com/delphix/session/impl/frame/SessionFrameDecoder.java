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

package com.delphix.session.impl.frame;

import com.delphix.appliance.logger.Logger;
import com.delphix.session.impl.common.*;
import com.delphix.session.service.*;
import com.delphix.session.util.ExternalObjectInput;
import com.delphix.session.util.ProtocolVersion;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.nio.ByteBuffer;
import java.util.zip.CheckedInputStream;
import java.util.zip.Checksum;

import static com.delphix.session.impl.frame.SessionFrame.*;
import static com.delphix.session.service.ServiceOption.*;
import static org.jboss.netty.buffer.ChannelBuffers.hexDump;

/**
 * This class describes the session frame decoder, which is responsible for the deserialization of session frames.
 *
 * For detailed description of the wire format, please refer to the SessionFrameEncoder class.
 *
 * When payload compression is disabled and the service payload includes bulk data, the bulk data is extracted from
 * the channel buffer into ByteBuffer's without additional data copy.
 *
 * Whenever possible, we try to apply data filtering, such as decode, digest, and decompress, in one pass to avoid
 * multiple traversals of the same data. This is done via the chaining of filter streams.
 *
 * The FrameDecoder class from which this class extends is stateful and has member variables such as the cumulation
 * buffer for operation. As such, this class must not be sharable as it is unsafe to share the state. Therefore, we
 * have one instance of the decoder per channel, unlike the encoder which is shared among all channels.
 */
public class SessionFrameDecoder extends FrameDecoder {

    private static final Logger logger = Logger.getLogger(SessionFrameDecoder.class);

    private final SessionTransportManager manager;

    private SessionNexus nexus;

    private SessionFrameOptions options;

    public SessionFrameDecoder(SessionTransportManager manager) {
        this.manager = manager;

        options = new SessionFrameOptions();
    }

    public SessionFrameOptions getOptions() {
        return options;
    }

    public void setOptions(SessionFrameOptions options) {
        this.options = options;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {
        SessionFrame frame;

        ChannelBuffer header;
        ChannelBuffer body;

        try {
            // Extract the frame from the input
            ChannelBuffer[] buffers = extractFrame(ctx, buffer);

            if (buffers == null) {
                return null;
            }

            header = buffers[0];
            body = buffers[1];
        } catch (Exception e) {
            logger.errorf("failed to extract frame: %s\n%s", buffer, hexDump(buffer));
            throw e;
        }

        InputStream is = new ChannelBufferInputStream(body);

        DigestMethod method = options.getFrameDigest();
        Checksum digest = method.create();

        if (digest != null) {
            is = new CheckedInputStream(is, digest);
        }

        ObjectInput oin = new ExternalObjectInput(is);

        try {
            // Decode the frame body
            frame = SessionFrame.deserialize(oin);

            // Verify frame digest
            if (digest != null) {
                verifyDigest(method, header, options.getFrameDigestOffset(), digest);
            }

            // Decode the command
            if (frame instanceof CommandRequest) {
                decodeRequest(header, body, (CommandRequest) frame);
            } else if (frame instanceof CommandResponse) {
                decodeResponse(header, body, (CommandResponse) frame);
            }
        } catch (Exception e) {
            logger.errorf("failed to decode frame: %s\n%s", body, hexDump(body));
            throw e;
        } finally {
            oin.close();
        }

        return frame;
    }

    private void decodeRequest(ChannelBuffer header, ChannelBuffer body, CommandRequest command)
            throws ClassNotFoundException, IOException {
        ServiceCodec codec = nexus.getCodec();
        ServiceRequest request;

        // Verify frame length
        verifyLength(header.getUnsignedMedium(LENGTH_OFFSET), true);

        InputStream bis = new ChannelBufferInputStream(body);
        InputStream is;

        try {
            is = options.getPayloadCompress().createInputStream(bis);
        } finally {
            bis.close();
        }

        DigestMethod method = options.getPayloadDigest();
        Checksum digest = method.create();

        if (digest != null) {
            is = new CheckedInputStream(is, digest);
        }

        ObjectInput oin = new ExternalObjectInput(is);

        try {
            if (codec != null) {
                request = codec.decodeRequest(is);
            } else {
                request = (ServiceRequest) oin.readObject();
            }

            int length = oin.readInt();

            if (length > 0) {
                ByteBuffer[] buffers = decodeData(is, digest, body, length);
                request.setData(buffers);
            }

            command.setRequest(request);
        } finally {
            oin.close();
        }

        if (digest != null) {
            verifyDigest(method, header, options.getPayloadDigestOffset(), digest);
        }
    }

    private void decodeResponse(ChannelBuffer header, ChannelBuffer body, CommandResponse command)
            throws IOException, ClassNotFoundException {
        ServiceCodec codec = nexus.getCodec();

        // Verify frame length
        verifyLength(header.getUnsignedMedium(LENGTH_OFFSET), false);

        if (command.getStatus() != CommandStatus.SUCCESS) {
            return;
        }

        ServiceResponse response;
        ServiceException exception;

        InputStream bis = new ChannelBufferInputStream(body);
        InputStream is;

        try {
            is = options.getPayloadCompress().createInputStream(bis);
        } finally {
            bis.close();
        }

        DigestMethod method = options.getPayloadDigest();
        Checksum digest = method.create();

        if (digest != null) {
            is = new CheckedInputStream(is, digest);
        }

        ObjectInput oin = new ExternalObjectInput(is);

        try {
            if (oin.readBoolean()) {
                if (codec != null) {
                    exception = codec.decodeException(is);
                } else {
                    exception = (ServiceException) oin.readObject();
                }

                command.setException(exception);
            }

            if (oin.readBoolean()) {
                if (codec != null) {
                    response = codec.decodeResponse(is);
                } else {
                    response = (ServiceResponse) oin.readObject();
                }

                int length = oin.readInt();

                if (length > 0) {
                    ByteBuffer[] buffers = decodeData(is, digest, body, length);
                    response.setData(buffers);
                }

                command.setResponse(response);
            }
        } finally {
            oin.close();
        }

        if (digest != null) {
            verifyDigest(method, header, options.getPayloadDigestOffset(), digest);
        }
    }

    private ChannelBuffer[] extractFrame(ChannelHandlerContext ctx, ChannelBuffer buffer) {
        int available = buffer.readableBytes();

        // Wait til the frame header is received
        if (available < HEADER_LENGTH) {
            return null;
        }

        /*
         * Create a slice that starts from current reader index. The buffer could have multiple frames in which case
         * we might be looking at the middle of the buffer. A slice conveniently hides that complexity.
         */
        ChannelBuffer slice;

        if (buffer.readerIndex() > 0) {
            slice = buffer.slice();
        } else {
            slice = buffer;
        }

        // Validate protocol identity
        int ident = slice.getInt(0);

        if (ident != PROTO_IDENT) {
            throw new ProtocolViolationException("invalid protocol identifier " + ident);
        }

        // Validate frame type and protocol version
        int type = slice.getUnsignedByte(TYPE_OFFSET);

        ProtocolVersion active;

        if (type == TYPE_CONNECT) {
            active = ProtocolVersion.getReserved();
        } else if (type == TYPE_VERSION) {
            active = getNexus(ctx).getActVersion();
        } else {
            throw new ProtocolViolationException("invalid frame type " + type);
        }

        ProtocolVersion version = new ProtocolVersion(slice.getUnsignedByte(VERSION_OFFSET),
                slice.getUnsignedByte(VERSION_OFFSET + 1), slice.getUnsignedByte(VERSION_OFFSET + 2));

        if (!version.equals(active)) {
            throw new ProtocolViolationException("invalid version " + version + " expected " + active);
        }

        // Validate frame length and payload offset
        int hdrLength = options.getHeaderLength();

        int offset = slice.getUnsignedByte(START_OFFSET);
        int length = slice.getUnsignedMedium(LENGTH_OFFSET);

        if (offset < hdrLength) {
            throw new ProtocolViolationException("invalid frame offset " + offset);
        }

        if (length < hdrLength) {
            throw new ProtocolViolationException("invalid frame length " + length);
        }

        if (offset > length) {
            throw new ProtocolViolationException("invalid frame offset/length " + offset + "/" + length);
        }

        // Wait til the full frame is received
        if (available < length) {
            return null;
        }

        // Verify header digest
        DigestMethod method = options.getHeaderDigest();
        Checksum digest = method.create();

        if (digest != null) {
            byte[] header = new byte[options.getHeaderDigestOffset()];

            slice.getBytes(0, header);
            digest.update(header, 0, header.length);

            verifyDigest(method, slice, options.getHeaderDigestOffset(), digest);
        }

        // Slice the frame header and body out of the slice buffer
        ChannelBuffer[] result = new ChannelBuffer[2];

        result[0] = slice.slice(0, hdrLength);
        result[1] = slice.slice(hdrLength, length - hdrLength);

        // Skip the current frame from the buffer passed in for the next round
        buffer.skipBytes(length);

        // Catch it now if the next frame does not start with protocol identity
        int index = buffer.readerIndex();

        if (buffer.writerIndex() >= index + TYPE_OFFSET) {
            type = buffer.getInt(index);

            if (type != PROTO_IDENT) {
                throw new ProtocolViolationException("invalid protocol identifier " + type);
            }
        }

        return result;
    }

    private void verifyLength(int length, boolean request) {
        ServiceOptions options = nexus.getOptions();

        if (request) {
            if (nexus.isClient()) {
                if (length > options.getOption(BACK_MAX_REQUEST)) {
                    throw new ProtocolViolationException("back channel request length exceeded");
                }
            } else {
                if (length > options.getOption(FORE_MAX_REQUEST)) {
                    throw new ProtocolViolationException("fore channel request length exceeded");
                }
            }
        } else {
            if (nexus.isClient()) {
                if (length > options.getOption(FORE_MAX_RESPONSE)) {
                    throw new ProtocolViolationException("fore channel response length exceeded");
                }
            } else {
                if (length > options.getOption(BACK_MAX_RESPONSE)) {
                    throw new ProtocolViolationException("back channel response length exceeded");
                }
            }
        }
    }

    private void verifyDigest(DigestMethod method, ChannelBuffer header, int offset, Checksum digest) {
        byte[] value = new byte[method.size()];

        header.getBytes(offset, value);

        if (digest.getValue() != method.fromByteArray(value)) {
            throw new DigestMismatchException("digest mismatch");
        }
    }

    private ByteBuffer[] decodeData(InputStream is, Checksum digest, ChannelBuffer body, int length)
            throws IOException {
        ByteBuffer[] buffers;

        if (options.isPayloadCompressed()) {
            byte[] data = new byte[length];
            int offset = 0;

            do {
                int count = is.read(data, offset, length);

                if (count < 0) {
                    throw new EOFException();
                }

                offset += count;
                length -= count;
            } while (length > 0);

            buffers = new ByteBuffer[1];
            buffers[0] = ByteBuffer.wrap(data);
        } else {
            buffers = body.toByteBuffers();

            if (options.isDigestData()) {
                DigestMethod.updateDataDigest(buffers, digest);
            }
        }

        return buffers;
    }

    private SessionNexus getNexus(ChannelHandlerContext ctx) {
        if (nexus == null) {
            SessionTransport xport = manager.locate(ctx.getChannel());
            nexus = xport.getNexus();
        }

        return nexus;
    }
}
