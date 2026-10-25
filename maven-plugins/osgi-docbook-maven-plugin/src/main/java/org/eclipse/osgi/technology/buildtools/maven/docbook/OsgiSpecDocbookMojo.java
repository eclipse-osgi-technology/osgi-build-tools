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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Packages the DocBook of a specification project, so that a book can be
 * assembled from it later: the chapters with their images and other included
 * files, the DocBook generated from the javadoc of the project, and the schema
 * listings the chapters include. The includes of the chapters are rewritten to
 * the layout of the archive:
 *
 * <pre>
 * chapters/&lt;xml:id&gt;/&lt;xml:id&gt;.xml   a chapter, with its images and included files
 * javadoc/&lt;package&gt;.xml              the javadoc of a package
 * resources/xmlns/..., json.schema/...  schema listings
 * resources/residentialdmt/...         the Residential DMT tree summary
 * META-INF/docbook.properties           the chapters and packages
 * </pre>
 *
 * A chapter may include the javadoc of a package of another project, such as
 * the Framework API chapter; that file comes from the DocBook archive of the
 * other project when the book is assembled. The archive is attached with the
 * classifier {@code docbook} and type {@code zip}.
 */
@Mojo(name = "docbook", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE)
public class OsgiSpecDocbookMojo extends AbstractMojo {

    /** The classifier of the DocBook archive */
    static final String CLASSIFIER = "docbook";

    private static final Logger LOG = LoggerFactory.getLogger(OsgiSpecDocbookMojo.class);

    private static final Pattern INCLUDE = Pattern.compile("(<(?:\\w+:)?include\\b[^>]*?\\bhref=\")([^\"]+)(\")");

    private static final Pattern FILEREF = Pattern.compile("(\\bfileref=\")([^\"]+)(\")");

    private static final Pattern ID = Pattern
            .compile("<(?:\\w+:)?(?:chapter|appendix|preface|part|article|section)\\b[^>]*?\\bxml:id=\"([^\"]+)\"");

    /** The chapter of the project; a missing file means the project has none */
    @Parameter(property = "osgi.docbook.spec.source", defaultValue = "src/main/resources/spec/spec.xml")
    String chapterSource;

    /** More chapters of the same project */
    @Parameter
    List<File> chapterSources;

    /**
     * The sources of the API to document, e.g. {@code ../api/src/main/java}
     * for a specification module next to the API module. Roots that do not
     * exist are ignored; without any, the compile source roots are documented.
     */
    @Parameter
    List<File> apiSourceRoots;

    /** Skip the DocBook archive */
    @Parameter(property = "osgi.docbook.docbook.skip", defaultValue = "false")
    boolean skip;

    @Inject
    MavenProject project;

    @Inject
    MavenProjectHelper helper;

