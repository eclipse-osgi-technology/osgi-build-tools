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
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.saxon.TransformerFactoryImpl;
import net.sf.saxon.lib.FeatureKeys;

/**
 * The steps shared by every specification output format: parameter checks,
 * javadoc generation and the virtual book set-up, or, for a complete book, the
 * staging of the book and of the DocBook archives it is assembled from. When
 * the docbook goal ran before in the same build, its archive is rendered: all
 * chapters of the project in one book.
 */
public abstract class AbstractOsgiSpecMojo extends AbstractSpecMojo {

    /** The bundled index of chapters and packages published on docs.osgi.org, relative to the style sheets */
    private static final String LINK_INDEX = "../links/osgi-spec-index.xml";

    private static final Logger LOG = LoggerFactory.getLogger(AbstractOsgiSpecMojo.class);

    /** The title of the specification, used when a single chapter is built; not used for a book */
    @Parameter(property = "osgi.docbook.specTitle", defaultValue = "${project.name}")
    String specTitle;

    /**
     * A complete book instead of a single chapter. The directory of this file
     * holds the front matter and the parts of the book of its own (title page,
     * preface, introduction); the chapters and their javadoc come from the
     * DocBook archives listed in {@code docbooks}. Everything is staged into
     * one directory, so the includes of the book are paths relative to that
     * directory, e.g. {@code chapters/service.cm/service.cm.xml}.
     */
    @Parameter(property = "osgi.docbook.bookSource")
    File bookSource;

    /**
     * The DocBook archives of a complete book, as
     * {@code groupId:artifactId:version}, e.g.
     * {@code org.osgi.spec:org.osgi.service.cm:1.6.1}. Each is resolved from
     * the Maven repositories (type {@code zip}, classifier {@code docbook});
     * one that is not there is taken from the unpacked archive checked in with
     * the book, {@code docbookFallback/<artifactId>/<version>}, such as the
     * DocBook of a release that was never deployed.
     */
    @Parameter(property = "osgi.docbook.docbooks")
    List<String> docbooks;

    /** The unpacked DocBook archives of the book, see {@code docbooks} */
    @Parameter(property = "osgi.docbook.docbookFallback", defaultValue = "${project.basedir}/src/main/docbook")
    File docbookFallback;

    /**
     * {@code yes} for a draft (watermark, remarks, draft content), {@code no}
     * for a final specification; by default a {@code -SNAPSHOT} is a draft.
     */
    @Parameter(property = "osgi.docbook.draftMode")
    String draftMode;

    /** The years of the copyright line, e.g. {@code 2000, 2022}; by default those of the book */
    @Parameter(property = "osgi.docbook.copyrightYear")
    String copyrightYear;

    /**
     * The release shown in the HTML page titles, e.g. {@code 8}. The PDF does
     * not show it.
     */
    @Parameter(property = "osgi.docbook.releaseVersion", defaultValue = "${project.version}")
    String releaseVersion;

    /**
     * Fail on a link whose target is not in the book. By default a complete
     * book ({@code bookSource}) fails. A chapter, or the chapters of one
     * repository, link to other specifications, which only the complete books
     * contain: such a link is a warning when its target is in the index of the
     * published specifications, and an error that does not fail the build
     * otherwise.
     */
    @Parameter(property = "osgi.docbook.failOnUnresolvedLinks")
    Boolean failOnUnresolvedLinks;

    /**
     * Link references to chapters and packages that are not part of this
     * specification to their published pages. When false they are rendered as
     * plain text.
     */
    @Parameter(property = "osgi.docbook.externalLinks", defaultValue = "true")
    boolean externalLinks;

    /** The URL that the paths in the external link index are relative to */
    @Parameter(property = "osgi.docbook.externalLinkBaseUrl", defaultValue = "https://docs.osgi.org/specification/")
    String externalLinkBaseUrl;

    /** An external link index to use instead of the bundled one */
    @Parameter(property = "osgi.docbook.externalLinkIndex")
    File externalLinkIndex;

    @Inject
    RepositorySystem repositorySystem;

    /** The repository session used to resolve the DocBook archives of a book */
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    RepositorySystemSession repositorySession;

    /** The base name of every output file */
    String outputFileBaseName;

    /** The project base directory */
    Path baseDir;

    /** The build directory shared by the output formats */
    Path specDir;

    /** The links of the book whose target is not in the book */
    UnresolvedLinks links;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (isSkipped()) {
            LOG.info("Skipping the specification {} generation", formatName());
            return;
        }

        // Check parameters
        if(bookSource == null && (specTitle == null || specTitle.isBlank() || "null".equals(specTitle))) {
            LOG.error("No specification title");
            throw new MojoFailureException("A specification title must be supplied. It will default to the project name if that is available");
        }

