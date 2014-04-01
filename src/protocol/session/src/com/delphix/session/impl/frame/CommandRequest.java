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

import com.delphix.session.service.ServiceRequest;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * This class describes the command request.
 *
 *   o slotID
 *   o slotSN
 *
 * Each command sent in either channel over the session must be assigned a slot while it remains outstanding. There
 * are two pieces of information pertaining to each slot, referred to as slotID and slotSN, carried in an exchange.
 * The former serves to identify the slot while the latter the instance of command exchange processed over the slot.
 * The slotSN is used by the owner of the slot table for duplicate request detection and by the peer for response
 * acknowledgment.
 *
 *   o idempotent
 *
 * This flag indicates whether the command carries an idempotent request or not. If yes, the full response won't be
 * cached in the slot.
 *
 *   o request
 *
 * The service specific request.
 *
 * The serialization of the service payload is handled differently from that of the enclosing session frame. For one,
 * the latter follows a binary format defined as part of the session protocol; whereas the former uses an application
 * specified encoding, which may be json, protobuf, or plain java serializable, to name a few. Another motivation for
 * the separate treatment stems from the fact that we support the user of different frame options, such as digest and
 * compression, for session frame than service payload, which may drastically change the codec flow. Last but not the
 * least, bulk data requires "raw" treatment that is yet different from the enclosing service payload.
 */
public class CommandRequest extends OperateRequest {

    private int slotID;
    private SerialNumber slotSN;
    private boolean idempotent;
    private int compressedDataSize;

    private ServiceRequest request;

    public CommandRequest() {
        super();
    }

    public int getSlotID() {
        return slotID;
    }

    public void setSlotID(int slotID) {
        this.slotID = slotID;
    }

    public SerialNumber getSlotSN() {
        return slotSN;
    }

    public void setSlotSN(SerialNumber slotSN) {
        this.slotSN = slotSN;
    }

    public boolean isIdempotent() {
        return idempotent;
    }

    public void setIdempotent(boolean idempotent) {
        this.idempotent = idempotent;
    }

    public ServiceRequest getRequest() {
        return request;
    }

    public void setRequest(ServiceRequest request) {
        this.request = request;
    }

    public int getCompressedDataSize() {
        return compressedDataSize;
    }

    public void setCompressedDataSize(int compressedDataSize) {
        this.compressedDataSize = compressedDataSize;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        slotID = in.readInt();
        slotSN = SerialNumber.deserialize(in);

        idempotent = in.readBoolean();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeInt(slotID);
        slotSN.writeExternal(out);

        out.writeBoolean(idempotent);
    }
}
