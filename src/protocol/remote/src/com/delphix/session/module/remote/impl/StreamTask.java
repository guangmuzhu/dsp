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

package com.delphix.session.module.remote.impl;

import java.io.Closeable;

/**
 * This interface describes a long running stream task that may involve multiple file exchanges.
 */
public interface StreamTask extends Closeable {

    /**
     * Return the tag uniquely identifying the stream task.
     */
    public int getTag();

    /**
     * Set the tag uniquely identifying the stream task.
     */
    public void setTag(int tag);
}
