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

package com.delphix.session.service;

import org.apache.commons.lang.StringUtils;

import java.util.*;

/**
 * This class describes the service options. All supported service options are defined here. Each option definition
 * includes the metadata that describes the name, scope, and target of the option, the data type and optional default
 * of its associated value, as well as how to validate, encode/decode, and evaluate/negotiate option values.
 *
 * The option name takes a dot delimited form. It is prefixed with the option scope, which may be either nexus or
 * xport. The scope prefix is followed by one or more name component(s) describing the option itself. Optionally,
 * it may have a target suffix, which may be either local or client or server.
 *
 * Option Scope
 *
 *   nexus.     the option is applicable nexus wide
 *   xport.     the option is applicable transport wide
 *
 * Option Target
 *
 *   .client    the option is applicable only to the client (special case of local)
 *   .server    the option is applicable only to the server (special case of local)
 *   .local     the option is applicable to both client and server independently without negotiation
 *   <none>     the option is applicable to both client and server but requires negotiation
 *
 * Protocol options (those that require negotiation) with nexus scope are negotiated once when the nexus is created
 * for the first time. We do not support renegotiation of nexus scoped protocol options. Those with transport scope
 * are negotiated every time the transport is logged in.
 *
 * Service option can not be instantiated outside of this class. To use it, simply refer to the options statically
 * defined here.
 *
 * The following is a listing of the service options currently supported.
 *
 *   SOCKET_SEND_BUFFER         xport.socketSendBuffer.local
 *
 *                              Standard socket option for output buffer size.
 *
 *   SOCKET_RECEIVE_BUFFER      xport.socketReceiveBuffer.local
 *
 *                              Standard socket option for input buffer size.
 *
 *   REUSE_ADDRESS              xport.reuseAddress.local
 *
 *                              Standard socket option for enabling local address reuse.
 *
 *   KEEP_ALIVE                 xport.keepAlive.local
 *
 *                              Standard socket option for connection keep alive.
 *
 *   WRITE_HIGH_WATERMARK       xport.writeHighWatermark.local
 *
 *                              Output queue high water mark in bytes. When exceeded, the transport would indicate to
 *                              the application that writes need to be throttled to avoid out of memory eventually.
 *                              The indicator is reset when the output queue dips below the low water mark.
 *
 *   WRITE_LOW_WATERMARK        xport.writeLowWatermark.local
 *
 *                              Output queue low water mark in bytes.
 *
 *   CONNECT_TIMEOUT            xport.connectTimeout.local
 *
 *                              Transport connect timeout in milliseconds. If the connection isn't established before
 *                              the timeout, the connect attempt is failed and the transport closed.
 *
 *   RECOVERY_INTERNAL          xport.recoveryInterval.client
 *
 *                              The interval to wait (in milliseconds) on the client before attempt recovery for the
 *                              first time after transport failure. Subsequent recovery attempts following consecutive
 *                              failures are scheduled with power-of-two exponential back-off from the initial wait
 *                              interval. The valid range is [1,000, 60,000] and the default is 10,000.
 *
 *   RECOVERY_TIMEOUT           xport.recoveryTimeout.client
 *
 *                              The maximum time to wait (in milliseconds) before attempting recovery on a failed
 *                              transport. It caps the interval determined by the exponential back-off. The valid
 *                              range is [120,000, 3,600,000] and the default is 300,000.
 *
 *   HEADER_DIGEST              nexus.headerDigest
 *
 *                              The digest mechanism used for the checksum of the frame header. The actual value in
 *                              use is determined by INTERSECT(client proposal, server offer), with the proposal and
 *                              the offer being lists of mechanisms in preference order. The supported mechanisms
 *                              include DIGEST_NONE, DIGEST_ADLER32, and DIGEST_CRC32. The default is DIGEST_NONE.
 *
 *   FRAME_DIGEST               nexus.frameDigest
 *
 *                              The digest mechanism used for the checksum of the frame body (with the exception of
 *                              the service payload which is determined by the PAYLOAD_DIGEST). The actual value in
 *                              use is determined by INTERSECT(client proposal, server offer), with the proposal and
 *                              the offer being lists of mechanisms in preference order. The supported mechanisms
 *                              include DIGEST_NONE, DIGEST_ADLER32, and DIGEST_CRC32. The default is DIGEST_NONE.
 *
 *   PAYLOAD_DIGEST             nexus.payloadDigest
 *
 *                              The digest mechanism used for the checksum of the service payload, such as the service
 *                              request/response and tje service exception. The actual value in use is determined by
 *                              INTERSECT(client proposal, server offer), with the proposal and the offer being lists
 *                              of mechanisms in preference order. The supported mechanisms include DIGEST_NONE,
 *                              DIGEST_ADLER32, and DIGEST_CRC32. The default is DIGEST_NONE.
 *
 *   PAYLOAD_COMPRESS           nexus.payloadCompress
 *
 *                              The compression mechanism used for the compression of the service payload. The actual
 *                              value in use is determined by INTERSECT(client proposal, server offer), with the
 *                              proposal and the offer being lists of mechanisms in preference order. The supported
 *                              mechanisms include COMPRESS_NONE, COMPRESS_DEFLATE, and COMPRESS_GZIP. The default is
 *                              COMPRESS_NONE.
 *
 *   DIGEST_DATA                nexus.digestData
 *
 *                              Whether to include optional service bulk data in the payload digest calculation. The
 *                              actual value in use in determined by OR(client proposal, server offer). It's a boolean
 *                              value and the default is false.
 *
 *   FORE_QUEUE_DEPTH           nexus.forechannel.queueDepth
 *
 *                              The maximum number of outstanding commands allowed to be issued over the forechannel.
 *                              The actual value in use is determined by MIN(client proposal, server offer). The valid
 *                              range is [1, 4096] and the default is 32.
 *
 *   BACK_QUEUE_DEPTH           nexus.backchannel.queueDepth
 *
 *                              Similar to FORE_QUEUE_DEPTH except over the backchannel.
 *
 *   FORE_MAX_REQUEST           nexus.forechannel.maxRequest
 *
 *                              The maximum encoded length of request frame over the forechannel in bytes. The actual
 *                              value in use is determined by MIN(client proposal, server offer). The valid range is
 *                              [8KB, 16MB] and the default is 64KB.
 *
 *   BACK_MAX_REQUEST           nexus.backchannel.maxRequest
 *
 *                              Similar to FORE_MAX_REQUEST except over the backchannel.
 *
 *   FORE_MAX_RESPONSE          nexus.forechannel.maxResponse
 *
 *                              Similar to FORE_MAX_REQUEST except for the response frame.
 *
 *   BACK_MAX_RESPONSE          nexus.backchannel.maxResponse
 *
 *                              Similar to BACK_MAX_REQUEST except for the response frame.
 *
 *   ORDERED_EXECUTION          nexus.orderedExecution
 *
 *                              Whether commands are always executed in the same order as they are submitted over the
 *                              nexus. The actual value in use is determined by AND(client proposal, server offer).
 *                              It's a boolean value and the default is true.
 *
 *   MAX_TRANSPORTS             nexus.maxTransports
 *
 *                              The maximum number of transports allowed to be logged in simultaneously to the same
 *                              nexus. The actual value in use is determined by MIN(client proposal, server offer).
 *                              The valid range is [1, 64] and the default is 32.
 *
 *   MIN_KEEPALIVE_TIME         nexus.minKeepAlive
 *
 *                              The minimum time (in seconds) a nexus shall be kept alive on the server after it
 *                              has lost all transports. Within this time period, nexus continuation attempt from
 *                              the client is guaranteed not to fail due to state loss. The actual value in use is
 *                              determined by MIN(client proposal, server offer). The valid range is [0, 86,400]
 *                              and the default is 3,600.
 *
 *   LOGOUT_TIMEOUT             nexus.logoutTimeout
 *
 *                              The maximum time (in seconds) before server responds to the client initiated logout
 *                              request. Upon logout request, an application service may want to quiesce on going
 *                              activities on the session, such as outstanding commands issued over the backchannel.
 *                              However, the client should not be kept waiting indefinitely. In case logout is not
 *                              granted in time, the client is responded to anyways when the timeout occurs.  The
 *                              actual value in use is determined by MIN(client proposal, server offer). The valid
 *                              range is [0, 60] and the default is 0 which means logout immediately.
 *
 *   SYNC_DISPATCH              nexus.syncDispatch.local
 *
 *                              Whether commands are dispatched synchronously or not. In synchronous dispatch mode,
 *                              if command dispatch has to be delayed, the dispatch context will block until either
 *                              the command is ready to be dispatched or it is terminated, whichever happens first.
 *                              The actual dispatch is processed within the dispatch context. Blocking the dispatch
 *                              context helps to prevent the flooding of the command queue. It's a boolean value and
 *                              the default is true. Set this to false if a completely asynchronous invocation mode
 *                              is desired.
 *
 *   XPORT_SCHED                nexus.xportSched.local
 *
 *                              The transport scheduler to use for request dispatching. In case of multi-connection
 *                              session, there may be more than one connections at a time attached to a session
 *                              channel. The scheduler is responsible for selecting a transport to dispatch the
 *                              request on. It is a string value and the default is round-robin.
 */
