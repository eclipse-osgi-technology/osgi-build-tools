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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.inject.Inject;
import javax.tools.DiagnosticListener;
import javax.tools.DocumentationTool;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopConfParser;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.xmlgraphics.io.Resource;
import org.apache.xmlgraphics.io.ResourceResolver;
import org.osgi.tools.xmldoclet.XmlDoclet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.EntityResolver2;

import net.sf.saxon.TransformerFactoryImpl;
import net.sf.saxon.lib.FeatureKeys;

@Mojo(name = "pdf", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyResolution = ResolutionScope.COMPILE)
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

    @Inject
    MavenProjectHelper helper;
    
    private String outputFileBaseName;
    
    public void execute() throws MojoExecutionException, MojoFailureException {
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
        Path baseDir = project.getBasedir().toPath().toAbsolutePath();
        Path buildDir = baseDir.resolve(project.getBuild().getDirectory()).resolve("spec/pdf");
        try {
            // The javadoc step normally creates this, but it may be skipped
            Files.createDirectories(buildDir);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create " + buildDir, e);
        }

        // Set up SAXON transformer
        TransformerFactory transformerFactory = TransformerFactory.newInstance(
                TransformerFactoryImpl.class.getName(), TransformerFactoryImpl.class.getClassLoader());
        try {
            transformerFactory.setFeature(FeatureKeys.IGNORE_SAX_SOURCE_PARSER, false);
        } catch (TransformerConfigurationException e) {
            throw new MojoExecutionException(e);
        }
        
        makeJavaDoc(baseDir, buildDir, transformerFactory);
        LOG.info("JavaDoc generation and transformation complete");
        
        String bookXML = setupBook(baseDir, buildDir, transformerFactory);
        LOG.info("Virtual book generation and transformation complete");
        
        Path foFile = createFOFile(bookXML, baseDir, buildDir, transformerFactory);
        LOG.info("Specification FO generation and transformation complete");
        
        Path pdf = generatePDF(foFile, buildDir, transformerFactory);
        LOG.info("Specification PDF generation and transformation complete");
        
        // We attach the FO as it can be used to make a diff in the future
        helper.attachArtifact(project, "fo", "specification", foFile.toFile());
        // We attach the PDF as it is our primary output
        helper.attachArtifact(project, "pdf", "specification", pdf.toFile());
    }


    /**
     * Generate JavaDoc for the specification packages and convert it into docbook 
     * @param baseDir
     * @param buildDir
     * @param transformerFactory
     * @throws MojoExecutionException
     */
    private void makeJavaDoc(Path baseDir, Path buildDir, TransformerFactory transformerFactory) throws MojoExecutionException {
        Path javadocDir = buildDir.resolve("javadoc");
        
        // Find the sources to document
        List<Path> sources = project.getCompileSourceRoots().stream()
                .map(baseDir::resolve)
                .flatMap(this::findSources)
                .toList();

        if (sources.isEmpty()) {
            // The javadoc tool fails when handed no sources
            LOG.info("No sources to document, skipping the JavaDoc step");
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
            List<String> classpath = new java.util.ArrayList<>();
            try {
                classpath.addAll(project.getCompileClasspathElements());
            } catch (DependencyResolutionRequiredException e) {
                throw new MojoExecutionException("Failed to resolve compile classpath", e);
            }
            String inherited = System.getProperty("java.class.path");
            if (inherited != null && !inherited.isBlank()) {
                classpath.add(inherited);
            }
            List<String> options = new java.util.ArrayList<>(List.of(
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

            Path ddfTxt = javadocDir.resolve("ddf.txt");
            try (Reader input = Files.newBufferedReader(javadocXml);
                    Writer output = Files.newBufferedWriter(ddfTxt, CREATE, TRUNCATE_EXISTING)) {
                StreamResult outputTarget = new StreamResult(output);
                outputTarget.setSystemId(ddfTxt.toUri().toString());
                transformer.transform(new StreamSource(input), outputTarget);
                LOG.debug("JavaDoc DDF generation complete");
            }
        } catch (IOException | TransformerException e) {
            LOG.error("Failed to generate docbook flavoured JavaDoc", e);
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
            transformer.setParameter("version", project.getVersion());
            try (InputStream input = getClass().getResourceAsStream("/docbook/book/book.xml")) {
                transformer.transform(new StreamSource(input), new StreamResult(sw));
            }
            if(LOG.isDebugEnabled()) {
                LOG.debug("Book XML is:\n\n{}", sw.toString());
            }
            return sw.toString();
        } catch (IOException | TransformerException e) {
            LOG.error("Failed to make virtual book", e);
            throw new MojoExecutionException("Failed to setup virtual specification book", e);
        }
    }

    /**
     * Generate a merged FO file ready for final conversion
     * @param bookXML
     * @param buildDir
     * @param transformerFactory
     * @return
     * @throws MojoExecutionException 
     */
    private Path createFOFile(String bookXML, Path baseDir, Path buildDir,
            TransformerFactory transformerFactory) throws MojoExecutionException {
        try (InputStream xslt = getClass().getResourceAsStream("/docbook/xsl/custom-fo.xsl")){
            transformerFactory.setURIResolver(this::resolveFOStyleSheetURI);
            // We want the FO file to be complete
            transformerFactory.setFeature(FeatureKeys.XINCLUDE, true);
            StreamSource xsltSource = new StreamSource(xslt);
            xsltSource.setSystemId(INTERNAL_FO);
            Transformer transformer = transformerFactory.newTransformer(xsltSource);
            
            LOG.debug("Loaded OSGi specification docbook to FO transformer");
            
            // We need to control the low-level resolution of entities in xinclude
            SAXParserFactory parserFactory = SAXParserFactory.newNSInstance();
            parserFactory.setNamespaceAware(true);
            parserFactory.setXIncludeAware(true);
          
            SAXParser parser = parserFactory.newSAXParser();
            XMLReader xmlReader = parser.getXMLReader();
            xmlReader.setEntityResolver(resolveBookXMLURI(baseDir, buildDir));
            
            Path outputPath = buildDir.resolve(outputFileBaseName + ".fo");
            try (Writer output = Files.newBufferedWriter(outputPath, CREATE, TRUNCATE_EXISTING)) {
                SAXSource bookSource = new SAXSource(xmlReader, new InputSource(new StringReader(bookXML)));
                bookSource.setSystemId(INTERNAL_BOOK);
                bookSource.setXMLReader(xmlReader);
                transformer.transform(bookSource, new StreamResult(output));
                LOG.debug("Created OSGi specification FO file");
            }
            return outputPath;
        } catch (Exception e) {
            LOG.error("Failed to create OSGi specification FO file", e);
            throw new MojoExecutionException("Failed to create OSGi specification FO file", e);
        } finally {
            // We want to reset the resolver, but a SAXON bug prevents it
            // transformerFactory.setURIResolver(null);
        }
    }
    
    private Source resolveFOStyleSheetURI(String href, String base) throws TransformerException {
        try {
            if(INTERNAL_FO.equals(base)) {
                LOG.debug("Resolving include {} for FO style sheet", href);
                // This is the FO stylesheet
                URL url;
                String systemId = null;
                if("".equals(href)) {
                    url = getClass().getResource("/docbook/xsl/custom-fo.xsl");
                    systemId = INTERNAL_FO;
                } else {
                    // normalize ".." segments — Class.getResource rejects them
                    url = getClass().getResource(
                        URI.create("/docbook/xsl/" + href).normalize().toString());
                }
                if(url == null) {
                    LOG.error("No file {} with base {}", href, base);
                    throw new TransformerException("Failed to find file");
                } else {
                    StreamSource source = new StreamSource(url.openStream());
                    source.setSystemId(systemId != null ? systemId : url.toURI().toASCIIString());
                    return source;
                }
            } else if (base != null && base.startsWith("jar:") && !href.contains(":")) {
                // jar: URIs are opaque — resolve the entry path textually
                LOG.debug("Resolving include {} inside jar {}", href, base);
                int sep = base.indexOf("!/");
                String entry = base.substring(sep + 1);
                String resolved = URI.create(entry).resolve(href).normalize().toString();
                URL url = getClass().getResource(resolved);
                if (url == null) {
                    LOG.error("No jar resource {} resolved from {} + {}", resolved, base, href);
                    throw new TransformerException("Failed to find file");
                }
                StreamSource source = new StreamSource(url.openStream());
                source.setSystemId(url.toURI().toASCIIString());
                return source;
            } else {
                LOG.debug("Resolving include {} for file {}", href, base);
                URI uri = URI.create(base).resolve(href);
                StreamSource source = new StreamSource(uri.toURL().openStream());
                source.setSystemId(uri.toASCIIString());
                return source;
            }
        } catch (TransformerException te) {
            throw te;
        } catch (Exception e) {
            LOG.error("Failed to resolve the URI", e);
            throw new TransformerException("Failed to resolve the URI", e);
        }
    }

    private EntityResolver resolveBookXMLURI(Path baseDir, Path buildDir) throws TransformerException {
        Path specFile = baseDir.resolve(chapterSource);
        return new EntityResolver2() {
            
            @Override
            public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
                throw new SAXException("Should be calling the SAX 2 methods");
            }
            
            @Override
            public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId)
                    throws SAXException, IOException {
                try {
                    if(INTERNAL_BOOK.equals(baseURI)) {
                        // This is the Book XML
                        LOG.debug("Resolving include of {} from the virtual book", systemId);
                        if("spec.xml".equals(systemId)) {
                            InputSource is = new InputSource(Files.newBufferedReader(specFile));
                            is.setSystemId(specFile.toUri().toASCIIString());
                            return is;
                        } else {
                            URL url = getClass().getResource("/docbook/book/" + systemId);
                            if(url == null) {
                                LOG.error("No file {} with base {}", systemId, baseURI);
                                throw new TransformerException("Failed to find file");
                            } else {
                                InputSource source = new InputSource(url.openStream());
                                source.setSystemId(url.toURI().toASCIIString());
                                return source;
                            }
                        }
                    } else if(INTERNAL_FO.equals(baseURI)) {
                        LOG.debug("Resolving include of {} from the style sheet", systemId);
                        return SAXSource.sourceToInputSource(resolveFOStyleSheetURI(systemId, baseURI));
                    } else if(specFile.toFile().toURI().equals(URI.create(baseURI))) {
                        // This is the spec XML
                        LOG.debug("Resolving include of {} in the spec file", systemId);
                        // Includes are relative to the spec file's directory
                        Path p = specFile.getParent().resolve(systemId).normalize();
                        if(Files.exists(p)) {
                            LOG.debug("File found relative to the spec source {}", p);
                            InputSource is = new InputSource(Files.newBufferedReader(p));
                            is.setSystemId(p.toUri().toASCIIString());
                            return is;
                        } else {
                            p = buildDir.resolve(systemId);
                            if(Files.exists(p)) {
                                LOG.debug("File found relative to the build directory {}", p);
                                InputSource is = new InputSource(Files.newBufferedReader(p));
                                is.setSystemId(p.toUri().toASCIIString());
                                return is;
                            } else {
                                // Legacy include path for generated javadoc:
                                // .../generated/javadoc/docbook/<pkg>.xml
                                java.util.regex.Matcher m = java.util.regex.Pattern
                                        .compile(".*generated/javadoc/docbook/(.+)")
                                        .matcher(systemId);
                                if (m.matches()) {
                                    p = buildDir.resolve("javadoc").resolve(m.group(1));
                                }
                                if (Files.exists(p)) {
                                    LOG.debug("File found via legacy javadoc mapping {}", p);
                                    InputSource is = new InputSource(Files.newBufferedReader(p));
                                    is.setSystemId(p.toUri().toASCIIString());
                                    return is;
                                }
                                LOG.error("File {} included by {} not found", systemId, specFile);
                                throw new TransformerException("Unknown file " + systemId);
                            }
                        }
                    } else {
                        LOG.debug("Resolving include of {} in file {}", systemId, baseURI);
                        URI uri = URI.create(baseURI).resolve(systemId);
                        InputSource source = new InputSource(uri.toURL().openStream());
                        source.setSystemId(uri.toASCIIString());
                        return source;
                    }
                } catch (Exception e) {
                    LOG.error("Failed to resolve the URI", e);
                    throw new SAXException("Failed to resolve the URI", e);
                }
            }
            
            @Override
            public InputSource getExternalSubset(String name, String baseURI) throws SAXException, IOException {
                LOG.debug("External subsets not supported");
                return null;
            }
        };
    }

    private Path generatePDF(Path foFile, Path buildDir, TransformerFactory transformerFactory) 
        throws MojoExecutionException {
        FopFactory fopFactory;
        try {
            try(InputStream is = getClass().getResourceAsStream("/fop/fop-osgi.xconf")) {
                FopConfParser parser = new FopConfParser(is, buildDir.toUri(), pdfResourceResolver(buildDir));
                parser.getFopFactoryBuilder().getImageManager();
                fopFactory = parser.getFopFactoryBuilder().build();
            }
            LOG.debug("Created the FOP processor");
    
            Path outputPath = buildDir.resolve(outputFileBaseName + ".pdf");
            try (OutputStream pdfOutput = new BufferedOutputStream( 
                    Files.newOutputStream(outputPath, CREATE, TRUNCATE_EXISTING))) {
                Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, pdfOutput);
                Transformer transformer = transformerFactory.newTransformer();
                
                Source xmlSource = new StreamSource(foFile.toFile());
                transformer.transform(xmlSource, new SAXResult(fop.getDefaultHandler()));
                LOG.debug("Generated the PDF");
            }
            return outputPath;
        } catch (Exception e) {
            LOG.error("Failed to create the PDF", e);
            throw new MojoExecutionException("Failed to create PDF", e);
        }
    }

    private ResourceResolver pdfResourceResolver(Path buildDir) {
        return new ResourceResolver() {
            @Override
            public Resource getResource(URI uri) throws IOException {
                LOG.debug("Resolving PDF file {}", uri);
                Path requiredPath = Paths.get(uri);
                if(Files.exists(requiredPath)) {
                    LOG.debug("File {} exists. Using it directly", uri);
                    return new Resource(Files.newInputStream(requiredPath));
                } else {
                    String relative = buildDir.relativize(requiredPath).toString().replace('\\', '/');
                    InputStream is; 
                    if(relative.startsWith("@font.base.url@/")) {
                        LOG.debug("URI is a font URI, searching local fonts");
                        is = getClass().getResourceAsStream("/fonts" + relative.substring(15));
                    } else if(relative.startsWith("@book.base.url@/")) {
                        LOG.debug("URI is a book base URI, searching local book fonts");
                        is = getClass().getResourceAsStream("/fop/hyph" + relative.substring(15));
                    } else {
                        LOG.debug("URI is not known. Checking locally");
                        is = getClass().getResourceAsStream("/docbook/" + relative);
                    }
                    
                    if(is != null) {
                        LOG.debug("Found resource for URI {}", uri);
                        return new Resource(is);
                    }
                    LOG.error("Could not find relative {}", relative);
                }
                LOG.error("Could not find URI {}", uri);
                return null;
            }
            
            @Override
            public OutputStream getOutputStream(URI uri) throws IOException {
                LOG.error("Could not find outputstream {}", uri);
                return null;
            }
        };
        
    }
}