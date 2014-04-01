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
 * This class provides a lightweight implementation of the ObjectInput interface. It works in tandem with the
 * ExternalObjectOutput class to provide custom and efficient serialization support. The user is responsible for
 * its own object type management. Typically, that would involve mapping the class to a type identifier that has
 * an efficient wire encoding and instantiate the class corresponding to the type identifier without having to go
 * through slow and expensive reflection. The java objects to be used with the ExternalObjectInput must implement
 * the default no-arg constructor with public access. The default constructor should defer initialization til the
 * call to the readExternal method. For casual use, it also supports reading an object encoded in the java default
 * ObjectOutputStream format.
 */
public class ExternalObjectInput extends DataInputStream implements ObjectInput {

    public ExternalObjectInput(InputStream in) {
        super(in);
    }

    @Override
    public Object readObject() throws ClassNotFoundException, IOException {
        ObjectInputStream ois = new ObjectInputStream(this);
        return ois.readObject();
    }
}