public abstract class ServiceOption<T> {

    // Supported options
    private static final Set<ServiceOption<?>> options = new HashSet<ServiceOption<?>>();

    private static final Set<String> schedSupported = new HashSet<String>();

    private static final Set<String> digestSupported = new HashSet<String>();
    private static final Set<String> compressSupported = new HashSet<String>();

    static {
        // Currently supported digest methods
        digestSupported.add("DIGEST_NONE");
        digestSupported.add("DIGEST_ADLER32");
        digestSupported.add("DIGEST_CRC32");

        // Currently supported compress methods
        compressSupported.add("COMPRESS_NONE");
        compressSupported.add("COMPRESS_DEFLATE");
        compressSupported.add("COMPRESS_GZIP");
        compressSupported.add("COMPRESS_LZ4");

        // Currently supported schedulers
        schedSupported.add("ROUND_ROBIN");
        schedSupported.add("LEAST_QUEUE");
    }

    // Default digest and compression offers
    private static final List<String> digestNone = Arrays.asList("DIGEST_NONE");
    private static final List<String> compressNone = Arrays.asList("COMPRESS_NONE");

    // Option definitions
    public static final ServiceOption<Integer> SOCKET_SEND_BUFFER =
            new ServiceOptionInteger("xport.socketSendBuffer.local", 4096, 16777216, 262144);

