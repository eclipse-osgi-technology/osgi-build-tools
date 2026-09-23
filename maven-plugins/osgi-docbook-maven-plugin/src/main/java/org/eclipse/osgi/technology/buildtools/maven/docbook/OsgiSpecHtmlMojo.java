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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.saxon.lib.FeatureKeys;

/**
 * Generates the chunked XHTML form of an OSGi specification chapter, with the
 * same look as the specification pages on docs.osgi.org.
 */
@Mojo(name = "html", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyResolution = ResolutionScope.COMPILE)
public class OsgiSpecHtmlMojo extends AbstractOsgiSpecMojo {

    private static final String INTERNAL_HTML = "internal:/html";
    private static final String INTERNAL_HTML_IMAGES = "internal:/html-images";

    /** The bundled index of chapters and packages published on docs.osgi.org */
    private static final String LINK_INDEX = "../links/osgi-spec-index.xml";

    /** The static files that every page references, relative to /docbook/xsl/html-resources/ */
    private static final List<String> STATIC_RESOURCES = List.of(
            "css/custom.css", "css/github.css",
            "js/main.js", "js/highlight.pack.js",
            "images/OSGi.svg", "images/favicon.png");

    private static final Logger LOG = LoggerFactory.getLogger(OsgiSpecHtmlMojo.class);

    /** Skip the HTML generation */
    @Parameter(property = "osgi.docbook.html.skip", defaultValue = "false")
    boolean skip;

    /** Attach a zip of the HTML pages to the project with the classifier {@code specification-html} */
    @Parameter(property = "osgi.docbook.html.attach", defaultValue = "true")
    boolean attachZip;

    /**
     * Link references to chapters and packages that are not part of this
     * specification to their published pages. When false they are rendered as
     * plain text.
     */
    @Parameter(property = "osgi.docbook.html.externalLinks", defaultValue = "true")
    boolean externalLinks;

    /** The URL that the paths in the external link index are relative to */
    @Parameter(property = "osgi.docbook.html.externalLinkBaseUrl", defaultValue = "https://docs.osgi.org/specification/")
    String externalLinkBaseUrl;

    /** An external link index to use instead of the bundled one */
    @Parameter(property = "osgi.docbook.html.externalLinkIndex")
    File externalLinkIndex;

    @Override
    String formatName() {
        return "html";
    }

    @Override
    boolean isSkipped() {
        return skip;
    }

    @Override
    void generate(SpecBook book, Path htmlDir) throws MojoExecutionException {
        // Stale chunks from a renamed chapter must not end up in the zip
        clean(htmlDir);

        transform(book, htmlDir);
        LOG.info("Specification HTML generation and transformation complete");

        copyImages(book, htmlDir);
        copyStaticResources(htmlDir);

        if (attachZip) {
            Path zip = baseDir.resolve(project.getBuild().getDirectory())
                    .resolve(outputFileBaseName + "-html.zip");
            zip(htmlDir, zip);
            helper.attachArtifact(project, "zip", "specification-html", zip.toFile());
            LOG.info("Specification HTML zip {} attached", zip.getFileName());
        }
    }

    private void transform(SpecBook book, Path htmlDir) throws MojoExecutionException {
        try {
            ClasspathStylesheetResolver resolver = new ClasspathStylesheetResolver(INTERNAL_HTML, "custom-html.xsl");
            TransformerFactory transformerFactory = newTransformerFactory();
            transformerFactory.setURIResolver(resolver);
            transformerFactory.setFeature(FeatureKeys.XINCLUDE, true);
            Transformer transformer = transformerFactory.newTransformer(resolver.stylesheet());
            LOG.debug("Loaded OSGi specification docbook to HTML transformer");

            // The chunks are written relative to the main output, which only
            // carries the chunker's messages
            Path mainOutput = htmlDir.resolveSibling("html.txt");
            transformer.setParameter("webhelp.base.dir", htmlDir.getFileName().toString());
            transformer.setParameter("webhelp.default.topic", "index.html");
            transformer.setParameter("release.version", project.getVersion());
            transformer.setParameter("chunker.output.encoding", "UTF-8");
            if (externalLinks) {
                transformer.setParameter("external.links.index", externalLinkIndex != null
                        ? externalLinkIndex.toPath().toAbsolutePath().toUri().toString() : LINK_INDEX);
                transformer.setParameter("external.links.base", externalLinkBaseUrl);
            }

            StreamResult result = new StreamResult(mainOutput.toFile());
            result.setSystemId(mainOutput.toUri().toString());
            transformer.transform(book.source(resolver), result);
        } catch (TransformerException e) {
            LOG.error("Failed to create the OSGi specification HTML", e);
            throw new MojoExecutionException("Failed to create the OSGi specification HTML", e);
        }
    }