    private Path outDir;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            LOG.info("Skipping the DocBook archive");
            return;
        }
        Path baseDir = project.getBasedir().toPath().toAbsolutePath();
        Path buildDir = baseDir.resolve(project.getBuild().getDirectory());
        Path specDir = buildDir.resolve("spec");
        outDir = specDir.resolve("docbook");
        clean(outDir);

        Path javadocDir = specDir.resolve("javadoc");
        new SpecJavadoc(project, SpecJavadoc.sourceRoots(project, baseDir, apiSourceRoots), javadocDir)
                .generate(AbstractOsgiSpecMojo.newTransformerFactory());
        TreeSet<String> packages = copyJavadoc(javadocDir);

        TreeSet<String> ids = new TreeSet<>();
        Path chapter = baseDir.resolve(chapterSource);
        if (Files.isRegularFile(chapter)) {
            ids.add(packChapter(chapter));
        } else {
            LOG.debug("No chapter {}", chapter);
        }
        if (chapterSources != null) {
            for (File f : chapterSources) {
                chapter = baseDir.resolve(f.toPath());
                if (!Files.isRegularFile(chapter)) {
                    throw new MojoExecutionException("The chapter " + chapter + " does not exist");
                }
                ids.add(packChapter(chapter));
            }
        }
        if (ids.isEmpty() && packages.isEmpty()) {
            LOG.warn("Neither chapters nor javadoc: the DocBook archive is empty");
        }

        // written by hand: Properties.store adds the time, and the archive
        // is meant to be reproducible
        String description = "# " + project.getGroupId() + ":" + project.getArtifactId() + "\n"
                + "chapters=" + String.join(",", ids) + "\n"
                + "packages=" + String.join(",", packages) + "\n"
                + "version=" + project.getVersion() + "\n";
        Path properties = outDir.resolve("META-INF/docbook.properties");
        try {
            Files.createDirectories(properties.getParent());
            Files.writeString(properties, description, UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write " + properties, e);
        }

        String baseName = project.getBuild().getFinalName();
        if (baseName == null || baseName.isBlank()) {
            baseName = project.getArtifactId() + "-" + project.getVersion();
        }
        Path zip = buildDir.resolve(baseName + "-" + CLASSIFIER + ".zip");
        zip(outDir, zip);
        helper.attachArtifact(project, "zip", CLASSIFIER, zip.toFile());
        LOG.info("DocBook archive {} with chapters {} and {} packages attached", zip.getFileName(), ids,
                packages.size());
    }

    private TreeSet<String> copyJavadoc(Path javadocDir) throws MojoExecutionException {
        TreeSet<String> packages = new TreeSet<>();
        if (!Files.isDirectory(javadocDir)) {
            return packages;
        }
        try (Stream<Path> files = Files.list(javadocDir)) {
            for (Path p : files.sorted().toList()) {
                String name = p.getFileName().toString();
                // javadoc.xml is the raw doclet output, the rest are packages
                if (!name.endsWith(".xml") || name.equals("javadoc.xml")) {
                    continue;
                }
                copy(p, outDir.resolve("javadoc").resolve(name));
                if (!name.endsWith("-ddf.xml")) {
                    packages.add(name.substring(0, name.length() - 4));
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy the javadoc DocBook", e);
        }
        return packages;
    }

    private String packChapter(Path chapter) throws MojoExecutionException {
        String xml = read(chapter);
        Matcher m = ID.matcher(xml);
        String id;
        if (m.find()) {
            id = m.group(1);
        } else {
            String name = chapter.getFileName().toString();
            id = name.substring(0, name.lastIndexOf('.'));
            LOG.warn("Chapter {} has no xml:id, using {}", chapter, id);
        }
        Path target = outDir.resolve("chapters").resolve(id).resolve(id + ".xml");
        pack(chapter, target);
        return id;
    }

    /**
     * Writes a chapter or an included file to its place in the archive, with
     * its includes and images rewritten, and packs what they refer to.
     */
    private void pack(Path source, Path target) throws MojoExecutionException {
        String xml = read(source);
        xml = rewrite(xml, INCLUDE, source, target, true);
        xml = rewrite(xml, FILEREF, source, target, false);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, xml, UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write " + target, e);
        }
    }

    private String rewrite(String xml, Pattern pattern, Path source, Path target, boolean include)
            throws MojoExecutionException {
        Matcher m = pattern.matcher(xml);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String href = m.group(2);
            String rewritten = include ? rewriteInclude(href, source, target) : rewriteFile(href, source, target);
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + rewritten + m.group(3)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String rewriteInclude(String href, Path source, Path target) throws MojoExecutionException {
        Matcher javadoc = SpecBook.LEGACY_JAVADOC.matcher(href);
        if (javadoc.matches()) {
            Path file = outDir.resolve("javadoc").resolve(javadoc.group(1));
            if (!Files.isRegularFile(file)) {
                LOG.info("{} includes the javadoc {} of another project", source.getFileName(),
                        javadoc.group(1));
            }
            return relative(target, file);
        }
        Matcher resource = SpecBook.LEGACY_RESOURCE.matcher(href);
        if (resource.matches()) {
            // the sources live under xmlns/, json.schema/ or
            // residentialdmt/ at the repository root: search upwards from
            // the chapter
            Path dir = source.getParent();
            for (int i = 0; i < 8 && dir != null; i++, dir = dir.getParent()) {
                Path p = dir.resolve(resource.group(1));
                if (Files.isRegularFile(p)) {
                    Path file = outDir.resolve("resources").resolve(resource.group(1));
                    try {
                        Files.createDirectories(file.getParent());
                        Files.writeString(file, SpecBook.schemaListing(p), UTF_8);
                    } catch (IOException e) {
                        throw new MojoExecutionException("Failed to copy " + p, e);
                    }
                    return relative(target, file);
                }
            }
            throw new MojoExecutionException(source + " includes " + href + ", which was not found");
        }
        return rewriteFile(href, source, target);
    }

    /**
     * A file next to the chapter, such as an image or an included section:
     * kept at the same relative path, or next to the chapter when it lies
     * outside the chapter's directory.
     */
    private String rewriteFile(String href, Path source, Path target) throws MojoExecutionException {
        if (href.contains(":")) {
            // an absolute URI
            return href;
        }
        Path file = source.getParent().resolve(href).normalize();
        if (!Files.isRegularFile(file)) {
            throw new MojoExecutionException(source + " refers to " + href + ", which was not found");
        }
        String kept = href.startsWith("../") || href.startsWith("/") ? file.getFileName().toString() : href;
        Path packed = target.getParent().resolve(kept).normalize();
        if (file.getFileName().toString().endsWith(".xml")) {
            pack(file, packed);
        } else {
            copy(file, packed);
        }
        return kept;
    }

    private static String relative(Path from, Path to) {
        return from.getParent().relativize(to).toString().replace('\\', '/');
    }

    private static String read(Path p) throws MojoExecutionException {
        try {
            return Files.readString(p, UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read " + p, e);
        }
    }

    private static void copy(Path from, Path to) throws MojoExecutionException {
        try {
            Files.createDirectories(to.getParent());
            Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy " + from, e);
        }
    }

    private static void clean(Path dir) throws MojoExecutionException {
        try {
            if (Files.exists(dir)) {
                try (Stream<Path> paths = Files.walk(dir)) {
                    for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(p);
                    }
                }
            }
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to clean " + dir, e);
        }
    }

    private static void zip(Path dir, Path zip) throws MojoExecutionException {
        try (Stream<Path> paths = Files.walk(dir);
                OutputStream os = new BufferedOutputStream(Files.newOutputStream(zip));
                ZipOutputStream zos = new ZipOutputStream(os)) {
            // sorted and with a fixed time, so that the archive is reproducible
            for (Path p : paths.filter(Files::isRegularFile).sorted().toList()) {
                ZipEntry entry = new ZipEntry(dir.relativize(p).toString().replace('\\', '/'));
                entry.setLastModifiedTime(FileTime.fromMillis(315532800000L));
                zos.putNextEntry(entry);
                Files.copy(p, zos);
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create " + zip, e);
        }
    }
}