    public static final ServiceOption<Integer> SOCKET_RECEIVE_BUFFER =
            new ServiceOptionInteger("xport.socketReceiveBuffer.local", 4096, 16777216, 262144);

    public static final ServiceOption<Integer> WRITE_HIGH_WATERMARK =
            new ServiceOptionInteger("xport.writeHighWatermark.local", 65536, 16777216, 262144);

    public static final ServiceOption<Integer> WRITE_LOW_WATERMARK =
            new ServiceOptionInteger("xport.writeLowWatermark.local", 4096, 16777216, 65536);

    public static final ServiceOption<Integer> CONNECT_TIMEOUT =
            new ServiceOptionInteger("xport.connectTimeout.local", 1 * 1000, 300 * 1000, 10 * 1000);

    public static final ServiceOption<Boolean> REUSE_ADDRESS =
            new ServiceOptionBoolean("xport.reuseAddress.local", true);

    public static final ServiceOption<Boolean> KEEP_ALIVE =
            new ServiceOptionBoolean("xport.keepAlive.local", true);

    public static final ServiceOption<Integer> RECOVERY_INTERVAL =
            new ServiceOptionInteger("xport.recoveryInterval.client", 1 * 1000, 60 * 1000, 10 * 1000);

    public static final ServiceOption<Integer> RECOVERY_TIMEOUT =
            new ServiceOptionInteger("xport.recoveryTimeout.client", 120 * 1000, 3600 * 1000, 300 * 1000);

    public static final ServiceOption<List<String>> HEADER_DIGEST =
            new ServiceOptionStringList("nexus.headerDigest", digestSupported, digestNone);

    public static final ServiceOption<List<String>> FRAME_DIGEST =
            new ServiceOptionStringList("nexus.frameDigest", digestSupported, digestNone);