    private void copyImages(SpecBook book, Path htmlDir) throws MojoExecutionException {
        StringWriter sw = new StringWriter();
        try {
            ClasspathStylesheetResolver resolver = new ClasspathStylesheetResolver(
                    INTERNAL_HTML_IMAGES, "custom-html-images.xsl");
            TransformerFactory transformerFactory = newTransformerFactory();
            transformerFactory.setURIResolver(resolver);
            transformerFactory.setFeature(FeatureKeys.XINCLUDE, true);
            transformerFactory.newTransformer(resolver.stylesheet())
                    .transform(book.source(resolver), new StreamResult(sw));
        } catch (TransformerException e) {
            LOG.error("Failed to list the OSGi specification images", e);
            throw new MojoExecutionException("Failed to list the OSGi specification images", e);
        }

        Path imagesDir = htmlDir.resolve("images");
        // The pages refer to images by file name only, see custom-html.xsl
        Map<String, String> copied = new HashMap<>();
        for (String line : sw.toString().split("\n")) {
            String uri = line.strip();
            if (uri.isEmpty()) {
                continue;
            }
            String name = uri.substring(uri.lastIndexOf('/') + 1);
            String previous = copied.putIfAbsent(name, uri);
            if (previous != null) {
                if (!previous.equals(uri)) {
                    LOG.warn("Image {} has the same file name as {} and will not be used", uri, previous);
                }
                continue;
            }
            try (InputStream is = URI.create(uri).toURL().openStream()) {
                copy(is, imagesDir.resolve(name));
            } catch (IOException | IllegalArgumentException e) {
                LOG.error("Failed to copy image {}", uri, e);
                throw new MojoExecutionException("Failed to copy image " + uri, e);
            }
        }
        LOG.debug("Copied {} images", copied.size());
    }

    private void copyStaticResources(Path htmlDir) throws MojoExecutionException {
        for (String resource : STATIC_RESOURCES) {
            copyResource("/docbook/xsl/html-resources/" + resource, htmlDir.resolve(resource));
        }
        // Matches the draft.watermark.image default in custom-html.xsl
        copyResource("/docbook/graphics/draft.svg", htmlDir.resolve("images/draft.svg"));
    }

    private void copyResource(String resource, Path target) throws MojoExecutionException {
        URL url = getClass().getResource(resource);
        if (url == null) {
            throw new MojoExecutionException("Missing plugin resource " + resource);
        }
        try (InputStream is = url.openStream()) {
            copy(is, target);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy " + resource, e);
        }
    }

    private static void copy(InputStream is, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(is, target, REPLACE_EXISTING);
    }

    private static void clean(Path dir) throws MojoExecutionException {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!p.equals(dir)) {
                    Files.delete(p);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to clean " + dir, e);
        }
    }

    private static void zip(Path dir, Path zip) throws MojoExecutionException {
        try (Stream<Path> paths = Files.walk(dir);
                OutputStream os = new BufferedOutputStream(Files.newOutputStream(zip));
                ZipOutputStream zos = new ZipOutputStream(os)) {
            // Sorted so that the archive layout does not depend on the file system
            for (Path p : paths.filter(Files::isRegularFile).sorted().toList()) {
                ZipEntry entry = new ZipEntry(dir.relativize(p).toString().replace('\\', '/'));
                entry.setLastModifiedTime(Files.getLastModifiedTime(p));
                zos.putNextEntry(entry);
                Files.copy(p, zos);
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create " + zip, e);
        }
    }
}
