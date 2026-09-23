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
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.maven.plugin.MojoExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.EntityResolver2;

/**
 * The virtual book that wraps the specification chapter, and the XInclude
 * resolution that pulls in the chapter, the bundled front matter and the
 * generated javadoc.
 */
final class SpecBook {

    static final String INTERNAL_BOOK = "internal:/book";

    private static final Logger LOG = LoggerFactory.getLogger(SpecBook.class);

    private static final Pattern LEGACY_JAVADOC = Pattern.compile(".*generated/javadoc/docbook/(.+)");

    private final Path specFile;
    private final Path specDir;
    private final String bookXML;

    private SpecBook(Path specFile, Path specDir, String bookXML) {
        this.specFile = specFile;
        this.specDir = specDir;
        this.bookXML = bookXML;
    }

    /**
     * Create a minimal book to wrap the specification chapter
     * @param specFile the specification chapter
     * @param specDir the shared specification build directory
     * @param title the book title
     * @param version the book version
     * @param transformerFactory
     * @return the book
     * @throws MojoExecutionException
     */
    static SpecBook setup(Path specFile, Path specDir, String title, String version,
            TransformerFactory transformerFactory) throws MojoExecutionException {
        StringWriter sw = new StringWriter();
        try (InputStream xslt = SpecBook.class.getResourceAsStream("/docbook/book/book-setup.xsl")){
            Transformer transformer = transformerFactory.newTransformer(new StreamSource(xslt));
            transformer.setParameter("title", title);
            transformer.setParameter("version", version);
            try (InputStream input = SpecBook.class.getResourceAsStream("/docbook/book/book.xml")) {
                transformer.transform(new StreamSource(input), new StreamResult(sw));
            }
            if(LOG.isDebugEnabled()) {
                LOG.debug("Book XML is:\n\n{}", sw.toString());
            }
        } catch (IOException | TransformerException e) {
            LOG.error("Failed to make virtual book", e);
            throw new MojoExecutionException("Failed to setup virtual specification book", e);
        }
        LOG.info("Virtual book generation and transformation complete");
        return new SpecBook(specFile, specDir, sw.toString());
    }

    /**
     * Create a source for the book with every XInclude resolved. Each call
     * returns a new source, so the book can be transformed more than once.
     *
     * @param stylesheetResolver resolves references made from the stylesheet
     * @return the source
     * @throws MojoExecutionException
     */
    SAXSource source(ClasspathStylesheetResolver stylesheetResolver) throws MojoExecutionException {
        try {
            // We need to control the low-level resolution of entities in xinclude
            SAXParserFactory parserFactory = SAXParserFactory.newNSInstance();
            parserFactory.setNamespaceAware(true);
            parserFactory.setXIncludeAware(true);

            SAXParser parser = parserFactory.newSAXParser();
            XMLReader xmlReader = parser.getXMLReader();
            xmlReader.setEntityResolver(resolver(stylesheetResolver));

            SAXSource bookSource = new SAXSource(xmlReader, new InputSource(new StringReader(bookXML)));
            bookSource.setSystemId(INTERNAL_BOOK);
            return bookSource;
        } catch (ParserConfigurationException | SAXException e) {
            LOG.error("Failed to create the book parser", e);
            throw new MojoExecutionException("Failed to create the book parser", e);
        }
    }

    private EntityResolver resolver(ClasspathStylesheetResolver stylesheetResolver) {
        Path javadocDir = specDir.resolve("javadoc");
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
                            return inputSource(specFile);
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
                    } else if(stylesheetResolver.internalId().equals(baseURI)) {
                        LOG.debug("Resolving include of {} from the style sheet", systemId);
                        return SAXSource.sourceToInputSource(stylesheetResolver.resolve(systemId, baseURI));
                    } else if(specFile.toFile().toURI().equals(URI.create(baseURI))) {
                        // This is the spec XML
                        LOG.debug("Resolving include of {} in the spec file", systemId);
                        // Includes are relative to the spec file's directory
                        Path p = specFile.getParent().resolve(systemId).normalize();
                        if(Files.exists(p)) {
                            LOG.debug("File found relative to the spec source {}", p);
                            return inputSource(p);
                        }
                        p = specDir.resolve(systemId);
                        if(Files.exists(p)) {
                            LOG.debug("File found relative to the build directory {}", p);
                            return inputSource(p);
                        }
                        // Legacy include path for generated javadoc:
                        // .../generated/javadoc/docbook/<pkg>.xml
                        Matcher m = LEGACY_JAVADOC.matcher(systemId);
                        if (m.matches()) {
                            p = javadocDir.resolve(m.group(1));
                            if (Files.exists(p)) {
                                LOG.debug("File found via legacy javadoc mapping {}", p);
                                return inputSource(p);
                            }
                        }
                        LOG.error("File {} included by {} not found", systemId, specFile);
                        throw new TransformerException("Unknown file " + systemId);
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

    private static InputSource inputSource(Path p) throws IOException {
        InputSource is = new InputSource(Files.newBufferedReader(p));
        is.setSystemId(p.toUri().toASCIIString());
        return is;
    }
}
