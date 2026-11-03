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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.testing.ArtifactStubFactory;
import org.apache.maven.project.MavenProject;
import org.eclipse.osgi.technology.buildtools.maven.docbook.OsgiSpecPdfMojo;
import org.junit.jupiter.api.Test;

@MojoTest
class PDFGenerationTest {

    /** A page sequence on a draft page master, which has the watermark */
    private static final Pattern DRAFT = Pattern
            .compile("<fo:page-sequence [^>]*master-reference=\"[a-z-]+-draft\"");

    @Test
    @InjectMojo(goal = "pdf")
    @Basedir("/test-projects/simple")
    public void testPdf(OsgiSpecPdfMojo mojo) throws Exception {
        MavenProject project = MojoExtension.getVariableValueFromObject(mojo, "project");
        // the test harness does not take the version of the POM
        project.setVersion("1.0-SNAPSHOT");
        project.setArtifact(new ArtifactStubFactory().createArtifact(
                project.getGroupId(), project.getArtifactId(), project.getVersion()));
        mojo.execute();

        Path spec = Path.of(project.getBuild().getDirectory(), "spec");
        assertTrue(Files.isRegularFile(spec.resolve("javadoc/org.osgi.service.jdbc.xml")),
                "Javadoc docbook missing");

        List<Artifact> attached = project.getAttachedArtifacts();
        assertEquals(2, attached.size());
        assertEquals("fo", attached.get(0).getType());
        assertEquals("pdf", attached.get(1).getType());
        for (Artifact a : attached) {
            assertEquals("specification", a.getClassifier());
            assertTrue(a.getFile().length() > 0, "Empty " + a.getFile());
        }
        assertTrue(Files.readString(attached.get(1).getFile().toPath(), java.nio.charset.StandardCharsets.ISO_8859_1)
                .startsWith("%PDF-"), "Not a PDF");

        String fo = Files.readString(attached.get(0).getFile().toPath());
        assertTrue(DRAFT.matcher(fo).find(), "a snapshot is no draft");

        // the OSGi fonts draw an ornament for these: Arial Unicode
        assertTrue(fo.matches("(?s).*<fo:inline [^>]*font-family=\"ArialUnicodeMS\">\u2126</fo:inline>.*"),
                "Ohm sign not in Arial");
        assertTrue(fo.matches("(?s).*<fo:inline [^>]*font-family=\"ArialUnicodeMS\">\u03a9</fo:inline>.*"),
                "Omega not in Arial");
    }

    @Test
    @InjectMojo(goal = "pdf")
    @Basedir("/test-projects/simple")
    public void testReleaseIsFinal(OsgiSpecPdfMojo mojo) throws Exception {
        MavenProject project = MojoExtension.getVariableValueFromObject(mojo, "project");
        project.setVersion("1.0.0");
        project.setArtifact(new ArtifactStubFactory().createArtifact(
                project.getGroupId(), project.getArtifactId(), project.getVersion()));
        mojo.execute();

        String fo = Files.readString(project.getAttachedArtifacts().get(0).getFile().toPath());
        assertFalse(DRAFT.matcher(fo).find(), "a release is a draft");
    }

    @Test
    @InjectMojo(goal = "pdf")
    @Basedir("/test-projects/simple")
    public void testLinksToOtherSpecifications(OsgiSpecPdfMojo mojo) throws Exception {
        MavenProject project = MojoExtension.getVariableValueFromObject(mojo, "project");
        project.setArtifact(new ArtifactStubFactory().createArtifact(
                project.getGroupId(), project.getArtifactId(), project.getVersion()));
        MojoExtension.setVariableValueToObject(mojo, "chapterSource", "src/main/resources/spec/spec-links.xml");
        // a chapter links to other specifications: they are published, so
        // only the id that is in no specification is reported
        mojo.execute();

        Object links = MojoExtension.getVariableValueFromObject(mojo, "links");
        Map<String, Set<String>> ids = MojoExtension.getVariableValueFromObject(links, "links");
        assertEquals(Set.of("no.such.id"), ids.keySet());
        assertEquals(Set.of("125.1"), ids.get("no.such.id"));
        assertEquals("pdf", project.getAttachedArtifacts().get(1).getType());

        // the references go to the published specifications, as in the HTML
        String fo = Files.readString(project.getAttachedArtifacts().get(0).getFile().toPath());
        assertTrue(fo.contains("external-destination=\"url(https://docs.osgi.org/specification/osgi.cmpn/8.1.0/service.cm.html)\""),
                "Chapter reference not linked externally");
        assertTrue(fo.contains("external-destination=\"url(https://docs.osgi.org/specification/osgi.cmpn/8.1.0/service.namespaces.html#service.namespaces-osgi.service.namespace)\""),
                "Section link not linked externally");
        assertTrue(fo.matches("(?s).*font-style=\"italic\">[^<]*Configuration Admin[^<]*</fo:inline>.*"),
                "Chapter title missing or not italic");
        // only the reference to the id that is in no specification is unresolved
        assertEquals(1, fo.split("\\?\\?\\?", -1).length - 1, "Unresolved references");
    }

