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

package com.delphix.session.module.remote;

import java.io.IOException;
import java.io.InputStream;

/**
 * This interface allows one to apply arbitrary filtering to the data read from an input source. For example, it
 * may be used to replace part of the input matching a certain pattern; or invoke different actions depending on
 * the input. The filter supports reading from the same or different sources.
 */
public interface StreamFilter {

    /**
     * Read the data from the input source, apply desired filtering, and return the result. It may block while
     * waiting for more data from the source.
     */
    public String read(InputStream source) throws IOException;
}
