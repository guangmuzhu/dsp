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

package com.delphix.session.test;

import com.delphix.appliance.server.test.UnitTest;
import com.delphix.session.impl.common.*;
import com.delphix.session.impl.frame.*;
import com.delphix.session.service.*;
import com.delphix.session.util.CompressMethod;
import com.delphix.session.util.EventSource;
import com.google.common.collect.ImmutableList;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.handler.codec.embedder.EncoderEmbedder;
import org.testng.annotations.*;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.UUID;

import static org.testng.Assert.*;

@UnitTest
public class FrameTest {

    private final ExchangeID exchangeID = ExchangeID.allocate();
    private final SerialNumber commandSN = new SerialNumber(3711L);
    private final SerialNumber slotSN = new SerialNumber(139L);
    private final int slotID = 11;
    private final int maxSlotID = 0xfff0;

    private final String val1 = new String("If your video's frozen try re-setting the TCP/IP stack");
    private final String val2 = new String(" A neutron walks into a bar and asks how much for a drink." +
            " The bartender replies \"for you, no charge\".");

    private final String errorMessage = "kaboom!";

    private CommandRequest cmdRequest;
    private CommandResponse cmdResponse;
    private CommandResponse cmdException;
    private CommandResponse badResponse;

    private SessionFrameEncoder frameEnc;
    private SessionFrameDecoder frameDec;

    private EncoderEmbedder<ChannelBuffer> encoder;
    private DecoderEmbedder<SessionFrame> decoder;

    private SessionTransportManager xportManager = new TestTransportManager();

    @BeforeClass
    public void initFrame() {
        HelloRequest request = new HelloRequest(val1.getBytes(), val2.getBytes());

        cmdRequest = new CommandRequest();

        cmdRequest.setExchangeID(exchangeID);
        cmdRequest.setCommandSN(commandSN);
        cmdRequest.setExpectedCommandSN(commandSN);
        cmdRequest.setSlotID(slotID);
        cmdRequest.setSlotSN(slotSN);
        cmdRequest.setMaxSlotIDInUse(maxSlotID);

        cmdRequest.setRequest(request);

        HelloResponse response = new HelloResponse(val1.getBytes(), val2.getBytes());

        cmdResponse = new CommandResponse();

        cmdResponse.setExchangeID(exchangeID);
        cmdResponse.setCommandSN(commandSN);
        cmdResponse.setExpectedCommandSN(commandSN);
        cmdResponse.setStatus(CommandStatus.SUCCESS);
        cmdResponse.setTargetMaxSlotID(maxSlotID);
        cmdResponse.setCurrentMaxSlotID(maxSlotID);
        cmdResponse.setSlotID(slotID);
        cmdResponse.setSlotSN(slotSN);

        cmdResponse.setResponse(response);

        ServiceExecutionException exception = new ServiceExecutionException(errorMessage);

        cmdException = new CommandResponse();

        cmdException.setExchangeID(exchangeID);
        cmdException.setCommandSN(commandSN);
        cmdException.setExpectedCommandSN(commandSN);
        cmdException.setStatus(CommandStatus.SUCCESS);
        cmdException.setTargetMaxSlotID(maxSlotID);
        cmdException.setCurrentMaxSlotID(maxSlotID);
        cmdException.setSlotID(slotID);
        cmdException.setSlotSN(slotSN);

        cmdException.setException(exception);

        badResponse = new CommandResponse();

        badResponse.setExchangeID(exchangeID);
        badResponse.setCommandSN(commandSN);
        badResponse.setExpectedCommandSN(commandSN);
        badResponse.setStatus(CommandStatus.SLOT_ID_INVALID);
        badResponse.setTargetMaxSlotID(maxSlotID);
        badResponse.setCurrentMaxSlotID(maxSlotID);
        badResponse.setSlotID(slotID);
        badResponse.setSlotSN(slotSN);
    }

    @AfterClass
    public void finiFrame() {

    }

    @BeforeMethod
    public void initCodec() {
        frameEnc = new SessionFrameEncoder(xportManager);
        frameDec = new SessionFrameDecoder(xportManager);

        encoder = new EncoderEmbedder<ChannelBuffer>(frameEnc);
        decoder = new DecoderEmbedder<SessionFrame>(frameDec);
    }

    @AfterMethod
    public void finiCodec() {
        encoder.finish();
        decoder.finish();
    }

    @Test
    public void testBasicCodec() {
        runFrameCodec();
    }

    @Test
    public void testHeaderDigest() {
        SessionFrameOptions options;

        options = frameEnc.getOptions();
        options.setHeaderDigest(DigestMethod.DIGEST_CRC32);

        options = frameDec.getOptions();
        options.setHeaderDigest(DigestMethod.DIGEST_CRC32);

        runFrameCodec();
    }

