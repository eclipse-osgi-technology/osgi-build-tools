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

import java.io.File;
import java.util.List;

import javax.inject.Inject;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

/**
 * The parameters that every goal of the plugin shares. A parameter shared by
 * several goals is declared here with one user property,
 * {@code osgi.docbook.<parameter>}. A parameter of a single goal has the
 * property {@code osgi.docbook.<goal>.<parameter>}, and so do the {@code skip}
 * and {@code attach} parameters that each goal declares for itself.
 */
public abstract class AbstractSpecMojo extends AbstractMojo {

    /** The DocBook source of the chapter, relative to the project base directory */
    @Parameter(property = "osgi.docbook.chapterSource", defaultValue = "src/main/resources/spec/spec.xml")
    String chapterSource;

    /**
     * The sources of the API to document, e.g. {@code ../api/src/main/java}
     * for a specification module next to the API module. Roots that do not
     * exist are ignored; without any, the compile source roots are documented.
     */
    @Parameter(property = "osgi.docbook.apiSourceRoots")
    List<File> apiSourceRoots;

    @Inject
    MavenProject project;

    @Inject
    MavenProjectHelper helper;
}
