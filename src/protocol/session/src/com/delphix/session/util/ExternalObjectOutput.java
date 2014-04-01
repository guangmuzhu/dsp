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

import java.io.*;

/**
 * This class provides a lightweight implementation of the ObjectOutput interface. Contrary to the full featured
 * ObjectOutputStream found in java, this class merely extends the DataOutputStream class for support of primitive
 * types and nothing else beyond that. As a result, the user is responsible for its own object type management
 * instead of the bulky and slow object class descriptor support in java. The java objects to be used with the
 * ExternalObjectOutput should implement the Externalizable interface, as the name implies, for custom encoding.
 * To encode such an object, the writeExternal method should be called with ExternalObjectOutput. For casual use,
 * it is also possible to call writeObject on ExternalObjectOutput for an object that implements the Serializable
 * interface. But keep in mind that doing so will write out the full java class descriptor.
 */
public class ExternalObjectOutput extends DataOutputStream implements ObjectOutput {

    public ExternalObjectOutput(OutputStream out) {
        super(out);
    }

    @Override
    public void writeObject(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);

        try {
            out.writeObject(obj);
        } finally {
            out.close();
        }

        super.write(baos.toByteArray());
    }
}
