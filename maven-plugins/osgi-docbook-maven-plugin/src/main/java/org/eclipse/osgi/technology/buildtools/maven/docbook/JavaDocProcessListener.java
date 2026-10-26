/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *******************************************************************************/
package org.eclipse.osgi.technology.buildtools.maven.docbook;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class JavaDocProcessListener implements DiagnosticListener<JavaFileObject> {
    private static final Logger LOG = LoggerFactory.getLogger(JavaDocProcessListener.class);

    @Override
    public void report(Diagnostic<? extends JavaFileObject> d) {
        if(d.getSource() != null) {
            switch(d.getKind()) {
            case ERROR:
                LOG.error("Diagnostic for {} at {}:{}. {}", d.getSource(), d.getLineNumber(), d.getColumnNumber(), d.getMessage(null));
                break;
            case WARNING:
            case MANDATORY_WARNING:
                LOG.warn("Diagnostic for {} at {}:{}. {}", d.getSource(), d.getLineNumber(), d.getColumnNumber(), d.getMessage(null));
                break;
            case NOTE:
                LOG.info("Diagnostic for {} at {}:{}. {}", d.getSource(), d.getLineNumber(), d.getColumnNumber(), d.getMessage(null));
                break;
            case OTHER:
                LOG.info("Diagnostic for {} at {}:{}. {}", d.getSource(), d.getLineNumber(), d.getColumnNumber(), d.getMessage(null));
                break;
            default:
                LOG.error("Unknown diagnostic {} for {}. {}", d.getKind(), d.getSource(), d.getMessage(null));
                break;
            }
        }
    }
}
