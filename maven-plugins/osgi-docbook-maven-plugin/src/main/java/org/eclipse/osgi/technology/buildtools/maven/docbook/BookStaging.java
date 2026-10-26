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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.transform.TransformerFactory;

import org.apache.maven.plugin.MojoExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stages a complete book: the DocBook archives of the book and the files of
 * the book itself are merged into one directory, so that every include of the
 * book is a plain relative path. The book of a project's own chapters is
 * staged the same way, from the archive of the docbook goal.
 */
final class BookStaging {

    /**
     * A DocBook archive: a zip file, or an unpacked archive
     *
     * @param id where it comes from, e.g. its coordinates
     * @param file the zip file or the directory
     */
    record Source(String id, Path file) {
    }

    /** The project context key of the archive directory of the docbook goal */
    static final String ARCHIVE = BookStaging.class.getName() + ".archive";

    /**
     * The directory next to the archive with the javadoc of the included
     * sources of other projects, see the docbook goal
     */
    static final String INCLUDED = "included";

    /** The front matter of the book that wraps a project's chapters */
    private static final List<String> FRONT_MATTER = List.of(
            "preface/eclipse.foundation.specification.license.1.0.xml", "preface/preface.xml",
            "001/introduction.xml");

    private static final Pattern LABEL = Pattern
            .compile("<(?:\\w+:)?(?:chapter|appendix)\\b[^>]*?\\blabel=\"([^\"]+)\"");

    private static final Pattern INCLUDE = Pattern.compile("<(?:\\w+:)?include\\b[^>]*?\\bhref=\"([^\"]+)\"");

    private static final Logger LOG = LoggerFactory.getLogger(BookStaging.class);

    private final Path dir;

    /** Which archive (or the book) each staged file comes from */
    private final Map<Path, String> origins = new HashMap<>();

    /**
     * @param dir the staging directory, emptied first
     */
    BookStaging(Path dir) {
        this.dir = dir;
    }