    public static final ServiceOption<List<String>> PAYLOAD_DIGEST =
            new ServiceOptionStringList("nexus.payloadDigest", digestSupported, digestNone);

    public static final ServiceOption<List<String>> PAYLOAD_COMPRESS =
            new ServiceOptionStringList("nexus.payloadCompress", compressSupported, compressNone);

    public static final ServiceOption<Boolean> DIGEST_DATA =
            new ServiceOptionBoolean("nexus.digestData", false);

    public static final ServiceOption<Integer> FORE_QUEUE_DEPTH =
            new ServiceOptionInteger("nexus.forechannel.queueDepth", 1, 4096, 32);

    public static final ServiceOption<Integer> BACK_QUEUE_DEPTH =
            new ServiceOptionInteger("nexus.backchannel.queueDepth", 1, 4096, 32);

    public static final ServiceOption<Integer> FORE_MAX_REQUEST =
            new ServiceOptionInteger("nexus.forechannel.maxRequest", 8192, 16777215, 65536);

    public static final ServiceOption<Integer> BACK_MAX_REQUEST =
            new ServiceOptionInteger("nexus.backchannel.maxRequest", 8192, 16777215, 65536);

    public static final ServiceOption<Integer> FORE_MAX_RESPONSE =
            new ServiceOptionInteger("nexus.forechannel.maxResponse", 8192, 16777215, 65536);

    public static final ServiceOption<Integer> BACK_MAX_RESPONSE =
            new ServiceOptionInteger("nexus.backchannel.maxResponse", 8192, 16777215, 65536);

    public static final ServiceOption<Boolean> ORDERED_EXECUTION =
            new ServiceOptionBoolean("nexus.orderedExecution", true) {

                @Override
                public Boolean negotiate(Boolean offered, Boolean proposed) {
                    return offered && proposed;
                }
            };

    public static final ServiceOption<Integer> MAX_TRANSPORTS =
            new ServiceOptionInteger("nexus.maxTransports", 1, 64, 32);

    public static final ServiceOption<Integer> MIN_KEEPALIVE_TIME =
            new ServiceOptionInteger("nexus.minKeepAlive", 0, 24 * 60 * 60, 60 * 60);

    public static final ServiceOption<Integer> LOGOUT_TIMEOUT =
            new ServiceOptionInteger("nexus.logoutTimeout", 0, 60, 0);

    public static final ServiceOption<Boolean> SYNC_DISPATCH =
            new ServiceOptionBoolean("nexus.syncDispatch.local", true);

    public static final ServiceOption<Integer> BANDWIDTH_LIMIT =
            new ServiceOptionInteger("nexus.bandwidthLimit.local", 0, Integer.MAX_VALUE, 0);

    public static final ServiceOption<String> XPORT_SCHEDULER =
            new ServiceOptionString("nexus.xportSched.local", schedSupported, "ROUND_ROBIN");

    public static Set<ServiceOption<?>> supportedOptions() {
        Set<ServiceOption<?>> supported = new HashSet<ServiceOption<?>>();
        supported.addAll(options);
        return supported;
    }

    public static Set<ServiceOption<?>> clientOptions() {
        Set<ServiceOption<?>> clientOptions = new HashSet<ServiceOption<?>>();

        for (ServiceOption<?> option : options) {
            if (option.isClient()) {
                clientOptions.add(option);
            }
        }

        return clientOptions;
    }

    public static Set<ServiceOption<?>> serverOptions() {
        Set<ServiceOption<?>> serverOptions = new HashSet<ServiceOption<?>>();

        for (ServiceOption<?> option : options) {
            if (option.isServer()) {
                serverOptions.add(option);
            }
        }

        return serverOptions;
    }

    public static ServiceOption<?> getOption(String name) {
        for (ServiceOption<?> option : options) {
            if (option.getName().equals(name)) {
                return option;
            }
        }

        throw new IllegalArgumentException("unsupported option " + name);
    }

    private static boolean hasOption(String name) {
        for (ServiceOption<?> option : options) {
            if (option.getName().equals(name)) {
                return true;
            }
        }

        return false;
    }