    @Test
    public void testFrameDigest() {
        SessionFrameOptions options;

        options = frameEnc.getOptions();
        options.setFrameDigest(DigestMethod.DIGEST_CRC32);

        options = frameDec.getOptions();
        options.setFrameDigest(DigestMethod.DIGEST_CRC32);

        runFrameCodec();
    }

    @Test
    public void testPayloadDigest() {
        SessionFrameOptions options;

        options = frameEnc.getOptions();
        options.setPayloadDigest(DigestMethod.DIGEST_CRC32);
        options.setDigestData(true);

        options = frameDec.getOptions();
        options.setPayloadDigest(DigestMethod.DIGEST_CRC32);
        options.setDigestData(true);

        runFrameCodec();
    }

    @Test
    public void testPayloadDigestNoData() {
        SessionFrameOptions options;

        options = frameEnc.getOptions();
        options.setPayloadDigest(DigestMethod.DIGEST_ADLER32);
        options.setDigestData(false);

        options = frameDec.getOptions();
        options.setPayloadDigest(DigestMethod.DIGEST_ADLER32);
        options.setDigestData(false);

        runFrameCodec();
    }

    @Test
    public void testPayloadDeflate() {
        SessionFrameOptions options;

        options = frameEnc.getOptions();
        options.setPayloadCompress(CompressMethod.COMPRESS_DEFLATE);

        options = frameDec.getOptions();
        options.setPayloadCompress(CompressMethod.COMPRESS_DEFLATE);

        runFrameCodec();
    }

    @Test
    public void testPayloadGzip() {
        SessionFrameOptions options;

        options = frameEnc.getOptions();
        options.setPayloadCompress(CompressMethod.COMPRESS_GZIP);

        options = frameDec.getOptions();
        options.setPayloadCompress(CompressMethod.COMPRESS_GZIP);

        runFrameCodec();
    }

    @Test
    public void testPayloadLz4() {
        SessionFrameOptions options;

        options = frameEnc.getOptions();
        options.setPayloadCompress(CompressMethod.COMPRESS_LZ4);

        options = frameDec.getOptions();
        options.setPayloadCompress(CompressMethod.COMPRESS_LZ4);

        runFrameCodec();
    }

    @Test
    public void testOptionsCombo() {
        SessionFrameOptions options;

        options = frameEnc.getOptions();
        options.setHeaderDigest(DigestMethod.DIGEST_CRC32);
        options.setFrameDigest(DigestMethod.DIGEST_CRC32);
        options.setPayloadDigest(DigestMethod.DIGEST_ADLER32);
        options.setDigestData(false);
        options.setPayloadCompress(CompressMethod.COMPRESS_DEFLATE);

        options = frameDec.getOptions();
        options.setHeaderDigest(DigestMethod.DIGEST_CRC32);
        options.setFrameDigest(DigestMethod.DIGEST_CRC32);
        options.setPayloadDigest(DigestMethod.DIGEST_ADLER32);
        options.setDigestData(false);
        options.setPayloadCompress(CompressMethod.COMPRESS_DEFLATE);

        runFrameCodec();
    }

    @Test
    public void testMultipleFrames() {
        SessionFrameOptions options;

        options = frameEnc.getOptions();
        options.setHeaderDigest(DigestMethod.DIGEST_CRC32);
        options.setFrameDigest(DigestMethod.DIGEST_CRC32);
        options.setPayloadDigest(DigestMethod.DIGEST_ADLER32);
        options.setDigestData(false);
        options.setPayloadCompress(CompressMethod.COMPRESS_DEFLATE);

        options = frameDec.getOptions();
        options.setHeaderDigest(DigestMethod.DIGEST_CRC32);
        options.setFrameDigest(DigestMethod.DIGEST_CRC32);
        options.setPayloadDigest(DigestMethod.DIGEST_ADLER32);
        options.setDigestData(false);
        options.setPayloadCompress(CompressMethod.COMPRESS_DEFLATE);

        ChannelBuffer result;

        for (int i = 0; i < 2; i++) {
            encoder.offer(cmdRequest);
        }

        encoder.offer(cmdResponse);

        assertEquals(encoder.size(), 3);

        ChannelBuffer[] buffers = new ChannelBuffer[3];
        encoder.pollAll(buffers);
        result = ChannelBuffers.copiedBuffer(buffers);

        decoder.offer(result);

        SessionFrame[] frames = new SessionFrame[3];
        decoder.pollAll(frames);

        verifyRequest(frames[0]);
        verifyRequest(frames[1]);
        verifyResponse(frames[2]);
    }

    private void runFrameCodec() {
        codecRequest();

        codecSuccessResponse();
        codecServiceException();
        codecProtocolFailure();
    }

    private void codecRequest() {
        ChannelBuffer result;

        encoder.offer(cmdRequest);
        result = encoder.poll();

        decoder.offer(result);
        verifyRequest(decoder.poll());
    }

