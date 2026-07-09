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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.inject.Inject;
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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.osgi.tools.xmldoclet.XmlDoclet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.saxon.TransformerFactoryImpl;

@Mojo(name = "pdf", defaultPhase = LifecyclePhase.COMPILE)
public class OsgiSpecPdfMojo extends AbstractMojo {

    private static final Logger LOG = LoggerFactory.getLogger(OsgiSpecPdfMojo.class);
    
    @Parameter(property = "osgi.docbook.spec", defaultValue = "src/main/resources/spec/spec.xml")
    String chapterSource;
    
    @Inject
    MavenProject project;
    
    public void execute() throws MojoExecutionException {
        Path baseDir = project.getBasedir().toPath().toAbsolutePath();
        Path buildDir = baseDir.resolve(project.getBuild().getDirectory()).resolve("spec/pdf");
        
        TransformerFactory transformerFactory = TransformerFactory.newInstance(
                TransformerFactoryImpl.class.getName(), TransformerFactoryImpl.class.getClassLoader());
        
        makeJavaDoc(baseDir, buildDir, transformerFactory);
        
        LOG.info("JavaDoc generation and transformation complete");
        
        setupBook(baseDir, buildDir, transformerFactory);
    }
    

    private void makeJavaDoc(Path baseDir, Path buildDir, TransformerFactory transformerFactory) throws MojoExecutionException {
        List<Path> sources = project.getCompileSourceRoots().stream()
                .map(baseDir::resolve)
                .flatMap(this::findSources)
                .toList();
        
        DocumentationTool documentationTool = ToolProvider.getSystemDocumentationTool();
        DiagnosticListener<? super JavaFileObject> diagnosticListener = new JavaDocProcessListener();
        
        StandardJavaFileManager fileManager = documentationTool
                .getStandardFileManager(diagnosticListener, null, UTF_8);
        
        StringWriter sw = new StringWriter();
        try {
            documentationTool.getTask(sw, fileManager, diagnosticListener, XmlDoclet.class, 
                List.of("-protected", "--show-members", "protected", "-encoding", "UTF-8", "-d", buildDir.toString()),
                fileManager.getJavaFileObjectsFromPaths(sources)).call();
        } finally {
            LOG.debug("JavaDoc generation output:\n\n{}", sw.toString());
        }
        
        
        try (InputStream xslt = getClass().getResourceAsStream("/docbook/xsl/javadoc2docbook.xsl")){
            StreamSource source = new StreamSource(xslt);
            source.setSystemId(buildDir.resolve("javadoc2docbook.xsl").toFile());
            Transformer transformer = transformerFactory.newTransformer(source);
            
            transformer.setParameter("destdir", buildDir.toString());
            transformer.setParameter("ddf.only", 0);

            Path javadocXml = buildDir.resolve("javadoc.xml");
            Path javadocTxt = buildDir.resolve("javadoc.txt");
            
            try (Reader input = Files.newBufferedReader(javadocXml);
                    Writer output = Files.newBufferedWriter(javadocTxt)) {
                StreamResult outputTarget = new StreamResult(output);
                outputTarget.setSystemId(javadocTxt.toFile());
                transformer.transform(new StreamSource(input), outputTarget);
            }
            
            transformer.setParameter("ddf.only", 1);

            Path ddfTxt = buildDir.resolve("ddf.txt");
            try (Reader input = Files.newBufferedReader(javadocXml);
                    Writer output = Files.newBufferedWriter(ddfTxt)) {
                StreamResult outputTarget = new StreamResult(output);
                outputTarget.setSystemId(ddfTxt.toFile());
                transformer.transform(new StreamSource(input), outputTarget);
            }
        } catch (IOException | TransformerException e) {
            throw new MojoExecutionException("Failed to make JavaDoc docbook files", e);
        }
    }
    
    public Stream<Path> findSources(Path p) {
        try {
            if (Files.isDirectory(p)) {
                return StreamSupport.stream(
                        Files.newDirectoryStream(p).spliterator(), false)
                        .flatMap(this::findSources);
            } else if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".java")) {
                return Stream.of(p);
            }
        } catch (IOException e) {
            LOG.error("Failed listing sources for path {}", p, e);
            throw new RuntimeException("Failed to list sources", e);
        }
        return Stream.empty();
    }
    
    private void setupBook(Path baseDir, Path buildDir, TransformerFactory transformerFactory) {
        Path source = baseDir.resolve(chapterSource);
        
    }
    
}