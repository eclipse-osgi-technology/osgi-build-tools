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
import java.net.URI;
import java.net.URL;
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
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
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

    private static final String INTERNAL_FO = "internal:/fo";
    private static final String INTERNAL_BOOK = "internal:/book";

    private static final Logger LOG = LoggerFactory.getLogger(OsgiSpecPdfMojo.class);
    
    @Parameter(property = "osgi.docbook.spec.source", defaultValue = "src/main/resources/spec/spec.xml")
    String chapterSource;
    
    @Parameter(property = "osgi.docbook.spec.title", defaultValue = "${project.name}")
    String specTitle;
    
    @Inject
    MavenProject project;
    
    public void execute() throws MojoExecutionException {
        Path baseDir = project.getBasedir().toPath().toAbsolutePath();
        Path buildDir = baseDir.resolve(project.getBuild().getDirectory()).resolve("spec/pdf");
        
        TransformerFactory transformerFactory = TransformerFactory.newInstance(
                TransformerFactoryImpl.class.getName(), TransformerFactoryImpl.class.getClassLoader());
        
        makeJavaDoc(baseDir, buildDir, transformerFactory);
        
        LOG.info("JavaDoc generation and transformation complete");
        
        String bookXML = setupBook(baseDir, buildDir, transformerFactory);
        
        Path foFile = createFOFile(bookXML, buildDir, transformerFactory);
    }


    /**
     * Generate JavaDoc for the specification packages and convert it into docbook 
     * @param baseDir
     * @param buildDir
     * @param transformerFactory
     * @throws MojoExecutionException
     */
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

    /**
     * Recursively search for java source files
     * 
     * @param p - a file or directory to check
     * @return A stream of source files
     */
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

    /**
     * Create a minimal book to wrap the specification chapter
     * @param baseDir
     * @param buildDir
     * @param transformerFactory
     * @return
     * @throws MojoExecutionException
     */
    private String setupBook(Path baseDir, Path buildDir, TransformerFactory transformerFactory) throws MojoExecutionException {
        StringWriter sw = new StringWriter();
        try (InputStream xslt = getClass().getResourceAsStream("/docbook/book/book-setup.xsl")){
            Transformer transformer = transformerFactory.newTransformer(new StreamSource(xslt));
            transformer.setParameter("title", specTitle);
            try (InputStream input = getClass().getResourceAsStream("/docbook/book/book.xml")) {
                transformer.transform(new StreamSource(input), new StreamResult(sw));
            }
            return sw.toString();
        } catch (IOException | TransformerException e) {
            throw new MojoExecutionException("Failed to setup specification book", e);
        }
    }

    /**
     * Generate a merged FO file ready for final conversion
     * @param bookXML
     * @param buildDir
     * @param transformerFactory
     * @return
     */
    private Path createFOFile(String bookXML, Path buildDir, TransformerFactory transformerFactory) {
        
        
        try (InputStream xslt = getClass().getResourceAsStream("/docbook/xsl/custom-fo.xsl")){
            transformerFactory.setURIResolver(this::resolveFOStyleSheetURI);
            StreamSource xsltSource = new StreamSource(xslt);
            xsltSource.setSystemId(INTERNAL_FO);
            Transformer transformer = transformerFactory.newTransformer(xsltSource);
            
            transformer.setURIResolver(null)
            
            transformer.setParameter("title", specTitle);
            try (InputStream input = getClass().getResourceAsStream("/docbook/book/book.xml")) {
                transformer.transform(new StreamSource(input), new StreamResult(sw));
            }
            return sw.toString();
        } catch (IOException | TransformerException e) {
            throw new MojoExecutionException("Failed to setup specification book", e);
        }
    }
    
    private Source resolveFOStyleSheetURI(String href, String base) throws TransformerException {
        try {
            if(INTERNAL_FO.equals(base)) {
                // This is the FO stylesheet
                URL url = getClass().getResource("/docbook/xsl/" + href);
                if(url == null) {
                    LOG.error("No file {} with base {}", href, base);
                    throw new TransformerException("Failed to find file");
                } else {
                    StreamSource source = new StreamSource(url.openStream());
                    source.setSystemId(url.toURI().toASCIIString());
                    return source;
                }
            } else {
                URI uri = URI.create(base).resolve(href);
                StreamSource source = new StreamSource(uri.toURL().openStream());
                source.setSystemId(uri.toASCIIString());
                return source;
                
            }
        } catch (TransformerException te) {
            throw te;
        } catch (Exception e) {
            LOG.error("Failed to resolve the URI");
            throw new TransformerException("Failed to resolve the URI", e);
        }
    }

    private URIResolver resolveBookXMLURI(Path baseDir, Path buildDir) throws TransformerException {
        Path specFile = baseDir.resolve(chapterSource);
        return (href, base) -> {
            try {
                if(INTERNAL_BOOK.equals(base)) {
                    // This is the Book XML
                    if("spec.xml".equals(href)) {
                        return new StreamSource(specFile.toFile());
                    } else {
                        URL url = getClass().getResource("/docbook/book/" + href);
                        if(url == null) {
                            LOG.error("No file {} with base {}", href, base);
                            throw new TransformerException("Failed to find file");
                        } else {
                            StreamSource source = new StreamSource(url.openStream());
                            source.setSystemId(url.toURI().toASCIIString());
                            return source;
                        }
                    }
                } else if(specFile.toFile().toURI().equals(URI.create(base))) {
                    // This is the spec XML
                    Path p = specFile.resolve(href);
                    if(Files.exists(p)) {
                        return new StreamSource(p.toFile());
                    } else {
                        p = buildDir.resolve(href);
                        if(Files.exists(p)) {
                            return new StreamSource(p.toFile());
                        } else {
                            throw new TransformerException("Unknonw file");
                        }
                    }
                } else {
                    URI uri = URI.create(base).resolve(href);
                    StreamSource source = new StreamSource(uri.toURL().openStream());
                    source.setSystemId(uri.toASCIIString());
                    return source;
                }
            } catch (TransformerException te) {
                throw te;
            } catch (Exception e) {
                LOG.error("Failed to resolve the URI");
                throw new TransformerException("Failed to resolve the URI", e);
            }
        };
    }
}