    @Test
    @InjectMojo(goal = "pdf")
    @Basedir("/test-projects/simple")
    public void testReleaseInfoShowsVersionOfOtherSpecification(OsgiSpecPdfMojo mojo) throws Exception {
        // the release info of a chapter, and every page header, show the version of the package
        assertEquals("Version 1.0", releaseInfoText(mojo, "/links/index-with-version.xml"));
    }

    @Test
    @InjectMojo(goal = "pdf")
    @Basedir("/test-projects/simple")
    public void testReleaseInfoShowsVersionFromBundledIndex(OsgiSpecPdfMojo mojo) throws Exception {
        // org.osgi.resource is Version 1.0 in Core Release 8, as in its published chapter
        assertEquals("Version 1.0", releaseInfoText(mojo, null));
    }

    @Test
    @InjectMojo(goal = "pdf")
    @Basedir("/test-projects/simple")
    public void testReleaseInfoWithoutVersionOfOtherSpecification(OsgiSpecPdfMojo mojo) throws Exception {
        // an index without the version gives the name of the package, not ???
        assertEquals("org.osgi.resource", releaseInfoText(mojo, "/links/index-without-version.xml"));
    }

    /**
     * @param index the index to use, or null for the bundled one
     * @return the text of the links to org.osgi.resource, which must all be the same
     */
    private static String releaseInfoText(OsgiSpecPdfMojo mojo, String index) throws Exception {
        MavenProject project = MojoExtension.getVariableValueFromObject(mojo, "project");
        project.setArtifact(new ArtifactStubFactory().createArtifact(
                project.getGroupId(), project.getArtifactId(), project.getVersion()));
        MojoExtension.setVariableValueToObject(mojo, "chapterSource", "src/main/resources/spec/spec-releaseinfo.xml");
        if (index != null) {
            MojoExtension.setVariableValueToObject(mojo, "externalLinkIndex",
                    Path.of(PDFGenerationTest.class.getResource(index).toURI()).toFile());
        }
        mojo.execute();

        Object links = MojoExtension.getVariableValueFromObject(mojo, "links");
        Map<String, Set<String>> ids = MojoExtension.getVariableValueFromObject(links, "links");
        assertEquals(Set.of(), ids.keySet());
        String fo = Files.readString(project.getAttachedArtifacts().get(0).getFile().toPath());
        assertFalse(fo.contains("???"), "Unresolved reference in the release info");
        Matcher m = Pattern.compile("(?s)<fo:basic-link external-destination=\"url\\(([^)]*)\\)\"[^>]*>(.*?)</fo:basic-link>")
                .matcher(fo);
        Set<String> texts = new java.util.TreeSet<>();
        int linked = 0;
        while (m.find()) {
            if (m.group(1).equals("https://docs.osgi.org/specification/osgi.core/8.0.0/framework.api.html#org.osgi.resource")) {
                texts.add(m.group(2).replaceAll("<[^>]*>", "").strip());
                linked++;
            }
        }
        assertTrue(linked > 1, "The title page and the page headers are not linked");
        assertEquals(1, texts.size(), texts.toString());
        return texts.iterator().next();
    }

    @Test
    @InjectMojo(goal = "pdf")
    @Basedir("/test-projects/simple")
    public void testLinksToOtherSpecificationsAsText(OsgiSpecPdfMojo mojo) throws Exception {
        MavenProject project = MojoExtension.getVariableValueFromObject(mojo, "project");
        project.setArtifact(new ArtifactStubFactory().createArtifact(
                project.getGroupId(), project.getArtifactId(), project.getVersion()));
        MojoExtension.setVariableValueToObject(mojo, "chapterSource", "src/main/resources/spec/spec-links.xml");
        MojoExtension.setVariableValueToObject(mojo, "externalLinks", false);
        mojo.execute();

        Object links = MojoExtension.getVariableValueFromObject(mojo, "links");
        Map<String, Set<String>> ids = MojoExtension.getVariableValueFromObject(links, "links");
        assertEquals(Set.of("service.cm", "service.namespaces-osgi.service.namespace", "no.such.id"), ids.keySet());
        String fo = Files.readString(project.getAttachedArtifacts().get(0).getFile().toPath());
        assertFalse(fo.contains("docs.osgi.org/specification/osgi.cmpn"), "Linked externally");
    }
}
