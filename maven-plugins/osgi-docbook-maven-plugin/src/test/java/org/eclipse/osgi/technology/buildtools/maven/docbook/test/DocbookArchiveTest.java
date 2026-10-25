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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.Reader;
import java.nio.file.Files;
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
import org.junit.jupiter.api.Test;

/**
 * The specification module of a repository: the docbook goal packs its
 * chapters and the DocBook of its API.
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
    @Basedir("/test-projects/javadoc-only/spec")
    public void testJavadocOnly(@InjectMojo(goal = "docbook") OsgiSpecDocbookMojo docbook) throws Exception {
        MavenProject project = project(docbook);
        docbook.execute();

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

    private static Artifact attached(MavenProject project, String classifier) {
        return project.getAttachedArtifacts().stream().filter(a -> classifier.equals(a.getClassifier()))
                .findFirst().orElseThrow(() -> new AssertionError("No attached " + classifier));
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
