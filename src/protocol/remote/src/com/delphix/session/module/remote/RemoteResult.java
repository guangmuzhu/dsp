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

package com.delphix.session.module.remote;

import static com.google.common.base.Preconditions.checkNotNull;

public class RemoteResult {

    private final int status;
    private final String stdout;
    private final String stderr;

    public RemoteResult(int status, String stdout, String stderr) {
        this.status = status;
        this.stdout = checkNotNull(stdout);
        this.stderr = checkNotNull(stderr);
    }

    public int getStatus() {
        return status;
    }

    public boolean hasStdout() {
        return !stdout.isEmpty();
    }

    public String getStdout() {
        return stdout;
    }

    public boolean hasStderr() {
        return !stderr.isEmpty();
    }

    public String getStderr() {
        return stderr;
    }
}