        if (draftMode == null || draftMode.isBlank()) {
            draftMode = project.getVersion().endsWith("-SNAPSHOT") ? "yes" : "no";
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

        SpecBook book;
        if (bookSource != null) {
            Path staged = new BookStaging(specDir.resolve("book"))
                    .stage(bookSource.toPath(), resolveDocbooks());
            book = SpecBook.book(staged);
        } else if (project.getContextValue(BookStaging.ARCHIVE) instanceof Path archive) {
            Path staged = new BookStaging(specDir.resolve("book")).stageArchive(archive, specTitle,
                    project.getVersion(), newTransformerFactory());
            if (staged == null) {
                LOG.info("No chapter in the DocBook archive, no specification {}", formatName());
                return;
            }
            book = SpecBook.book(staged);
        } else {
            new SpecJavadoc(project, SpecJavadoc.sourceRoots(project, baseDir, apiSourceRoots),
                    specDir.resolve("javadoc")).generate(newTransformerFactory());

            book = SpecBook.setup(baseDir.resolve(chapterSource), specDir, specTitle,
                    project.getVersion(), newTransformerFactory());
        }

        links = new UnresolvedLinks(failOnUnresolvedLinks != null ? failOnUnresolvedLinks : bookSource != null,
                linkIndex());
        generate(book, formatDir);
        links.report();
    }

    /**
     * @return the index of the published specifications, which tells a link
     *         to another specification from a link without any target
     * @throws MojoExecutionException
     */
    URL linkIndex() throws MojoExecutionException {
        if (externalLinkIndex != null) {
            try {
                return externalLinkIndex.toPath().toAbsolutePath().toUri().toURL();
            } catch (MalformedURLException e) {
                throw new MojoExecutionException("Not a link index: " + externalLinkIndex, e);
            }
        }
        URL index = AbstractOsgiSpecMojo.class.getResource(UnresolvedLinks.INDEX);
        if (index == null) {
            throw new MojoExecutionException("Missing plugin resource " + UnresolvedLinks.INDEX);
        }
        return index;
    }

    /**
     * Tells a style sheet where the published specifications are, so that it
     * links to them the references to other specifications
     *
     * @param transformer the transformer of the style sheet
     * @param enabled false to leave such references as text
     */
    void externalLinks(Transformer transformer, boolean enabled) {
        if (enabled) {
            transformer.setParameter("external.links.index", externalLinkIndex != null
                    ? externalLinkIndex.toPath().toAbsolutePath().toUri().toString() : LINK_INDEX);
            transformer.setParameter("external.links.base", externalLinkBaseUrl);
        }
    }

    /**
     * @return the archives of {@code docbooks}: from the Maven repositories,
     *         or else from the fallback directory
     * @throws MojoExecutionException if an archive is in neither
     */
    private List<BookStaging.Source> resolveDocbooks() throws MojoExecutionException {
        List<BookStaging.Source> sources = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String coordinates : docbooks == null ? List.<String> of() : docbooks) {
            String[] gav = coordinates.trim().split(":");
            if (gav.length != 3) {
                throw new MojoExecutionException("Not groupId:artifactId:version: " + coordinates);
            }
            Path zip = resolve(gav);
            if (zip != null) {
                LOG.info("DocBook {} from {}", coordinates, zip);
                sources.add(new BookStaging.Source(coordinates, zip));
                continue;
            }
            Path dir = docbookFallback.toPath().resolve(gav[1]).resolve(gav[2]);
            if (Files.isDirectory(dir)) {
                LOG.info("DocBook {} from {}", coordinates, baseDir.relativize(dir.toAbsolutePath()));
                sources.add(new BookStaging.Source(coordinates, dir));
            } else {
                missing.add(coordinates);
            }
        }
        if (!missing.isEmpty()) {
            throw new MojoExecutionException("DocBook archives neither in the Maven repositories nor in "
                    + docbookFallback + ": " + missing);
        }
        return sources;
    }

    /**
     * @param gav groupId, artifactId and version of a DocBook archive
     * @return the archive from the Maven repositories, or null if it is not
     *         there
     */
    private Path resolve(String[] gav) {
        if (repositorySession == null) {
            // no Maven build, such as a test of the mojo
            return null;
        }
        ArtifactRequest request = new ArtifactRequest(
                new DefaultArtifact(gav[0], gav[1], OsgiSpecDocbookMojo.CLASSIFIER, "zip", gav[2]),
                project.getRemoteProjectRepositories(), null);
        try {
            return repositorySystem.resolveArtifact(repositorySession, request).getArtifact().getFile().toPath();
        } catch (ArtifactResolutionException e) {
            LOG.debug("DocBook {} not in the Maven repositories", String.join(":", gav), e);
            return null;
        }
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
