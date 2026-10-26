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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.maven.plugin.MojoExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.EntityResolver2;

/**
 * The book to render: either the virtual book that wraps a single
 * specification chapter, with the XInclude resolution that pulls in the
 * chapter, the bundled front matter and the generated javadoc, or a complete
 * book file whose includes are plain relative paths.
 */
final class SpecBook {

    static final String INTERNAL_BOOK = "internal:/book";

    private static final String INTERNAL_PROFILE = "internal:/profile";

    private static final Logger LOG = LoggerFactory.getLogger(SpecBook.class);

    static final Pattern LEGACY_JAVADOC = Pattern.compile(".*generated/javadoc/docbook/(.+)");

    /**
     * Legacy include path for schema listings, e.g.
     * .../generated/xmlns/scr/v1.5.0/scr.xsd or
     * .../generated/json.schema/featurelauncher/v1.0.0/bundle-start-levels.json,
     * and for the tree summary of the Residential Device Management Tree,
     * .../generated/residentialdmt/treesummary.xml. The monorepo build copied
     * or generated these into generated/; in a specification repository they
     * live under xmlns/, json.schema/ or residentialdmt/ at the repository
     * root, so the mapped path is searched upwards from the spec file's
     * directory.
     */
    static final Pattern LEGACY_RESOURCE = Pattern
            .compile(".*generated/((?:xmlns|json\\.schema|residentialdmt)/.+)");

    /** Everything before the schema element of an XSD: prolog and licence comment */
    private static final Pattern SCHEMA_PROLOG = Pattern.compile("(?s).*(<(\\w+:)?schema)");

    /**
     * The text of a schema listing: an XSD without what precedes its schema
     * element, as the monorepo build listed it.
     */
    static String schemaListing(Path p) throws IOException {
        String text = Files.readString(p);
        return p.getFileName().toString().endsWith(".xsd") ? SCHEMA_PROLOG.matcher(text).replaceFirst("$1") : text;
    }

    private final Path specFile;
    private final Path specDir;
    private final String bookXML;
    /** A complete book file, or null for the virtual book */
    private final Path bookFile;

    private SpecBook(Path specFile, Path specDir, String bookXML, Path bookFile) {
        this.specFile = specFile;
        this.specDir = specDir;
        this.bookXML = bookXML;
        this.bookFile = bookFile;
    }

    /**
     * A complete book: its includes are resolved relative to the book file,
     * as for any DocBook document.
     *
     * @param bookFile the book file
     * @return the book
     */
    static SpecBook book(Path bookFile) {
        return new SpecBook(null, null, null, bookFile);
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
        String bookXML = wrapper(title, version, List.of(), transformerFactory);
        if(LOG.isDebugEnabled()) {
            LOG.debug("Book XML is:\n\n{}", bookXML);
        }
        LOG.info("Virtual book generation and transformation complete");
        return new SpecBook(specFile, specDir, bookXML, null);
    }