    protected final String name; // Option name
    protected final T value; // Default value (optional)

    private ServiceOption(String name) {
        this(name, null);
    }

    private ServiceOption(String name, T value) {
        this.name = name;
        this.value = value;

        if (hasOption(name)) {
            throw new IllegalArgumentException("duplicate option name " + name);
        }

        options.add(this);
    }

    public String getName() {
        return name;
    }

    public boolean hasDefault() {
        return value != null;
    }

    public T getDefault() {
        return value;
    }

    public T getMaximum() {
        throw new UnsupportedOperationException();
    }

    public T getMinimum() {
        throw new UnsupportedOperationException();
    }

    public boolean isNexus() {
        return name.startsWith("nexus.");
    }

    public boolean isClient() {
        return !name.endsWith(".server");
    }

    public boolean isServer() {
        return !name.endsWith(".client");
    }

    public boolean isLocal() {
        return name.endsWith(".client") || name.endsWith(".server") || name.endsWith(".local");
    }

    public String encode(T value) {
        return value.toString();
    }

    public abstract T decode(String value);

    public void validate(T value) {
        if (value == null) {
            throw new IllegalArgumentException("null value for " + name);
        }
    }

    public abstract T negotiate(T offered, T proposed);

    private static class ServiceOptionString extends ServiceOption<String> {

        private final Set<String> allowed;

        ServiceOptionString(String name, Set<String> allowed, String value) {
            super(name, value);

            this.allowed = allowed;
        }

        @Override
        public void validate(String value) {
            super.validate(value);

            if (!allowed.contains(value)) {
                throw new IllegalArgumentException(value + " unsupported for " + name);
            }
        }

        @Override
        public String decode(String value) {
            return value;
        }

        @Override
        public String negotiate(String offered, String proposed) {
            if (!offered.equals(proposed)) {
                throw new IllegalArgumentException(proposed + " unsupported for " + name);
            }

            return offered;
        }
    }

    private static class ServiceOptionStringList extends ServiceOption<List<String>> {

        private final Set<String> allowed;

        ServiceOptionStringList(String name, Set<String> allowed, List<String> value) {
            super(name, value);

            this.allowed = allowed;
        }

        @Override
        public void validate(List<String> value) {
            super.validate(value);

            if (!allowed.containsAll(value)) {
                throw new IllegalArgumentException(value + " unsupported for " + name);
            }
        }

        @Override
        public List<String> negotiate(List<String> offered, List<String> proposed) {
            List<String> result = new ArrayList<String>(proposed);
            result.retainAll(offered);

            if (result.isEmpty()) {
                throw new IllegalArgumentException(proposed + " unsupported for " + name);
            }

            return result;
        }

        @Override
        public String encode(List<String> value) {
            return StringUtils.join(value, ',');
        }

        @Override
        public List<String> decode(String value) {
            return Arrays.asList(StringUtils.split(value, ','));
        }
    }

    private static class ServiceOptionInteger extends ServiceOption<Integer> {

        private final int lowerBound;
        private final int upperBound;

        ServiceOptionInteger(String name, int lowerBound, int upperBound, int value) {
            super(name, value);

            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
        }

        @Override
        public Integer getMaximum() {
            return Integer.valueOf(upperBound);
        }

        @Override
        public Integer getMinimum() {
            return Integer.valueOf(lowerBound);
        }

        @Override
        public void validate(Integer value) {
            super.validate(value);

            if (value < lowerBound || value > upperBound) {
                throw new IllegalArgumentException(value + " out of bound for " + name);
            }
        }

        @Override
        public Integer negotiate(Integer offered, Integer proposed) {
            return Integer.valueOf(Math.min(offered, proposed));
        }

        @Override
        public Integer decode(String value) {
            return Integer.valueOf(value);
        }
    }

    private static class ServiceOptionBoolean extends ServiceOption<Boolean> {

        ServiceOptionBoolean(String name, Boolean value) {
            super(name, value);
        }

        @Override
        public Boolean negotiate(Boolean offered, Boolean proposed) {
            return offered || proposed;
        }

        @Override
        public Boolean decode(String value) {
            return Boolean.valueOf(value);
        }
    }
}
