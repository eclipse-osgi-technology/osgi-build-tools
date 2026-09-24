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
 * Checks that the nested types of the nested-types test project are written to
 * javadoc.xml, named relative to their package as Outer.Inner, and that
 * references to them and to their members are linked in the chapter.
 */
@MojoTest
class NestedTypesTest {

    private static final String CHAPTER = "service.nested.html#org.osgi.service.nested.";

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
            String html = Files.readString(spec.resolve("html/service.nested.html"), UTF_8);
            output = new Output(xml, html);
        }
        return output;
    }

    private static final XPath XPATH = XPathFactory.newInstance().newXPath();

    private static List<String> values(Document xml, String expression) throws Exception {
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
    @Basedir("/test-projects/nested-types")
    public void testTypes(OsgiSpecHtmlMojo mojo) throws Exception {
        Document xml = generate(mojo).xml();

        // Nested types follow their enclosing type, the private one is not documented
        assertEquals(List.of("Outer", "Outer.Inner", "Outer.Inner.Deep", "Outer.Kind", "User"),
                values(xml, "//class/@qn"));
        assertEquals(List.of("org.osgi.service.nested.Outer.Inner.Deep"),
                values(xml, "//class[@qn='Outer.Inner.Deep']/@fqn"));
        assertEquals(List.of("ENUM"), values(xml, "//class[@qn='Outer.Kind']/@kind"));
        assertEquals(List.of("A type nested two levels deep."),
                values(xml, "//class[@qn='Outer.Inner.Deep']/lead"));
    }

    @Test
    @InjectMojo(goal = "html")
    @Basedir("/test-projects/nested-types")
    public void testMembers(OsgiSpecHtmlMojo mojo) throws Exception {
        Document xml = generate(mojo).xml();

        assertEquals(List.of("Outer.Inner.Inner(int)", "Outer.Inner.method(Deep)"),
                values(xml, "//class[@qn='Outer.Inner']/method/@qn"));
        assertEquals(List.of("Outer.Inner.FIELD"), values(xml, "//class[@qn='Outer.Inner']/field/@qn"));
        assertEquals(List.of("Outer.Inner.Deep.deep()"), values(xml, "//class[@qn='Outer.Inner.Deep']/method/@qn"));
        assertEquals(List.of("Outer.Kind.ONE", "Outer.Kind.TWO"),
                values(xml, "//class[@qn='Outer.Kind']/field/@qn"));
    }

    @Test
    @InjectMojo(goal = "html")
    @Basedir("/test-projects/nested-types")
    public void testLinks(OsgiSpecHtmlMojo mojo) throws Exception {
        Output out = generate(mojo);
        Document xml = out.xml();

        assertEquals(List.of(
                new Link("#Outer.Inner", "Inner"),
                new Link("#Outer.Inner.Inner(int)", "Inner.Inner(int)"),
                new Link("#Outer.Inner.FIELD", "Inner.FIELD"),
                new Link("#Outer.Inner.method(Deep)", "Inner.method(Inner.Deep)"),
                new Link("#Outer.Inner.Deep", "Inner.Deep"),
                new Link("#Outer.Kind", "Kind"),
                new Link("#Outer.Kind.ONE", "Kind.ONE")),
                links(xml, "//class[@qn='Outer']/description//a"));
        assertEquals(List.of(
                new Link("#Outer.Inner.method(Deep)", "Outer.Inner.method(Outer.Inner.Deep)"),
                new Link("#Outer.Inner.Deep.deep()", "Outer.Inner.Deep.deep()")),
                links(xml, "//class[@qn='User']/a"));
        assertEquals(List.of(new Link("#Outer.Kind", "Outer.Kind")),
                links(xml, "//method[@qn='User.use(Kind)']/param//a"));

        String html = out.html();
        for (String id : List.of("Outer.Inner", "Outer.Inner.Inner-int-", "Outer.Inner.FIELD",
                "Outer.Inner.method-Deep-", "Outer.Inner.Deep", "Outer.Inner.Deep.deep--", "Outer.Kind",
                "Outer.Kind.ONE")) {
            assertTrue(html.contains("href=\"" + CHAPTER + id + "\""), "Link to " + id + " missing");
        }
        assertFalse(html.contains("UNRESOLVED"), "Unresolved link in the chapter");
    }

    /**
     * Every link must match the pqn key of javadoc2docbook.xsl, or the build
     * reports an unresolved href.
     */
    @Test
    @InjectMojo(goal = "html")
    @Basedir("/test-projects/nested-types")
    public void testLinksMatchTargets(OsgiSpecHtmlMojo mojo) throws Exception {
        Document xml = generate(mojo).xml();

        List<String> targets = new ArrayList<>();
        NodeList elements = (NodeList) XPATH.evaluate("//package|//class|//method|//field", xml,
                XPathConstants.NODESET);
        for (int i = 0; i < elements.getLength(); i++) {
            Element e = (Element) elements.item(i);
            targets.add(e.getAttribute("package") + "#" + e.getAttribute("qn"));
        }

        // All links in this project are relative to the package that holds them
        // The lead of Outer repeats its seven description links
        List<String> hrefs = values(xml, "//a/@href");
        assertEquals(17, hrefs.size());
        for (String href : hrefs) {
            assertTrue(targets.contains("org.osgi.service.nested" + href), "No target for " + href);
        }
    }
}
