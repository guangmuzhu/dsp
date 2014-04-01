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
 * Copyright (c) 2013, 2014 by Delphix. All rights reserved.
 */

package com.delphix.appliance.host.example.server;

import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ExampleServerLauncher {

    private static final String CONFIG_LOCATION = "META-INF/spring/example-server-context.xml";

    public static void main(String[] args) {
        AbstractApplicationContext context = new ClassPathXmlApplicationContext(CONFIG_LOCATION);
        try {
            final ExampleServer server = (ExampleServer) context.getBean("server");

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    server.fini();
                }
            });
        } finally {
            context.close();
        }
    }
}
