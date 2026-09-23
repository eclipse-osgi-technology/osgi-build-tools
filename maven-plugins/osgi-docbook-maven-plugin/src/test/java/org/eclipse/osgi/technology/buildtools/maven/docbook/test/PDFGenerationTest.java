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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    @Test
    @InjectMojo(goal = "pdf")
    @Basedir("/test-projects/simple")
    public void testPdf(OsgiSpecPdfMojo mojo) throws Exception {
        MavenProject project = MojoExtension.getVariableValueFromObject(mojo, "project");
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
    }
}
