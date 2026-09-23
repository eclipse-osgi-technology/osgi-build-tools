/*******************************************************************************
 * Copyright (c) Contributors to the Eclipse Foundation
 *
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
 *
 * SPDX-License-Identifier: Apache-2.0
 *******************************************************************************/
package org.eclipse.osgi.technology.buildtools.maven.docbook;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.saxon.TransformerFactoryImpl;
import net.sf.saxon.lib.FeatureKeys;

/**
 * The steps shared by every specification output format: parameter checks,
 * javadoc generation and the virtual book set-up.
 */
public abstract class AbstractOsgiSpecMojo extends AbstractMojo {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractOsgiSpecMojo.class);

    @Parameter(property = "osgi.docbook.spec.source", defaultValue = "src/main/resources/spec/spec.xml")
    String chapterSource;

    @Parameter(property = "osgi.docbook.spec.title", defaultValue = "${project.name}")
    String specTitle;

    @Inject
    MavenProject project;

    @Inject
    MavenProjectHelper helper;

    /** The base name of every output file */
    String outputFileBaseName;

    /** The project base directory */
    Path baseDir;

    /** The build directory shared by the output formats */
    Path specDir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (isSkipped()) {
            LOG.info("Skipping the specification {} generation", formatName());
            return;
        }

        // Check parameters
        if(specTitle == null || specTitle.isBlank() || "null".equals(specTitle)) {
            LOG.error("No specification title");
            throw new MojoFailureException("A specification title must be supplied. It will default to the project name if that is available");
        }

        outputFileBaseName = project.getBuild().getFinalName();
        if(outputFileBaseName == null || outputFileBaseName.isBlank() || "null".equals(outputFileBaseName)) {
            outputFileBaseName = project.getArtifactId() + "." + project.getVersion();
        }
        LOG.info("Using {} as the base file name for specification outputs", outputFileBaseName);

        // Set up folders
        baseDir = project.getBasedir().toPath().toAbsolutePath();
        specDir = baseDir.resolve(project.getBuild().getDirectory()).resolve("spec");
        Path formatDir = specDir.resolve(formatName());
        try {
            Files.createDirectories(formatDir);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create " + formatDir, e);
        }

        new SpecJavadoc(project, baseDir, specDir.resolve("javadoc")).generate(newTransformerFactory());

        SpecBook book = SpecBook.setup(baseDir.resolve(chapterSource), specDir, specTitle,
                project.getVersion(), newTransformerFactory());

        generate(book, formatDir);
    }

    /**
     * @return the name of the output format, also used as its build directory name
     */
    abstract String formatName();

    /**
     * @return true if this format should not be generated
     */
    boolean isSkipped() {
        return false;
    }

    /**
     * Generate the output format from the book
     * @param book the specification book
     * @param formatDir the build directory for this format
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    abstract void generate(SpecBook book, Path formatDir) throws MojoExecutionException, MojoFailureException;

    /**
     * Each step gets its own factory, because Saxon does not allow the URI
     * resolver of a factory to be reset once it has been set.
     *
     * @return a new Saxon transformer factory
     * @throws MojoExecutionException
     */
    static TransformerFactory newTransformerFactory() throws MojoExecutionException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance(
                TransformerFactoryImpl.class.getName(), TransformerFactoryImpl.class.getClassLoader());
        try {
            transformerFactory.setFeature(FeatureKeys.IGNORE_SAX_SOURCE_PARSER, false);
        } catch (TransformerConfigurationException e) {
            throw new MojoExecutionException(e);
        }
        return transformerFactory;
    }
}
