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
package org.eclipse.osgi.technology.buildtools.maven.docbook.test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.ArtifactStubFactory;
import org.apache.maven.project.MavenProject;
import org.eclipse.osgi.technology.buildtools.maven.docbook.OsgiSpecDocbookMojo;
import org.eclipse.osgi.technology.buildtools.maven.docbook.OsgiSpecHtmlMojo;
import org.eclipse.osgi.technology.buildtools.maven.docbook.OsgiSpecPdfMojo;
import org.junit.jupiter.api.Test;

/**
 * The specification module of a repository: the docbook goal packs its
 * chapters and the DocBook of its API, the pdf and html goals render that
 * archive with the javadoc of other projects from their sources.
 */
@MojoTest
class DocbookArchiveTest {

    @Test
    @InjectMojo(goal = "docbook")
    @Basedir("/test-projects/repo/spec")
    public void testArchive(OsgiSpecDocbookMojo docbook) throws Exception {
        MavenProject project = project(docbook);
        docbook.execute();

        Artifact zip = attached(project, "docbook");
        Set<String> entries = entries(zip.getFile());
        assertEquals(new TreeSet<>(List.of("META-INF/docbook.properties",
                "chapters/repo.one/repo.one.xml", "chapters/repo.one/one-classes.svg", "chapters/repo.one/part.xml",
                "chapters/repo.xml/repo.xml.xml", "javadoc/org.osgi.service.repo.xml",
                "resources/xmlns/repo/v1.0.0/repo.xsd")), entries,
                "the javadoc of another project is not packed");

        try (ZipFile z = new ZipFile(zip.getFile())) {
            String one = read(z, "chapters/repo.one/repo.one.xml");
            assertTrue(one.contains("href=\"../../javadoc/org.osgi.service.repo.xml\""), one);
            assertTrue(one.contains("href=\"../../resources/xmlns/repo/v1.0.0/repo.xsd\""), one);
            assertTrue(one.contains("href=\"part.xml\""), one);
            assertTrue(one.contains("fileref=\"one-classes.svg\""), one);
            String two = read(z, "chapters/repo.xml/repo.xml.xml");
            assertTrue(two.contains("href=\"../../javadoc/org.osgi.service.foreign.xml\""), two);
            assertTrue(read(z, "javadoc/org.osgi.service.repo.xml").contains("repo-javadoc-marker"));
            String xsd = read(z, "resources/xmlns/repo/v1.0.0/repo.xsd");
            assertTrue(xsd.startsWith("<schema") && !xsd.contains("repo-xsd-licence-marker"), xsd);
            assertEquals("""
                    # org.osgi.spec:org.osgi.service.repo
                    chapters=repo.one,repo.xml
                    packages=org.osgi.service.repo
                    version=1.0.0
                    """, read(z, "META-INF/docbook.properties"));
        }
    }

    @Test
    @InjectMojo(goal = "docbook")
    @Basedir("/test-projects/repo/spec")
    public void testMissingChapter(OsgiSpecDocbookMojo docbook) throws Exception {
        project(docbook);
        MojoExtension.setVariableValueToObject(docbook, "chapterSources",
                List.of(new File("one/spec.xml"), new File("three/spec.xml")));

        MojoExecutionException e = assertThrows(MojoExecutionException.class, docbook::execute);
        assertTrue(e.getMessage().contains("three/spec.xml does not exist"), e.getMessage());
    }

    @Test
    @Basedir("/test-projects/repo/spec")
    public void testPdf(@InjectMojo(goal = "docbook") OsgiSpecDocbookMojo docbook,
            @InjectMojo(goal = "pdf") OsgiSpecPdfMojo pdf) throws Exception {
        MavenProject project = project(docbook);
        included(docbook);
        MojoExtension.setVariableValueToObject(pdf, "project", project);
        MojoExtension.setVariableValueToObject(pdf, "specTitle", project.getName());
        docbook.execute();
        pdf.execute();

        List<Artifact> attached = project.getAttachedArtifacts();
        assertEquals(List.of("zip", "fo", "pdf"), attached.stream().map(Artifact::getType).toList());
        assertTrue(Files.readString(attached(project, "specification", "pdf").getFile().toPath(), java.nio.charset.StandardCharsets.ISO_8859_1)
                .startsWith("%PDF-"), "Not a PDF");

        assertFalse(entries(attached(project, "docbook").getFile()).contains("javadoc/org.osgi.service.foreign.xml"),
                "the javadoc of the included sources is packed");
        String fo = Files.readString(attached(project, "specification", "fo").getFile().toPath(), UTF_8);
        for (String marker : List.of("chapter-one-marker", "chapter-two-marker", "part-marker", "repo-xsd-marker",
                "repo-javadoc-marker", "A type of another project, foreign-javadoc-marker",
                "Repository Test Specification")) {
            assertTrue(fo.contains(marker), "Missing " + marker);
        }
        assertFalse(fo.contains("repo-xsd-licence-marker"), "xsd prolog listed");
        // in the order of the labels: chapter two has label 1
        assertTrue(fo.indexOf("chapter-two-marker") < fo.indexOf("chapter-one-marker"), "chapters not ordered");
    }

