/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugin.compiler;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

import java.util.Locale;

import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.services.MessageBuilder;
import org.apache.maven.api.services.MessageBuilderFactory;

/**
 * A Java compiler diagnostic listener which send the messages to the Maven logger.
 *
 * @author Martin Desruisseaux
 */
final class DiagnosticLogger implements DiagnosticListener<JavaFileObject> {
    /**
     * The logger where to send diagnostics.
     */
    private final Log logger;

    /**
     * The factory for creating message builders.
     */
    private final MessageBuilderFactory messageBuilderFactory;

    /**
     * The locale for compiler message.
     */
    private final Locale locale;

    /**
     * Number of errors or warnings.
     */
    private int numErrors, numWarnings;

    /**
     * Creates a listener which will send the diagnostics to the given logger.
     *
     * @param logger the logger where to send diagnostics
     * @param messageBuilderFactory the factory for creating message builders
     * @param locale the locale for compiler message
     */
    DiagnosticLogger(Log logger, MessageBuilderFactory messageBuilderFactory, Locale locale) {
        this.logger = logger;
        this.messageBuilderFactory = messageBuilderFactory;
        this.locale = locale;
    }

    /**
     * Invoked when the compiler emitted a warning.
     *
     * @param diagnostic the warning emitted by the Java compiler
     */
    @Override
    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        MessageBuilder message = messageBuilderFactory.builder();
        Diagnostic.Kind kind = diagnostic.getKind();
        String style;
        switch (kind) {
            case ERROR:
                style = ".error:-bold,f:red";
                break;
            case WARNING:
                style = ".warning:-bold,f:yellow";
                break;
            default:
                style = ".info:-bold,f:blue";
                break;
        }
        JavaFileObject source = diagnostic.getSource();
        if (source != null) {
            message.a(source.getName()).a(':');
        }
        long line = diagnostic.getLineNumber();
        long column = diagnostic.getColumnNumber();
        if (line != Diagnostic.NOPOS || column != Diagnostic.NOPOS) {
            message.style(style).a('[');
            if (line != Diagnostic.NOPOS) {
                message.a(line);
            }
            if (column != Diagnostic.NOPOS) {
                message.a(',').a(column);
            }
            message.a(']').resetStyle();
        }
        message.newline();
        String code = diagnostic.getCode();
        if (code != null) {
            message.style(style).a('[').a(code).a("] ").resetStyle();
        }
        String log = message.a(diagnostic.getMessage(locale)).toString();
        switch (kind) {
            case ERROR:
                logger.error(log);
                numErrors++;
                break;
            case WARNING:
                logger.warn(log);
                numWarnings++;
                break;
            default:
                logger.info(log);
                break;
        }
    }

    /**
     * Reports summary after the compilation finished.
     */
    void logSummary() {
        if (numWarnings != 0) {
            logger.info(numWarnings + " warnings");
        }
        if (numErrors != 0) {
            logger.info(numErrors + " errors");
        }
    }
}
