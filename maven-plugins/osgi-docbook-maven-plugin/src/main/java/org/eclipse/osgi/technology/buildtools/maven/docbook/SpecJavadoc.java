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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.tools.DiagnosticListener;
import javax.tools.DocumentationTool;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.osgi.tools.xmldoclet.XmlDoclet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates JavaDoc for the specification packages and converts it into one
 * docbook file per package, shared by all the output formats.
 */
final class SpecJavadoc {

    private static final Logger LOG = LoggerFactory.getLogger(SpecJavadoc.class);

    private final MavenProject project;
    private final Path baseDir;
    private final Path javadocDir;

    /**
     * @param project the project being built
     * @param baseDir the project base directory
     * @param javadocDir the directory to write the javadoc into
     */
    SpecJavadoc(MavenProject project, Path baseDir, Path javadocDir) {
        this.project = project;
        this.baseDir = baseDir;
        this.javadocDir = javadocDir;
    }

    /**
     * Generate JavaDoc for the specification packages and convert it into docbook
     * @param transformerFactory
     * @throws MojoExecutionException
     */
    void generate(TransformerFactory transformerFactory) throws MojoExecutionException {
        // Find the sources to document
        List<Path> sources = project.getCompileSourceRoots().stream()
                .map(baseDir::resolve)
                .flatMap(SpecJavadoc::findSources)
                .toList();

        if (sources.isEmpty()) {
            // The javadoc tool fails when handed no sources
            LOG.info("No sources to document, skipping the JavaDoc step");
            return;
        }

        // The ddf pass is written last, so it marks a complete earlier run
        Path marker = javadocDir.resolve("ddf.txt");
        if (isUpToDate(marker, sources)) {
            LOG.info("JavaDoc docbook in {} is up to date", javadocDir);
            return;
        }

        // Use the JDK documentation tools to build the raw javadoc with our custom doclet
        DocumentationTool documentationTool = ToolProvider.getSystemDocumentationTool();
        DiagnosticListener<? super JavaFileObject> diagnosticListener = new JavaDocProcessListener();
        StandardJavaFileManager fileManager = documentationTool
                .getStandardFileManager(diagnosticListener, null, UTF_8);

        StringWriter sw = new StringWriter();
        Boolean result;
        try {
            // The doclet has to resolve annotations such as
            // org.osgi.annotation.versioning.Version, so the module's compile
            // classpath has to be on it. An explicit -classpath REPLACES the
            // default one, so append what would have been used anyway.
            List<String> classpath = new ArrayList<>();
            try {
                classpath.addAll(project.getCompileClasspathElements());
            } catch (DependencyResolutionRequiredException e) {
                throw new MojoExecutionException("Failed to resolve compile classpath", e);
            }
            String inherited = System.getProperty("java.class.path");
            if (inherited != null && !inherited.isBlank()) {
                classpath.add(inherited);
            }
            List<String> options = new ArrayList<>(List.of(
                "-protected", "--show-members", "protected", "-encoding", "UTF-8",
                "-d", javadocDir.toString()));
            if (!classpath.isEmpty()) {
                options.add("-classpath");
                options.add(String.join(java.io.File.pathSeparator, classpath));
            }
            result = documentationTool.getTask(sw, fileManager, diagnosticListener, XmlDoclet.class,
                options,
                fileManager.getJavaFileObjectsFromPaths(sources)).call();
        } finally {
            LOG.debug("JavaDoc generation output:\n\n{}", sw.toString());
        }

        if(!Boolean.TRUE.equals(result)) {
            LOG.error("Failed to generate JavaDoc");
            throw new MojoExecutionException("Failed to generate JavaDoc");
        }

        // Transform the javadoc into docbook format
        try (InputStream xslt = getClass().getResourceAsStream("/docbook/xsl/javadoc2docbook.xsl")){
            StreamSource source = new StreamSource(xslt);
            source.setSystemId(javadocDir.resolve("javadoc2docbook.xsl").toUri().toString());
            Transformer transformer = transformerFactory.newTransformer(source);

            transformer.setParameter("destdir", javadocDir.toUri().toString());
            transformer.setParameter("ddf.only", 0);

            Path javadocXml = javadocDir.resolve("javadoc.xml");
            Path javadocTxt = javadocDir.resolve("javadoc.txt");

            try (Reader input = Files.newBufferedReader(javadocXml);
                    Writer output = Files.newBufferedWriter(javadocTxt, CREATE, TRUNCATE_EXISTING)) {
                StreamResult outputTarget = new StreamResult(output);
                outputTarget.setSystemId(javadocTxt.toUri().toString());
                transformer.transform(new StreamSource(input), outputTarget);
                LOG.debug("JavaDoc docbook generation complete");
            }

            transformer.setParameter("ddf.only", 1);

            try (Reader input = Files.newBufferedReader(javadocXml);
                    Writer output = Files.newBufferedWriter(marker, CREATE, TRUNCATE_EXISTING)) {
                StreamResult outputTarget = new StreamResult(output);
                outputTarget.setSystemId(marker.toUri().toString());
                transformer.transform(new StreamSource(input), outputTarget);
                LOG.debug("JavaDoc DDF generation complete");
            }
        } catch (IOException | TransformerException e) {
            LOG.error("Failed to generate docbook flavoured JavaDoc", e);
            throw new MojoExecutionException("Failed to make JavaDoc docbook files", e);
        }
        LOG.info("JavaDoc generation and transformation complete");
    }

    private static boolean isUpToDate(Path marker, List<Path> sources) throws MojoExecutionException {
        if (!Files.isRegularFile(marker)) {
            return false;
        }
        try {
            FileTime generated = Files.getLastModifiedTime(marker);
            for (Path p : sources) {
                if (Files.getLastModifiedTime(p).compareTo(generated) > 0) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to check the JavaDoc timestamps", e);
        }
    }

    /**
     * Recursively search for java source files
     *
     * @param p - a file or directory to check
     * @return A stream of source files
     */
    static Stream<Path> findSources(Path p) {
        try {
            if (Files.isDirectory(p)) {
                return StreamSupport.stream(
                        Files.newDirectoryStream(p).spliterator(), false)
                        .flatMap(SpecJavadoc::findSources);
            } else if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".java")) {
                return Stream.of(p);
            }
        } catch (IOException e) {
            LOG.error("Failed listing sources for path {}", p, e);
            throw new RuntimeException("Failed to list sources", e);
        }
        return Stream.empty();
    }
}
