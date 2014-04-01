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

package com.delphix.session.util;

import com.delphix.appliance.server.util.ExceptionUtil;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * This class implements a data source based on a file to be used with the data sender.
 */
public class LocalFileSource extends AbstractDataSource {

    private final String path;
    private final long offset;
    private final long length;

    private RandomAccessFile file;

    public LocalFileSource(String path, long offset, long length) {
        super();

        this.path = path;
        this.offset = offset;
        this.length = length;

        try {
            file = new RandomAccessFile(path, "r");

            if (offset > 0) {
                file.seek(offset);
            }
        } catch (IOException e) {
            throw ExceptionUtil.getDelphixException(e);
        }

        channel = file.getChannel();
    }

    public String getPath() {
        return path;
    }

    public long getOffset() {
        return offset;
    }

    public long getLength() {
        return length;
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            file.close();
        }
    }

    @Override
    public String toString() {
        return "file:" + path;
    }
}
