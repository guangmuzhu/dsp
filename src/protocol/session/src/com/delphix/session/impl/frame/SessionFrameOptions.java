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

import com.delphix.session.service.ServiceOptions;
import com.delphix.session.util.CompressMethod;

import java.util.List;

import static com.delphix.session.impl.frame.SessionFrame.HEADER_LENGTH;
import static com.delphix.session.service.ServiceOption.*;

/**
 * This class describes the session frame codec options. The following codec options are currently supported.
 *
 *     header digest     - enable checksum on the frame header including both the fixed and the variable part
 *
 *     frame digest      - enable checksum on the frame body immediately following the header but not including the
 *                         service payload
 *
 *     payload digest    - enable checksum on the service payload either including the bulk data or not
 *
 *     payload compress  - enable compression on the service payload including the bulk data
 *
 * Multiple digest and compression algorithms are supported. New algorithms can be added with ease by extending the
 * DigestMethod and CompressMethod enumerations. The choice of digest algorithm can be made independently for header,
 * frame, and payload, which allows maximum flexibility for balancing speed v.s. collision proof.
 *
 * Given the relative small overhead of the protocol, it is strongly recommended that header and frame digest to be
 * enabled at all times, especially if the frame is to be routed during transit. Service payload should be protected
 * with digest too. If large amount of bulk data is included, a digest algorithm such as Adler32 might provide a
 * speedier option with minimal compromise on collision. If bulk data already includes checksum itself, the protocol
 * level digest should be opted out to avoid duplicate work.
 *
 * If compression is selected, the entire service payload shall be compressed, including any bulk data. If bulk data
 * is already compressed, the protocol level compression should be opted out, unless the rest of service payload is
 * large in size when compared to the bulk data. If payload digest is enabled too, the entire payload will be subject
 * to checksum.
 *
 * All digests are included in the variable part of the frame header as opposed to be included in line with the data
 * it covers. Such a design simplifies the wire tracer as it enables state-less decoding without having to know the
 * frame options which are negotiated only during login.
 *
 * The details on the encoding format as well as copy efficiency are discussed in the codec implementation.
 */
public class SessionFrameOptions {

    private DigestMethod headerDigest;
    private DigestMethod frameDigest;

    private DigestMethod payloadDigest;
    private boolean digestData;

    private CompressMethod payloadCompress;

    public SessionFrameOptions() {
        headerDigest = DigestMethod.DIGEST_NONE;
        frameDigest = DigestMethod.DIGEST_NONE;

        payloadDigest = DigestMethod.DIGEST_NONE;
        digestData = true;

        payloadCompress = CompressMethod.COMPRESS_NONE;
    }

    public SessionFrameOptions(ServiceOptions options) {
        List<String> methods;

        methods = options.getOption(HEADER_DIGEST);
        headerDigest = DigestMethod.valueOf(methods.get(0));

        methods = options.getOption(FRAME_DIGEST);
        frameDigest = DigestMethod.valueOf(methods.get(0));

        methods = options.getOption(PAYLOAD_DIGEST);
        payloadDigest = DigestMethod.valueOf(methods.get(0));

        digestData = options.getOption(DIGEST_DATA);

        methods = options.getOption(PAYLOAD_COMPRESS);
        payloadCompress = CompressMethod.valueOf(methods.get(0));
    }

    public DigestMethod getHeaderDigest() {
        return headerDigest;
    }

    public void setHeaderDigest(DigestMethod headerDigest) {
        this.headerDigest = headerDigest;
    }

    public DigestMethod getFrameDigest() {
        return frameDigest;
    }

    public void setFrameDigest(DigestMethod frameDigest) {
        this.frameDigest = frameDigest;
    }

    public DigestMethod getPayloadDigest() {
        return payloadDigest;
    }

    public void setPayloadDigest(DigestMethod payloadDigest) {
        this.payloadDigest = payloadDigest;
    }

    public boolean isPayloadDigested() {
        return payloadDigest != DigestMethod.DIGEST_NONE;
    }

    public CompressMethod getPayloadCompress() {
        return payloadCompress;
    }

    public void setPayloadCompress(CompressMethod payloadCompress) {
        this.payloadCompress = payloadCompress;
    }

    public boolean isPayloadCompressed() {
        return payloadCompress != CompressMethod.COMPRESS_NONE;
    }

    public boolean isDigestData() {
        return isPayloadDigested() && (digestData || isPayloadCompressed());
    }

    public void setDigestData(boolean digestData) {
        this.digestData = digestData;
    }

    public int getHeaderLength() {
        int length = HEADER_LENGTH;

        length += frameDigest.size();
        length += payloadDigest.size();
        length += headerDigest.size();

        return length;
    }

    public int getFrameDigestOffset() {
        return HEADER_LENGTH;
    }

    public int getPayloadDigestOffset() {
        return getFrameDigestOffset() + frameDigest.size();
    }

    public int getHeaderDigestOffset() {
        return getPayloadDigestOffset() + payloadDigest.size();
    }
}
