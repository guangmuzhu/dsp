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

/**
 * Task management status enumeration.
 */
public enum TaskMgmtStatus {

    ALREADY_COMPLETED("command already completed"),
    ABORTED_BEFORE_ARRIVAL("command aborted before arrival"),
    ABORTED_BEFORE_START("command aborted before start"),
    ABORTED_AFTER_START("command aborted after start"),
    UNABORTABLE("command cannot be aborted"),
    ABORTED_SLOT_FAILURE("command with slot failure aborted");

    private final String desc;

    private TaskMgmtStatus(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
