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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * This class describes the task management response.
 *
 *   o status
 *
 * The task management will be processed as follows. The slot sequence will not be updated if the command is aborted
 * before it is started.
 *
 *   if (targetExchangeID outstanding in slot table) {
 *       if (command abortable) {
 *           command aborted after start
 *       } else {
 *           command not abortable
 *       }
 *   } else if (targetCommandSequence < expectedCommandSequence) {
 *       command already completed
 *   } else if (command received out of sequence order) {
 *       command aborted before start
 *   } else {
 *       command aborted before arrival
 *   }
 */
public class TaskMgmtResponse extends OperateResponse {

    private TaskMgmtStatus status;

    public TaskMgmtResponse() {
        super();
    }

    public TaskMgmtStatus getStatus() {
        return status;
    }

    public void setStatus(TaskMgmtStatus status) {
        this.status = status;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        status = TaskMgmtStatus.values()[in.readByte()];
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeByte(status.ordinal());
    }
}