    /**
     * The book that wraps specification chapters: the bundled front matter,
     * {@code preface/} and {@code 001/introduction.xml}, and the chapters.
     *
     * @param title the book title
     * @param version the book version
     * @param chapters the paths of the chapters to include, or none for the
     *            single chapter {@code spec.xml}
     * @param transformerFactory
     * @return the book XML
     * @throws MojoExecutionException
     */
    static String wrapper(String title, String version, List<String> chapters,
            TransformerFactory transformerFactory) throws MojoExecutionException {
        StringWriter sw = new StringWriter();
        try (InputStream xslt = SpecBook.class.getResourceAsStream("/docbook/book/book-setup.xsl")){
            Transformer transformer = transformerFactory.newTransformer(new StreamSource(xslt));
            transformer.setParameter("title", title);
            transformer.setParameter("version", version);
            transformer.setParameter("chapters", String.join(" ", chapters));
            try (InputStream input = SpecBook.class.getResourceAsStream("/docbook/book/book.xml")) {
                transformer.transform(new StreamSource(input), new StreamResult(sw));
            }
        } catch (IOException | TransformerException e) {
            LOG.error("Failed to make virtual book", e);
            throw new MojoExecutionException("Failed to setup virtual specification book", e);
        }
        return sw.toString();
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
            if (bookFile != null) {
                xmlReader.setEntityResolver(bookResolver(stylesheetResolver));
                return new SAXSource(xmlReader, new InputSource(bookFile.toUri().toASCIIString()));
            }
            xmlReader.setEntityResolver(resolver(stylesheetResolver));

            SAXSource bookSource = new SAXSource(xmlReader, new InputSource(new StringReader(bookXML)));
            bookSource.setSystemId(INTERNAL_BOOK);
            return bookSource;
        } catch (ParserConfigurationException | SAXException e) {
            LOG.error("Failed to create the book parser", e);
            throw new MojoExecutionException("Failed to create the book parser", e);
        }
    }

    /**
     * @return the directory of a complete book, which relative image paths
     *         are resolved against, or null for the virtual book
     */
    Path bookDir() {
        return bookFile != null ? bookFile.getParent() : null;
    }

    /**
     * The book with all includes resolved and profiled as the monorepo build
     * did: without comments and the content of the other status (draft or
     * final), and with the copyright year.
     *
     * @param draftMode {@code yes} or {@code no}
     * @param copyrightYear the years of the copyright line, or null
     * @return the profiled book
     * @throws MojoExecutionException
     */
    DOMSource profiled(String draftMode, String copyrightYear) throws MojoExecutionException {
        try {
            ClasspathStylesheetResolver resolver = new ClasspathStylesheetResolver(INTERNAL_PROFILE,
                    "custom-profile.xsl");
            TransformerFactory transformerFactory = AbstractOsgiSpecMojo.newTransformerFactory();
            transformerFactory.setURIResolver(resolver);
            Transformer transformer = transformerFactory.newTransformer(resolver.stylesheet());
            transformer.setParameter("draft.mode", draftMode);
            if (copyrightYear != null && !copyrightYear.isBlank()) {
                transformer.setParameter("copyright.year", copyrightYear);
            }

            SAXSource source = source(resolver);
            DOMResult result = new DOMResult();
            transformer.transform(source, result);
            String systemId = bookFile != null ? bookFile.toUri().toASCIIString() : INTERNAL_BOOK;
            if (bookFile != null) {
                // XInclude leaves xml:base relative to the including file;
                // make them absolute, so that the stylesheets resolve images
                // to absolute URIs
                Element root = ((Document) result.getNode()).getDocumentElement();
                absoluteBase(root, URI.create(systemId));
            }
            LOG.info("Book profiled with draft.mode {}", draftMode);
            return new DOMSource(result.getNode(), systemId);
        } catch (TransformerException e) {
            LOG.error("Failed to profile the book", e);
            throw new MojoExecutionException("Failed to profile the specification book", e);
        }
    }

    private static void absoluteBase(Element element, URI base) {
        String attr = element.getAttributeNS(XMLConstants.XML_NS_URI, "base");
        if (!attr.isEmpty()) {
            base = base.resolve(attr);
            element.setAttributeNS(XMLConstants.XML_NS_URI, "xml:base", base.toString());
        }
        for (org.w3c.dom.Node n = element.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element child) {
                absoluteBase(child, base);
            }
        }
    }

    private EntityResolver bookResolver(ClasspathStylesheetResolver stylesheetResolver) {
        return new EntityResolver2() {

            @Override
            public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
                return null;
            }

            @Override
            public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId)
                    throws SAXException, IOException {
                if (stylesheetResolver.internalId().equals(baseURI)) {
                    try {
                        return SAXSource.sourceToInputSource(stylesheetResolver.resolve(systemId, baseURI));
                    } catch (TransformerException e) {
                        throw new SAXException("Failed to resolve " + systemId, e);
                    }
                }
                // everything else relative to the including file
                return null;
            }

            @Override
            public InputSource getExternalSubset(String name, String baseURI) throws SAXException, IOException {
                return null;
            }
        };
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
                        // Legacy include path for schema listings: the sources
                        // live under xmlns/ or json.schema/, in a spec repo at
                        // the repository root - search upwards from the spec
                        m = LEGACY_RESOURCE.matcher(systemId);
                        if (m.matches()) {
                            Path dir = specFile.getParent();
                            for (int i = 0; i < 8 && dir != null; i++, dir = dir.getParent()) {
                                p = dir.resolve(m.group(1));
                                if (Files.exists(p)) {
                                    LOG.debug("File found via legacy resource mapping {}", p);
                                    InputSource is = new InputSource(new StringReader(schemaListing(p)));
                                    is.setSystemId(p.toUri().toASCIIString());
                                    return is;
                                }
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
