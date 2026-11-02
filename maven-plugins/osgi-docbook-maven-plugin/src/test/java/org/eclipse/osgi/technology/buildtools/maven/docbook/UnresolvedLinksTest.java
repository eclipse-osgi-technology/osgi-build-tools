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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;

class UnresolvedLinksTest {

    private static final URL INDEX = UnresolvedLinks.class.getResource(UnresolvedLinks.INDEX);

    /** The messages of a chapter build of Remote Service Admin, and one of the chunker */
    private static final List<String> MESSAGES = List.of(
            "[122.9.5] Error: no ID for constraint linkend: service.namespaces-osgi.service.namespace.",
            "[122.9.5] XLink to nonexistent id: service.namespaces-osgi.service.namespace",
            "[122.2] XLink to nonexistent id: service.namespaces-osgi.service.namespace",
            "ERROR: xref linking to intro.core.release has no generated link text.",
            "XRef to nonexistent id: intro.core.release",
            "0Endterm points to nonexistent ID: service.cm-title",
            "Writing html/index.html for book(cmpn.book)");

    @Test
    void testCollect() {
        UnresolvedLinks links = new UnresolvedLinks(false, INDEX);
        MESSAGES.forEach(m -> links.message(m, false));

        assertEquals(Map.of("intro.core.release", Set.of(), "service.cm-title", Set.of(),
                "service.namespaces-osgi.service.namespace", Set.of("122.9.5", "122.2")), links.links());
    }

    @Test
    void testChapterWarns() {
        UnresolvedLinks links = new UnresolvedLinks(false, INDEX);
        MESSAGES.forEach(m -> links.message(m, false));
        links.message("XRef to nonexistent id: no.such.id", false);

        assertDoesNotThrow(links::report);
    }

    @Test
    void testBookFails() {
        UnresolvedLinks links = new UnresolvedLinks(true, INDEX);
        assertDoesNotThrow(links::report, "no link, no failure");
        MESSAGES.forEach(m -> links.message(m, false));

        MojoFailureException e = assertThrows(MojoFailureException.class, links::report);
        assertEquals("Links without a target in the book: [intro.core.release, service.cm-title, "
                + "service.namespaces-osgi.service.namespace]", e.getMessage());
    }

    @Test
    void testPublished() throws Exception {
        UnresolvedLinks.Index index = UnresolvedLinks.Index.read(INDEX);

        assertEquals("104 Configuration Admin Service Specification in osgi.cmpn/8.1.0/", index.find("service.cm"));
        assertEquals("135.4 osgi.service Namespace in osgi.cmpn/8.1.0/",
                index.find("service.namespaces-osgi.service.namespace"));
        // the chapter that the id is scoped to
        assertEquals("104 Configuration Admin Service Specification in osgi.cmpn/8.1.0/",
                index.find("service.cm-title"));
        // the longest package
        assertEquals("package org.osgi.service.cm.annotations in osgi.cmpn/8.1.0/",
                index.find("org.osgi.service.cm.annotations.RequireConfigurationAdmin"));
        assertEquals("package org.osgi.service.cm in osgi.cmpn/8.1.0/", index.find("org.osgi.service.cm-version"));
        assertTrue(index.find("framework.lifecycle").endsWith(" in osgi.core/8.0.0/"));
        assertNull(index.find("no.such.id"));
    }
}
