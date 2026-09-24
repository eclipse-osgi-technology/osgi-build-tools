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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.plugin.testing.ArtifactStubFactory;
import org.apache.maven.project.MavenProject;
import org.eclipse.osgi.technology.buildtools.maven.docbook.OsgiSpecHtmlMojo;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Checks that varargs methods of the varargs test project keep the Type...
 * form of the published anchor ids, and that array and varargs types are
 * rendered with their dimensions once.
 */
@MojoTest
class VarargsTest {

    private static final String CHAPTER = "service.varargs.html#org.osgi.service.varargs.Varargs.";

    record Link(String href, String text) {
    }

    record Output(Document xml, String html) {
    }

    // Generating the chapter is slow, and every test reads the same output
    private static Output output;

    private static synchronized Output generate(OsgiSpecHtmlMojo mojo) throws Exception {
        if (output == null) {
            MavenProject project = MojoExtension.getVariableValueFromObject(mojo, "project");
            project.setArtifact(new ArtifactStubFactory().createArtifact(
                    project.getGroupId(), project.getArtifactId(), project.getVersion()));
            MojoExtension.setVariableValueToObject(mojo, "attachZip", false);
            mojo.execute();

            Path spec = Path.of(project.getBuild().getDirectory(), "spec");
            Document xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(spec.resolve("javadoc/javadoc.xml").toFile());
            String html = Files.readString(spec.resolve("html/service.varargs.html"), UTF_8);
            output = new Output(xml, html);
        }
        return output;
    }

    private static final XPath XPATH = XPathFactory.newInstance().newXPath();

    private static String text(Document xml, String expression) throws Exception {
        return XPATH.evaluate("normalize-space(" + expression + ")", xml);
    }

    private static List<String> texts(Document xml, String expression) throws Exception {
        NodeList nodes = (NodeList) XPATH.evaluate(expression, xml, XPathConstants.NODESET);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            result.add(nodes.item(i).getTextContent().strip());
        }
        return result;
    }

    private static List<Link> links(Document xml, String expression) throws Exception {
        NodeList nodes = (NodeList) XPATH.evaluate(expression, xml, XPathConstants.NODESET);
        List<Link> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element a = (Element) nodes.item(i);
            result.add(new Link(a.getAttribute("href"), a.getTextContent().strip()));
        }
        return result;
    }

    @Test
    @InjectMojo(goal = "html")
    @Basedir("/test-projects/varargs")
    public void testQualifiedNames(OsgiSpecHtmlMojo mojo) throws Exception {
        Document xml = generate(mojo).xml();

        assertEquals(List.of("Varargs.Varargs(Item...)", "Varargs.names(String...)", "Varargs.array(String[])",
                "Varargs.items(int,Item...)", "Varargs.matrix(String[][])", "Varargs.rows(String[]...)",
                "Varargs.lists(List...)"),
                texts(xml, "//class[@name='Varargs']/method/@qn"));
        assertEquals(List.of("(org.osgi.service.varargs.Item...)"),
                texts(xml, "//method[@qn='Varargs.Varargs(Item...)']/@signature"));
    }

    @Test
    @InjectMojo(goal = "html")
    @Basedir("/test-projects/varargs")
    public void testAnchorIds(OsgiSpecHtmlMojo mojo) throws Exception {
        String html = generate(mojo).html();

        // The ids of the published specifications, such as
        // ServiceComponentRuntime.getComponentDescriptionDTOs-Bundle...-
        for (String id : List.of("Varargs-Item...-", "names-String...-", "array-String---", "items-int-Item...-",
                "matrix-String-----", "rows-String--...-", "lists-List...-")) {
            assertTrue(html.contains("id=\"org.osgi.service.varargs.Varargs." + id + "\""), "No id " + id);
        }
        // The chapter links a varargs method by its published id
        assertTrue(html.contains(">names(String...)</a>"), "Chapter link to a varargs method missing");
        assertFalse(html.contains("???"), "Unresolved cross reference in the chapter");
    }

    @Test
    @InjectMojo(goal = "html")
    @Basedir("/test-projects/varargs")
    public void testLinks(OsgiSpecHtmlMojo mojo) throws Exception {
        Output out = generate(mojo);
        Document xml = out.xml();

        assertEquals(List.of(
                new Link("#Varargs.names(String...)", "names(String...)"),
                new Link("#Varargs.array(String[])", "array(String[])"),
                new Link("#Varargs.items(int,Item...)", "items(int, Item...)"),
                new Link("#Varargs.rows(String[]...)", "rows(String[]...)")),
                links(xml, "//class[@name='Varargs']/description//a"));
        assertEquals(List.of(new Link("#Varargs.lists(List...)", "lists(List...)")),
                links(xml, "//class[@name='Varargs']/a"));

        String html = out.html();
        for (String id : List.of("names-String...-", "array-String---", "items-int-Item...-", "rows-String--...-",
                "lists-List...-")) {
            assertTrue(html.contains("href=\"" + CHAPTER + id + "\""), "Link to " + id + " missing");
        }
    }

    @Test
    @InjectMojo(goal = "html")
    @Basedir("/test-projects/varargs")
    public void testTypes(OsgiSpecHtmlMojo mojo) throws Exception {
        Output out = generate(mojo);
        Document xml = out.xml();

        // The dimensions are only in the dimension attribute
        assertEquals(List.of("String", "..."),
                List.of(text(xml, "//method[@qn='Varargs.names(String...)']/parameter/@typeName"),
                        text(xml, "//method[@qn='Varargs.names(String...)']/parameter/@dimension")));
        assertEquals(List.of("String", "[]..."),
                List.of(text(xml, "//method[@qn='Varargs.rows(String[]...)']/parameter/@typeName"),
                        text(xml, "//method[@qn='Varargs.rows(String[]...)']/parameter/@dimension")));
        assertEquals(List.of("String", "[][]"),
                List.of(text(xml, "//method[@qn='Varargs.matrix(String[][])']/parameter/@typeName"),
                        text(xml, "//method[@qn='Varargs.matrix(String[][])']/parameter/@dimension")));
        assertEquals(List.of("String", "[]"),
                List.of(text(xml, "//method[@qn='Varargs.array(String[])']/@typeName"),
                        text(xml, "//method[@qn='Varargs.array(String[])']/@dimension")));
        assertEquals(List.of("String", "[]"),
                List.of(text(xml, "//field[@qn='Varargs.ARRAY']/@typeName"),
                        text(xml, "//field[@qn='Varargs.ARRAY']/@dimension")));

        String text = String.join(" ", out.html().replaceAll("<[^>]+>", " ").replace("&lt;", "<")
                .replace("&gt;", ">").split("\\s+"));
        for (String signature : List.of("public Varargs(Item... items)", "public void names(String... names)",
                "public String[] array(String[] names)", "public void items(int count, Item... items)",
                "public void matrix(String[][] rows)", "public void rows(String[]... rows)",
                "public final void lists(List<String>... lists)", "public static final String[] ARRAY")) {
            assertTrue(text.contains(signature), "Signature " + signature + " missing");
        }
    }
}
