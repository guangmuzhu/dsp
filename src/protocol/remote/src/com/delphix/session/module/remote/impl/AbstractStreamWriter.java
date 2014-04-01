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
 * Copyright (c) 2014 by Delphix. All rights reserved.
 */

package com.delphix.session.module.remote.impl;

import com.delphix.appliance.server.exception.DelphixInterruptedException;

public abstract class AbstractStreamWriter implements StreamWriter {

    private boolean ready;

    @Override
    public synchronized void startWrite() {
        while (!ready) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new DelphixInterruptedException(e);
            }
        }
    }

    @Override
    public synchronized void writeReady() {
        if (!ready) {
            ready = true;
            notify();
        }
    }
}
