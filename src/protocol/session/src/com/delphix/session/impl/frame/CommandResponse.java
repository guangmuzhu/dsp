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

import com.delphix.session.service.ServiceException;
import com.delphix.session.service.ServiceResponse;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * This class describes a command response.
 *
 *   o slotID
 *   o slotSN
 *
 * The slot information - see CommandRequest for details.
 *
 *   o status
 *
 * This field refers to the command delivery status pertaining to the protocol processing. The service specific status
 * is included in the service response or exception.
 *
 *   o response
 *   o exception
 *
 * The service specific response or exception.
 *
 * The serialization of the service payload is handled differently from that of the enclosing session frame. For one,
 * the latter follows a binary format defined as part of the session protocol; whereas the former uses an application
 * specified encoding, which may be json, protobuf, or plain java serializable, to name a few. Another motivation for
 * the separate treatment stems from the fact that we support the user of different frame options, such as digest and
 * compression, for session frame than service payload, which may drastically change the codec flow. Last but not the
 * least, bulk data requires "raw" treatment that is yet different from the enclosing service payload.
 */
public class CommandResponse extends OperateResponse {

    private int slotID;
    private SerialNumber slotSN;

    private CommandStatus status;

    private ServiceResponse response;
    private ServiceException exception;

    public CommandResponse() {
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

    public CommandStatus getStatus() {
        return status;
    }

    public void setStatus(CommandStatus status) {
        this.status = status;
    }

    public ServiceResponse getResponse() {
        return response;
    }

    public void setResponse(ServiceResponse response) {
        this.response = response;
    }

    public ServiceException getException() {
        return exception;
    }

    public void setException(ServiceException exception) {
        this.exception = exception;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        slotID = in.readInt();
        slotSN = SerialNumber.deserialize(in);

        status = CommandStatus.values()[in.readByte()];
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeInt(slotID);
        slotSN.writeExternal(out);

        out.writeByte(status.ordinal());
    }
}
