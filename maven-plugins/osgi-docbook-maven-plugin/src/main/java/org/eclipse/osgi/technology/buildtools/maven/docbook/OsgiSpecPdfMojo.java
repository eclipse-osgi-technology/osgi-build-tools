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

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.stream.Stream;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopConfParser;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.xmlgraphics.io.Resource;
import org.apache.xmlgraphics.io.ResourceResolver;
import org.osgi.tools.fop.FOPMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.saxon.lib.FeatureKeys;

/**
 * Generates the PDF form of an OSGi specification chapter.
 */
@Mojo(name = "pdf", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyResolution = ResolutionScope.COMPILE)
public class OsgiSpecPdfMojo extends AbstractOsgiSpecMojo {

    private static final String INTERNAL_FO = "internal:/fo";

    private static final Logger LOG = LoggerFactory.getLogger(OsgiSpecPdfMojo.class);

    @Override
    String formatName() {
        return "pdf";
    }

    @Override
    void generate(SpecBook book, Path buildDir) throws MojoExecutionException {
        Path foFile = createFOFile(book, buildDir);
        LOG.info("Specification FO generation and transformation complete");

        Path pdf = generatePDF(foFile, buildDir, book.bookDir(), newTransformerFactory());
        LOG.info("Specification PDF generation and transformation complete");

        // We attach the FO as it can be used to make a diff in the future
        helper.attachArtifact(project, "fo", "specification", foFile.toFile());
        // We attach the PDF as it is our primary output
        helper.attachArtifact(project, "pdf", "specification", pdf.toFile());
    }

    /**
     * Generate a merged FO file ready for final conversion
     * @param book
     * @param buildDir
     * @return
     * @throws MojoExecutionException
     */
    private Path createFOFile(SpecBook book, Path buildDir) throws MojoExecutionException {
        try {
            ClasspathStylesheetResolver resolver = new ClasspathStylesheetResolver(INTERNAL_FO, "custom-fo.xsl");
            TransformerFactory transformerFactory = newTransformerFactory();
            transformerFactory.setURIResolver(resolver);
            // We want the FO file to be complete
            transformerFactory.setFeature(FeatureKeys.XINCLUDE, true);
            Transformer transformer = transformerFactory.newTransformer(resolver.stylesheet());
            transformer.setParameter("draft.mode", draftMode);
            // custom-fo.xsl asks for indentation, which Saxon also applies to
            // mixed content: it puts spaces around links and inline elements
            transformer.setOutputProperty(OutputKeys.INDENT, "no");

            LOG.debug("Loaded OSGi specification docbook to FO transformer");

            Path outputPath = buildDir.resolve(outputFileBaseName + ".fo");
            try (Writer output = Files.newBufferedWriter(outputPath, CREATE, TRUNCATE_EXISTING)) {
                transformer.transform(book.profiled(draftMode, copyrightYear), new StreamResult(output));
                LOG.debug("Created OSGi specification FO file");
            }
            return outputPath;
        } catch (Exception e) {
            LOG.error("Failed to create OSGi specification FO file", e);
            throw new MojoExecutionException("Failed to create OSGi specification FO file", e);
        }
    }

    private Path generatePDF(Path foFile, Path buildDir, Path bookDir, TransformerFactory transformerFactory)
        throws MojoExecutionException {
        FopFactory fopFactory;
        try {
            // FOP finds the fonts by their family names in this directory, and
            // the SVG images use them through Java's font registry
            Path fontDir = extractFonts(buildDir.resolve("fonts"));
            FOPMain.registerFonts(fontDir.toString());
            try(InputStream conf = getClass().getResourceAsStream("/fop/fop-osgi.xconf")) {
                String xconf = new String(conf.readAllBytes(), StandardCharsets.UTF_8)
                        .replace("@font.base.dir@", fontDir.toString());
                InputStream is = new ByteArrayInputStream(xconf.getBytes(StandardCharsets.UTF_8));
                FopConfParser parser = new FopConfParser(is, buildDir.toUri(), pdfResourceResolver(buildDir, bookDir));
                parser.getFopFactoryBuilder().getImageManager();
                fopFactory = parser.getFopFactoryBuilder().build();
            }
            LOG.debug("Created the FOP processor");

            Path outputPath = buildDir.resolve(outputFileBaseName + ".pdf");
            try (OutputStream pdfOutput = new BufferedOutputStream(
                    Files.newOutputStream(outputPath, CREATE, TRUNCATE_EXISTING))) {
                FOUserAgent userAgent = fopFactory.newFOUserAgent();
                // the books are English, hyphenated as FOP 1.1 did
                WordHyphenation.install(userAgent, "en");
                Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, userAgent, pdfOutput);
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

    /**
     * Copies the bundled fonts into a directory
     *
     * @param fontDir the directory
     * @return the directory
     */
    static Path extractFonts(Path fontDir) throws IOException, URISyntaxException {
        URL url = OsgiSpecPdfMojo.class.getResource("/fonts/ARIALUNI.ttf");
        if (url == null) {
            throw new IOException("No bundled fonts");
        }
        URI uri = url.toURI();
        Files.createDirectories(fontDir);
        if ("jar".equals(uri.getScheme())) {
            FileSystem fs;
            try {
                fs = FileSystems.newFileSystem(uri, Map.of());
            } catch (FileSystemAlreadyExistsException e) {
                fs = FileSystems.getFileSystem(uri);
            }
            copyFonts(fs.getPath("/fonts"), fontDir);
        } else {
            copyFonts(Path.of(uri).getParent(), fontDir);
        }
        return fontDir;
    }

    private static void copyFonts(Path from, Path to) throws IOException {
        try (Stream<Path> fonts = Files.list(from)) {
            for (Path f : fonts.toList()) {
                Path target = to.resolve(f.getFileName().toString());
                if (!Files.exists(target)) {
                    Files.copy(f, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private ResourceResolver pdfResourceResolver(Path buildDir, Path bookDir) {
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
                    } else if(bookDir != null && relative.startsWith("@book.base.url@/")
                            && Files.isRegularFile(bookDir.resolve(relative.substring(16)))) {
                        LOG.debug("URI is relative to the book, e.g. an image of a chapter");
                        is = Files.newInputStream(bookDir.resolve(relative.substring(16)));
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
