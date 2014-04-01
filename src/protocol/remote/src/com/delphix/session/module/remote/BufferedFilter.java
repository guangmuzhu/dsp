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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * A simple pass-through stream filter implementation using the BufferedReader.
 */
public class BufferedFilter implements StreamFilter {

    protected InputStream source;
    protected BufferedReader reader;

    @Override
    public String read(InputStream source) throws IOException {
        setupReader(source);
        return filter();
    }

    protected void setupReader(InputStream source) {
        // Skip if we are reading from the same source as we did before
        if (this.source == source) {
            return;
        }

        this.source = source;

        reader = new BufferedReader(new InputStreamReader(source));
    }

    protected String filter() throws IOException {
        char[] buffer = new char[1024];

        int bytesRead = reader.read(buffer);

        if (bytesRead == -1) {
            return null;
        }

        return new String(buffer, 0, bytesRead);
    }
}