    /**
     * @param bookSource the book file; its whole directory is staged
     * @param sources the DocBook archives of the book
     * @return the staged book file
     * @throws MojoExecutionException if a staged file would be replaced by
     *             different content
     */
    Path stage(Path bookSource, List<Source> sources) throws MojoExecutionException {
        if (!Files.isRegularFile(bookSource)) {
            throw new MojoExecutionException("The book " + bookSource + " does not exist");
        }
        clean();

        if (sources.isEmpty()) {
            LOG.warn("No DocBook archives, see docbooks");
        }
        for (Source s : sources) {
            try {
                if (Files.isDirectory(s.file())) {
                    putAll(s.file(), s.id());
                } else {
                    unzip(s.file(), s.id());
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to stage " + s.id(), e);
            }
        }

        Path bookDir = bookSource.toAbsolutePath().getParent();
        try (Stream<Path> files = Files.walk(bookDir)) {
            for (Path p : files.filter(Files::isRegularFile).sorted().toList()) {
                try (InputStream is = Files.newInputStream(p)) {
                    put(bookDir.relativize(p).toString().replace('\\', '/'), is, "the book");
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to stage the book " + bookDir, e);
        }
        checkIncludes();
        LOG.info("Staged the book and {} DocBook archives in {}", sources.size(), dir);
        return dir.resolve(bookSource.getFileName().toString());
    }

    /**
     * Stages the book of a project's own chapters: the archive of the docbook
     * goal, the javadoc of the included sources of other projects, and the
     * bundled front matter. The chapters are in the order of their labels.
     *
     * @param archive the archive directory of the docbook goal
     * @param title the book title
     * @param version the book version
     * @param transformerFactory
     * @return the staged book file, or null when the archive has no chapter
     * @throws MojoExecutionException if a staged file would be replaced by
     *             different content, or a chapter includes a file that is not
     *             staged
     */
    Path stageArchive(Path archive, String title, String version, TransformerFactory transformerFactory)
            throws MojoExecutionException {
        clean();
        List<String> chapters = chapters(archive);
        if (chapters.isEmpty()) {
            return null;
        }
        try {
            putAll(archive, "the project");
            Path included = archive.resolveSibling(INCLUDED);
            if (Files.isDirectory(included)) {
                putAll(included, "the included sources");
            }
            for (String f : FRONT_MATTER) {
                try (InputStream is = BookStaging.class.getResourceAsStream("/docbook/book/" + f)) {
                    put(f, is, "the front matter");
                }
            }
            String book = SpecBook.wrapper(title, version, chapters, transformerFactory);
            put("book.xml", new ByteArrayInputStream(book.getBytes(UTF_8)), "the book");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to stage the chapters of " + archive, e);
        }
        checkIncludes();
        LOG.info("Staged the chapters {} in {}", chapters, dir);
        return dir.resolve("book.xml");
    }

    /** Stages the files of a directory, but its META-INF */
    private void putAll(Path source, String origin) throws IOException, MojoExecutionException {
        try (Stream<Path> files = Files.walk(source)) {
            for (Path p : files.filter(Files::isRegularFile).sorted().toList()) {
                String name = source.relativize(p).toString().replace('\\', '/');
                if (name.startsWith("META-INF/")) {
                    continue;
                }
                try (InputStream is = Files.newInputStream(p)) {
                    put(name, is, origin);
                }
            }
        }
    }

    /**
     * @return the chapters of an archive, chapters/&lt;id&gt;/&lt;id&gt;.xml, in
     *         the order of their numeric labels, then the others
     */
    private static List<String> chapters(Path archive) throws MojoExecutionException {
        Path dir = archive.resolve("chapters");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        record Chapter(String path, String label) {
            int number() {
                return label != null && label.matches("\\d+") ? Integer.parseInt(label) : Integer.MAX_VALUE;
            }
        }
        List<Chapter> chapters = new ArrayList<>();
        try (Stream<Path> ids = Files.list(dir)) {
            for (Path p : ids.sorted().toList()) {
                String id = p.getFileName().toString();
                Path file = p.resolve(id + ".xml");
                if (Files.isRegularFile(file)) {
                    Matcher m = LABEL.matcher(Files.readString(file, UTF_8));
                    chapters.add(new Chapter("chapters/" + id + "/" + id + ".xml", m.find() ? m.group(1) : null));
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to list the chapters of " + archive, e);
        }
        return chapters.stream()
                .sorted(Comparator.comparingInt(Chapter::number).thenComparing(Chapter::path))
                .map(Chapter::path)
                .toList();
    }

    /**
     * Every include of a staged file must be staged too: the javadoc of
     * another project only comes with its DocBook archive.
     */
    private void checkIncludes() throws MojoExecutionException {
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(dir)) {
            // a chapter directory is named after the xml:id, such as util.xml
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".xml") && Files.isRegularFile(f))
                    .sorted().toList()) {
                Matcher m = INCLUDE.matcher(Files.readString(p, UTF_8));
                while (m.find()) {
                    String href = m.group(1);
                    if (!href.isEmpty() && !href.contains(":")
                            && !Files.isRegularFile(p.getParent().resolve(href).normalize())) {
                        missing.add(dir.relativize(p).toString().replace('\\', '/') + " includes " + href);
                    }
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to check the includes of " + dir, e);
        }
        if (!missing.isEmpty()) {
            missing.forEach(LOG::error);
            throw new MojoExecutionException("Files included by the book are missing: " + missing
                    + ". The javadoc of another project comes from its DocBook archive, see docbooks,"
                    + " or for the chapters of a project from its sources, see includedSourceRoots.");
        }
    }

    private void unzip(Path file, String origin) throws IOException, MojoExecutionException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            for (ZipEntry e : zip.stream().filter(e -> !e.isDirectory()).toList()) {
                // every archive has its own description
                if (e.getName().startsWith("META-INF/")) {
                    continue;
                }
                try (InputStream is = zip.getInputStream(e)) {
                    put(e.getName(), is, origin);
                }
            }
        }
        LOG.debug("Staged {}", origin);
    }

    private void put(String name, InputStream is, String origin) throws IOException, MojoExecutionException {
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(dir)) {
            throw new MojoExecutionException("Entry " + name + " of " + origin + " is outside the book");
        }
        byte[] content = is.readAllBytes();
        String previous = origins.putIfAbsent(target, origin);
        if (previous != null) {
            // the same schema or image may come with several chapters
            if (!Arrays.equals(Files.readAllBytes(target), content)) {
                throw new MojoExecutionException(
                        name + " of " + origin + " differs from the one of " + previous);
            }
            return;
        }
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    private void clean() throws MojoExecutionException {
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
}
