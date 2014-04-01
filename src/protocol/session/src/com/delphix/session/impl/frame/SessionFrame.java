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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * This class serves as the base for all session frames.
 *
 * Session communication is performed via exchanges. An exchange consists of a pair of related wire frames exchanged
 * between the two parties involved in the communication. One of the frames in the pair is a request and the other a
 * response.
 *
 * Exchanges serve different functions and therefore have different types. Some exchanges are initiated on behalf of
 * the application of the protocol while others the protocol itself. An exchange may be initiated either by the client
 * or by the server. The former is referred to as a forechannel exchange and the latter backchannel.
 *
 * The session frame constitutes the bulk of the data sent over the wire in a single protocol data unit with the only
 * exception being the frame header. The frame header has a sequence of bytes encoded in binary format that contains
 * the following information.
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
 *   |                                                                           |
 *   |                             ...           ...                             |
 *   |                                                                           |
 *   +---> +---------------+---------------+---------------+---------------+     |
 *         |  Frame Type   |             Session Frame                     /     |
 *         +---------------+                                               /     |
 *        +/                                                               /     |
 *         +---------------+---------------+---------------+---------------+ +---+
 *
 *
 *   o protocol identifier
 *
 * The ASCII characters 'D', 'S', and 'P' serve as protocol identifier.
 *
 *   o protocol data unit type
 *
 * There are two PDU types supported, namely the initial connect and everything else. The difference lies in the
 * protocol version.
 *
 *   o protocol version
 *
 * The major, minor, and revision fields together indicate the version of the protocol that has been agreed upon by
 * the peers and in active use. For the initial connect, these fields are reserved.
 *
 *   o frame offset
 *
 * The frame offset points to the offset of the frame itself. Typically, it is the size of the fixed portion of the
 * frame header. But additional frame header data can be supported in the future.
 *
 *   o protocol data unit length
 *
 * The PDU length is the total length that includes the frame header (both fixed and variable) as well as the frame
 * itself.
 *
 *
 * The frame itself has the following common fields.
 *
 *   o frame type
 *
 * The type of session frame which takes the ordinal value of the SessionFrameType enumeration.
 *
 *   o exchangeID
 *
 * Each exchange is uniquely identified among all other outstanding exchanges in the same session channel. Each of the
 * two frames in the same exchange includes the identifier for correlation purpose.
 *
 *   o commandSN
 *   o expectedCommandSN
 *
 * A session-wide 32-bit sequence number is established for each channel during session creation. Comparisons and
 * arithmetic on the sequence number use Serial Number Arithmetic as defined in [RFC1982], where SERIAL_BITS = 32.
 *
 * A command sent over a channel is always assigned the current sequence number of that channel and the sequence
 * number is advanced afterwards. Commands are delivered to the peer in the sequence order even though they may be
 * sent over different connections of the same session. Each frame, in the opposite direction as the command (i.e.,
 * from the peer back), carries the next sequence number as expected by the peer for acknowledgment purposes.
 *
 * Non-command exchanges may also carry command sequence. They refer to the current sequence number of the channel
 * but the sequence number is not advanced.
 *
 * A request carries the commandSN of the channel it is associate with and the expectedCommandSN of the opposite
 * channel in the same session. Vice versa for response.
 */
public abstract class SessionFrame implements Externalizable {

    public static final int PROTO_IDENT = 0x44535000; // Protocol identifier (DSP)
    public static final int HEADER_LENGTH = 0xC; // Frame header length (fixed only)
    public static final int TYPE_OFFSET = 0x4; // Frame type offset
    public static final int VERSION_OFFSET = 0x5; // Protocol version offset
    public static final int START_OFFSET = 0x8; // Frame start offset
    public static final int LENGTH_OFFSET = 0x9; // Frame length offset (including header)

    public static final int TYPE_CONNECT = 0x0; // Frame header type connect
    public static final int TYPE_VERSION = 0x1; // Frame header type version

    protected ExchangeID exchangeID; // Outstanding exchange identifier

    protected SerialNumber commandSN; // Command sequence (of the associated channel)
    protected SerialNumber expectedCommandSN; // Expected command sequence (of the opposite channel)

    protected SessionFrame() {

    }

    /**
     * Get the ID of the exchange this session frame belongs to.
     */
    public ExchangeID getExchangeID() {
        return exchangeID;
    }

    public void setExchangeID(ExchangeID exchangeID) {
        this.exchangeID = exchangeID;
    }

    public void setExchangeID() {
        setExchangeID(ExchangeID.allocate());
    }

    public SerialNumber getCommandSN() {
        return commandSN;
    }

    public void setCommandSN(SerialNumber commandSN) {
        this.commandSN = commandSN;
    }

    public SerialNumber getExpectedCommandSN() {
        return expectedCommandSN;
    }

    public void setExpectedCommandSN(SerialNumber expectedCommandSN) {
        this.expectedCommandSN = expectedCommandSN;
    }

    /**
     * Check if the session frame is for an exchange sent over the forechannel or backchannel.
     */
    public abstract boolean isForeChannel();

    /**
     * Check if the session frame is for a request or response.
     */
    public abstract boolean isRequest();

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        exchangeID = ExchangeID.deserialize(in);

        commandSN = SerialNumber.deserialize(in);
        expectedCommandSN = SerialNumber.deserialize(in);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        SessionFrameType type = SessionFrameType.getType(this.getClass());
        out.writeByte(type.ordinal());

        exchangeID.writeExternal(out);

        commandSN.writeExternal(out);
        expectedCommandSN.writeExternal(out);
    }

    public static SessionFrame deserialize(ObjectInput in) throws IOException, ClassNotFoundException {
        SessionFrameType type = SessionFrameType.values()[in.readByte()];

        // newInstance uses reflection but not class loading
        SessionFrame frame;

        try {
            frame = type.getObjectClass().newInstance();
        } catch (Exception e) {
            throw new IOException(e);
        }

        frame.readExternal(in);

        return frame;
    }
}
