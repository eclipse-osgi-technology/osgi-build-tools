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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The staging of a complete book from DocBook archives, zipped as in the Maven
 * repositories or unpacked as checked in with the book.
 */
class BookStagingTest {

    @TempDir
    Path tmp;

    @Test
    void testZipAndDirectory() throws Exception {
        Path zip = tmp.resolve("one-docbook.zip");
        try (OutputStream os = Files.newOutputStream(zip); ZipOutputStream zos = new ZipOutputStream(os)) {
            entry(zos, "META-INF/docbook.properties", "chapters=one\n");
            entry(zos, "chapters/one/one.xml", "<chapter/>");
        }
        Path dir = tmp.resolve("two");
        write(dir.resolve("META-INF/docbook.properties"), "packages=org.osgi.two\n");
        write(dir.resolve("javadoc/org.osgi.two.xml"), "<section/>");
        Path book = book("chapters/one/one.xml", "javadoc/org.osgi.two.xml");

        Path staged = new BookStaging(tmp.resolve("staged"))
                .stage(book, List.of(new BookStaging.Source("one", zip), new BookStaging.Source("two", dir)));

        assertEquals(tmp.resolve("staged/book.xml"), staged);
        assertTrue(Files.isRegularFile(tmp.resolve("staged/chapters/one/one.xml")));
        assertTrue(Files.isRegularFile(tmp.resolve("staged/javadoc/org.osgi.two.xml")));
        assertFalse(Files.exists(tmp.resolve("staged/META-INF")), "the descriptions of the archives are staged");
    }

    @Test
    void testMissingInclude() throws Exception {
        Path book = book("chapters/three/three.xml");

        MojoExecutionException e = assertThrows(MojoExecutionException.class,
                () -> new BookStaging(tmp.resolve("staged")).stage(book, List.of()));
        assertTrue(e.getMessage().contains("book.xml includes chapters/three/three.xml"), e.getMessage());
    }

    private Path book(String... includes) throws Exception {
        StringBuilder sb = new StringBuilder("<book xmlns:xi=\"http://www.w3.org/2001/XInclude\">");
        for (String include : includes) {
            sb.append("<xi:include href=\"").append(include).append("\"/>");
        }
        Path book = tmp.resolve("book/book.xml");
        write(book, sb.append("</book>").toString());
        return book;
    }

    private static void entry(ZipOutputStream zos, String name, String content) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(UTF_8));
        zos.closeEntry();
    }

    private static void write(Path p, String content) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, UTF_8);
    }
}
