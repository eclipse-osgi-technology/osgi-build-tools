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
package org.eclipse.osgi.technology.buildtools.maven.docbook.test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.zip.ZipFile;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.testing.ArtifactStubFactory;
import org.apache.maven.project.MavenProject;
import org.eclipse.osgi.technology.buildtools.maven.docbook.OsgiSpecHtmlMojo;
import org.junit.jupiter.api.Test;

@MojoTest
class HTMLGenerationTest {

    @Test
    @InjectMojo(goal = "html")
    @Basedir("/test-projects/simple")
    public void testHtml(OsgiSpecHtmlMojo mojo) throws Exception {
        MavenProject project = MojoExtension.getVariableValueFromObject(mojo, "project");
        project.setArtifact(new ArtifactStubFactory().createArtifact(
                project.getGroupId(), project.getArtifactId(), project.getVersion()));
        mojo.execute();

        Path html = Path.of(project.getBuild().getDirectory(), "spec", "html");
        for (String file : List.of("index.html", "service.jdbc.html", "css/custom.css", "css/github.css",
                "js/main.js", "js/highlight.pack.js", "images/OSGi.svg", "images/draft.svg",
                "images/jdbc-classes.svg")) {
            assertTrue(Files.isRegularFile(html.resolve(file)), "Missing " + file);
        }

        String chapter = Files.readString(html.resolve("service.jdbc.html"), UTF_8);
        assertTrue(chapter.contains("src=\"images/jdbc-classes.svg\""), "Class diagram not referenced");
        assertTrue(chapter.contains("href=\"service.jdbc.html#org.osgi.service.jdbc.DataSourceFactory\""),
                "Javadoc link not resolved");
        assertTrue(chapter.contains("<div id=\"sidebar\">") || chapter.contains("id=\"tree\""),
                "Sidebar table of contents missing");

        List<Artifact> attached = project.getAttachedArtifacts();
        assertEquals(1, attached.size());
        Artifact zip = attached.get(0);
        assertEquals("zip", zip.getType());
        assertEquals("specification-html", zip.getClassifier());
        try (ZipFile zf = new ZipFile(zip.getFile())) {
            assertTrue(zf.getEntry("index.html") != null, "index.html not in zip");
            assertTrue(zf.getEntry("images/jdbc-classes.svg") != null, "Image not in zip");
        }
    }

    @Test
    @InjectMojo(goal = "html")
    @Basedir("/test-projects/simple")
    public void testJavadocReused(OsgiSpecHtmlMojo mojo) throws Exception {
        MavenProject project = MojoExtension.getVariableValueFromObject(mojo, "project");
        project.setArtifact(new ArtifactStubFactory().createArtifact(
                project.getGroupId(), project.getArtifactId(), project.getVersion()));
        MojoExtension.setVariableValueToObject(mojo, "attachZip", false);
        mojo.execute();

        // A second format in the same build must not regenerate the javadoc
        Path javadoc = Path.of(project.getBuild().getDirectory(), "spec", "javadoc", "javadoc.xml");
        FileTime generated = Files.getLastModifiedTime(javadoc);
        mojo.execute();
        assertEquals(generated, Files.getLastModifiedTime(javadoc));
        assertTrue(project.getAttachedArtifacts().isEmpty());
    }
}