    @Test
    @Basedir("/test-projects/repo/spec")
    public void testHtml(@InjectMojo(goal = "docbook") OsgiSpecDocbookMojo docbook,
            @InjectMojo(goal = "html") OsgiSpecHtmlMojo html) throws Exception {
        MavenProject project = project(docbook);
        included(docbook);
        MojoExtension.setVariableValueToObject(html, "project", project);
        docbook.execute();
        html.execute();

        Path dir = Path.of(project.getBuild().getDirectory(), "spec", "html");
        for (String file : List.of("index.html", "repo.one.html", "repo.xml.html", "images/one-classes.svg")) {
            assertTrue(Files.isRegularFile(dir.resolve(file)), "Missing " + file);
        }
        assertTrue(Files.readString(dir.resolve("repo.xml.html"), UTF_8).contains("foreign-javadoc-marker"));
        assertTrue(Files.readString(dir.resolve("repo.one.html"), UTF_8).contains("repo-javadoc-marker"));
        assertEquals("specification-html", attached(project, "specification-html").getClassifier());
    }

    @Test
    @Basedir("/test-projects/repo/spec")
    public void testMissingJavadoc(@InjectMojo(goal = "docbook") OsgiSpecDocbookMojo docbook,
            @InjectMojo(goal = "pdf") OsgiSpecPdfMojo pdf) throws Exception {
        MavenProject project = project(docbook);
        MojoExtension.setVariableValueToObject(pdf, "project", project);
        docbook.execute();

        MojoExecutionException e = assertThrows(MojoExecutionException.class, pdf::execute);
        assertTrue(e.getMessage().contains(
                "chapters/repo.xml/repo.xml.xml includes ../../javadoc/org.osgi.service.foreign.xml"), e.getMessage());
        assertEquals(List.of("zip"), project.getAttachedArtifacts().stream().map(Artifact::getType).toList());
    }

    @Test
    @Basedir("/test-projects/javadoc-only/spec")
    public void testJavadocOnly(@InjectMojo(goal = "docbook") OsgiSpecDocbookMojo docbook,
            @InjectMojo(goal = "pdf") OsgiSpecPdfMojo pdf) throws Exception {
        MavenProject project = project(docbook);
        MojoExtension.setVariableValueToObject(pdf, "project", project);
        docbook.execute();
        pdf.execute();

        assertEquals(List.of("zip"), project.getAttachedArtifacts().stream().map(Artifact::getType).toList(),
                "no chapter, no PDF");
        Artifact zip = attached(project, "docbook");
        assertEquals(new TreeSet<>(List.of("META-INF/docbook.properties", "javadoc/org.osgi.service.foreign.xml")),
                entries(zip.getFile()));
        try (ZipFile z = new ZipFile(zip.getFile())) {
            assertTrue(read(z, "javadoc/org.osgi.service.foreign.xml").contains("foreign-javadoc-marker"));
            assertTrue(read(z, "META-INF/docbook.properties").contains("chapters=\n"));
        }
    }

    /** The project of the test, with the coordinates of its POM */
    private static MavenProject project(OsgiSpecDocbookMojo mojo) throws Exception {
        MavenProject project = MojoExtension.getVariableValueFromObject(mojo, "project");
        try (Reader r = Files.newBufferedReader(project.getBasedir().toPath().resolve("pom.xml"))) {
            Model pom = new MavenXpp3Reader().read(r);
            project.setGroupId(pom.getGroupId());
            project.setArtifactId(pom.getArtifactId());
            project.setVersion(pom.getVersion());
            project.setName(pom.getName());
        }
        project.getBuild().setFinalName(project.getArtifactId() + "-" + project.getVersion());
        project.setArtifact(new ArtifactStubFactory().createArtifact(
                project.getGroupId(), project.getArtifactId(), project.getVersion()));
        return project;
    }

    /**
     * The javadoc of org.osgi.service.foreign from the sources of the other
     * project
     */
    private static void included(OsgiSpecDocbookMojo docbook) throws Exception {
        MojoExtension.setVariableValueToObject(docbook, "includedSourceRoots",
                List.of(new File("../../javadoc-only/api/src/main/java")));
    }

    private static Artifact attached(MavenProject project, String classifier) {
        return project.getAttachedArtifacts().stream().filter(a -> classifier.equals(a.getClassifier()))
                .findFirst().orElseThrow(() -> new AssertionError("No attached " + classifier));
    }

    private static Artifact attached(MavenProject project, String classifier, String type) {
        return project.getAttachedArtifacts().stream()
                .filter(a -> classifier.equals(a.getClassifier()) && type.equals(a.getType())).findFirst()
                .orElseThrow(() -> new AssertionError("No attached " + classifier + " " + type));
    }

    private static Set<String> entries(File zip) throws Exception {
        try (ZipFile z = new ZipFile(zip)) {
            return z.stream().map(ZipEntry::getName).collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private static String read(ZipFile z, String name) throws Exception {
        ZipEntry e = z.getEntry(name);
        assertTrue(e != null, "No " + name);
        return new String(z.getInputStream(e).readAllBytes(), UTF_8);
    }
}
