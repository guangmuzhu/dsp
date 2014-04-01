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

package com.delphix.checkstyle;

import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;
import com.puppycrawl.tools.checkstyle.api.AutomaticBean;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;

public class DelphixAuditListener extends AutomaticBean implements AuditListener {

    private String styleUrl;

    public void setStyleUrl(String styleUrl) {
        this.styleUrl = styleUrl;
    }

    @Override
    protected void finishLocalSetup() throws CheckstyleException {
        if (styleUrl == null)
            throw new CheckstyleException("the DelphixAuditListener requires a styleUrl property");
    }

    @Override
    public void auditStarted(AuditEvent arg0) {}

    @Override
    public void auditFinished(AuditEvent arg0) {}

    @Override
    public void fileStarted(AuditEvent arg0) {}

    @Override
    public void fileFinished(AuditEvent arg0) {}

    @Override
    public void addError(AuditEvent event) {
        printEvent(event);
    }

    @Override
    public void addException(AuditEvent event, Throwable t) {
        printEvent(event);
        t.printStackTrace();
    }

    private void printEvent(AuditEvent event) {
        System.out.println(event.getFileName() + ":" + event.getLine() + ":" + event.getColumn());
        System.out.println(event.getMessage());
        System.out.println("See " + styleUrl + event.getModuleId());
        System.out.println();
    }

}