    private void verifyRequest(SessionFrame frame) {
        assertTrue(frame instanceof CommandRequest);

        CommandRequest command = (CommandRequest) frame;

        assertEquals(command.getExchangeID(), exchangeID);
        assertEquals(command.getCommandSN(), commandSN);
        assertEquals(command.getExpectedCommandSN(), commandSN);
        assertEquals(command.getSlotID(), slotID);
        assertEquals(command.getSlotSN(), slotSN);
        assertEquals(command.getMaxSlotIDInUse(), maxSlotID);
        assertNotNull(command.getRequest());

        ServiceRequest svcRequest = command.getRequest();
        assertTrue(svcRequest instanceof HelloRequest);

        HelloRequest request = (HelloRequest) svcRequest;
        verifyData(request.getData());
    }

    private void codecSuccessResponse() {
        CommandResponse command = codecResponseCommon(cmdResponse);

        assertEquals(command.getStatus(), CommandStatus.SUCCESS);
        assertNull(command.getException());
        assertNotNull(command.getResponse());

        ServiceResponse svcResponse = command.getResponse();
        assertTrue(svcResponse instanceof HelloResponse);

        HelloResponse response = (HelloResponse) svcResponse;
        verifyData(response.getData());
    }

    private void codecProtocolFailure() {
        CommandResponse command = codecResponseCommon(badResponse);

        assertEquals(command.getStatus(), CommandStatus.SLOT_ID_INVALID);
        assertNull(command.getResponse());
        assertNull(command.getException());
    }

    private void codecServiceException() {
        CommandResponse command = codecResponseCommon(cmdException);

        assertEquals(command.getStatus(), CommandStatus.SUCCESS);
        assertNull(command.getResponse());
        assertNotNull(command.getException());

        ServiceException exception = command.getException();
        assertEquals(exception.getMessage(), errorMessage);
    }

    private CommandResponse codecResponseCommon(CommandResponse cmdResponse) {
        ChannelBuffer result;

        encoder.offer(cmdResponse);
        result = encoder.poll();

        decoder.offer(result);

        return verifyResponse(decoder.poll());
    }

    private CommandResponse verifyResponse(SessionFrame frame) {
        assertTrue(frame instanceof CommandResponse);

        CommandResponse command = (CommandResponse) frame;

        assertEquals(command.getExchangeID(), exchangeID);
        assertEquals(command.getCommandSN(), commandSN);
        assertEquals(command.getExpectedCommandSN(), commandSN);
        assertEquals(command.getSlotID(), slotID);
        assertEquals(command.getSlotSN(), slotSN);
        assertEquals(command.getCurrentMaxSlotID(), maxSlotID);
        assertEquals(command.getTargetMaxSlotID(), maxSlotID);

        return command;
    }

    private void verifyData(ByteBuffer[] buffers) {
        int length = 0;

        for (int i = 0; i < buffers.length; i++) {
            length += buffers[i].remaining();
        }

        assertEquals(length, val1.getBytes().length + val2.getBytes().length);

        byte[] buf = new byte[length];
        int offset = 0;
        int count;

        for (int i = 0; i < buffers.length; i++) {
            count = buffers[i].remaining();
            buffers[i].get(buf, offset, count);
            offset += count;
        }

        String val = new String(buf);

        assertEquals(val, val1 + val2);
    }

    private static class TestTransportManager extends SessionTransportManager {

        private SessionTransport xport = new TestTransport();

        @Override
        public SessionTransport locate(Channel channel) {
            return xport;
        }

        @Override
        public SessionTransportHandler getConnectHandler(SessionTransport xport) {
            return null;
        }

        @Override
        public SessionTransportHandler getOperateHandler(SessionTransport xport) {
            return null;
        }
    }

    private static class TestTransport extends SessionTransport {

        private TestNexus nexus = new TestNexus();

        public TestTransport() {
            super(null);

            ClientConfig spec = nexus.getSpec();
            options = spec.getOptions().getTransportOptions();
        }

        @Override
        public boolean isClient() {
            return false;
        }

        @Override
        public SessionNexus getNexus() {
            return nexus;
        }
    }

    private static class TestNexus extends SessionNexus {

        private static final ServiceType type = new ServiceType(UUID.randomUUID(), "hello", "hello service");
        private static final HelloService helloService = new HelloService(type);
        private static final ClientConfig spec = new ClientConfig(null, null, helloService,
                ImmutableList.<ProtocolHandler<?>> of(helloService));

        public TestNexus() {
            super(spec);

            actVersion = SessionProtocol.V_1_0_0;
            options = spec.getOptions().getNexusOptions();
        }

        public ClientConfig getSpec() {
            return spec;
        }

        @Override
        public Collection<ServiceTransport> getTransports() {
            return null;
        }

        @Override
        public boolean isClient() {
            return false;
        }

        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public boolean isDegraded() {
            return false;
        }

        @Override
        public CloseFuture close() {
            return null;
        }

        @Override
        public EventSource getEventSource() {
            return null;
        }

        @Override
        public void stop() {

        }
    }
}
