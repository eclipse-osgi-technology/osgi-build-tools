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
 * Checks how the Javadoc tags in the javadoc-tags test project are written to
 * javadoc.xml by the XmlDoclet, and how they are rendered in the chapter. Each
 * class of the test project holds one kind of tag.
 */
@MojoTest
class JavadocTagsTest {

    private static final String CHAPTER = "service.tags.html#org.osgi.service.tags.";

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
            String html = Files.readString(spec.resolve("html/service.tags.html"), UTF_8);
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
    @Basedir("/test-projects/javadoc-tags")
    public void testCode(OsgiSpecHtmlMojo mojo) throws Exception {
        Output out = generate(mojo);
        Document xml = out.xml();

        assertEquals(List.of("a < b"), texts(xml, "//class[@name='CodeTags']/description/code"));
        assertEquals("Inline code in a comparison a < b, and the literal <x>.",
                text(xml, "//class[@name='CodeTags']/description"));
        // The literal is text, not markup
        assertEquals(0.0, XPATH.evaluate("count(//class[@name='CodeTags']/description//x)", xml,
                XPathConstants.NUMBER));
        assertEquals(List.of("true", "false"), texts(xml, "//method[@qn='CodeTags.service()']/description/code"));
        assertEquals(List.of("true"), texts(xml, "//method[@qn='CodeTags.service()']/return/code"));
        assertEquals(List.of("Import-Package: org.osgi.service.tags; version=\"[1.0,2.0)\""),
                texts(xml, "//package[@name='org.osgi.service.tags']/description//code"));

        assertTrue(out.html().contains("<code class=\"code\">a &lt; b</code>"), "Code not rendered");
    }

    @Test
    @InjectMojo(goal = "html")
    @Basedir("/test-projects/javadoc-tags")
    public void testLink(OsgiSpecHtmlMojo mojo) throws Exception {
        Output out = generate(mojo);
        Document xml = out.xml();

        // The nested type is not written to javadoc.xml and the unknown type
        // does not resolve, so neither is linked
        assertEquals(List.of(
                new Link("#LinkTags.NAME", "NAME"),
                new Link("#LinkTags.method(String)", "labelled method"),
                new Link("#LinkTags.LinkTags(int)", "LinkTags(int)"),
                new Link("org.osgi.service.tags.other#Remote", "org.osgi.service.tags.other.Remote"),
                new Link("java.lang#String", "String"),
                new Link("org.osgi.annotation.versioning#Version", "org.osgi.annotation.versioning.Version"),
                new Link("#LinkTags.NAME", "plain name")),
                links(xml, "//class[@name='LinkTags']/description//a"));
        String description = text(xml, "//class[@name='LinkTags']/description");
        assertTrue(description.contains("a nested type Nested, a type"), description);
        assertTrue(description.contains("An unknown type NoSuchType is text."), description);

        String html = out.html();
        for (String id : List.of("LinkTags.NAME", "LinkTags.method-String-", "LinkTags.LinkTags-int-",
                "other.Remote")) {
            assertTrue(html.contains("href=\"" + CHAPTER + id + "\""), "Link to " + id + " missing");
        }
        // A package of another specification is linked to its published page
        assertTrue(html.contains("href=\"https://docs.osgi.org/specification/osgi.core/8.0.0/framework.api.html"
                + "#org.osgi.annotation.versioning.Version\""), "Link to another specification missing");
    }

    @Test
    @InjectMojo(goal = "html")
    @Basedir("/test-projects/javadoc-tags")
    public void testValue(OsgiSpecHtmlMojo mojo) throws Exception {
        Output out = generate(mojo);
        Document xml = out.xml();

        assertEquals("Constant values \"constant\" and 42, and a constant of another type \"name\".",
                text(xml, "//class[@name='ValueTags']/description"));
        assertEquals("The constant, whose value is \"constant\".",
                text(xml, "//field[@qn='ValueTags.CONST']/description"));

        assertTrue(out.html().contains("Constant values \"constant\" and 42"), "Values not rendered");
    }

    @Test
    @InjectMojo(goal = "html")
    @Basedir("/test-projects/javadoc-tags")
    public void testSee(OsgiSpecHtmlMojo mojo) throws Exception {
        Output out = generate(mojo);
        Document xml = out.xml();

        assertEquals(List.of(
                new Link("#SeeTags.field", "field"),
                new Link("#ValueTags.CONST", "CONST"),
                new Link("#SeeTags.method(String)", "method(String)"),
                new Link("org.osgi.service.tags.other#Remote.remote()", "org.osgi.service.tags.other.Remote.remote()"),
                new Link("org.osgi.service.tags.other#org.osgi.service.tags.other", "org.osgi.service.tags.other"),
                new Link("", "OSGi Core Release 8"),
                new Link("https://docs.osgi.org", "OSGi Specifications")),
                links(xml, "//class[@name='SeeTags']/a"));

        String html = out.html();
        for (String id : List.of("SeeTags.field", "ValueTags.CONST", "SeeTags.method-String-",
                "other.Remote.remote--", "other")) {
            assertTrue(html.contains("href=\"" + CHAPTER + id + "\""), "See Also link to " + id + " missing");
        }
        assertTrue(html.contains("<code xmlns=\"http://www.w3.org/1999/xhtml\" class=\"code\">OSGi Core Release 8</code>"),
                "See Also text missing");
    }

    @Test
    @InjectMojo(goal = "html")
    @Basedir("/test-projects/javadoc-tags")
    public void testInheritDoc(OsgiSpecHtmlMojo mojo) throws Exception {
        Document xml = generate(mojo).xml();

        String base = "//method[@qn='InheritBase.lookup(String)']";
        String sub = "//method[@qn='InheritSub.lookup(String)']";
        assertEquals("Look up the value of a Remote key.", text(xml, sub + "/description"));
        // Remote is only imported by InheritBase, so the links only resolve
        // when inherited documentation is resolved in the scope of InheritBase
        for (String part : List.of("description", "return", "throws")) {
            List<Link> links = links(xml, sub + "/" + part + "//a");
            assertFalse(links.isEmpty(), "No links in inherited " + part);
            assertEquals(links(xml, base + "/" + part + "//a"), links, "Inherited " + part);
        }
        assertEquals(List.of(new Link("org.osgi.service.tags.other#Remote.remote()", "Remote.remote()")),
                links(xml, sub + "/return//a"));
        assertEquals("The key, never null.", text(xml, sub + "/param[@name='key']"));
    }

    @Test
    @InjectMojo(goal = "html")
    @Basedir("/test-projects/javadoc-tags")
    public void testBlockTags(OsgiSpecHtmlMojo mojo) throws Exception {
        Output out = generate(mojo);
        Document xml = out.xml();

        assertEquals("1.2", text(xml, "//class[@name='BlockTags']/since"));
        assertEquals("1.0", text(xml, "//class[@name='BlockTags']/version"));
        assertEquals("$Id$", text(xml, "//class[@name='BlockTags']/author"));
        assertEquals("1.1", text(xml, "//method[@qn='BlockTags.replacement()']/since"));
        assertEquals("Use replacement() instead.", text(xml, "//method[@qn='BlockTags.old()']/deprecated"));
        assertEquals(List.of(new Link("#BlockTags.replacement()", "replacement()")),
                links(xml, "//method[@qn='BlockTags.old()']/deprecated//a"));

        String html = out.html();
        assertTrue(html.contains("<label>Since</label><span>1.2</span>"), "Since not rendered");
        assertFalse(html.contains("@since"), "Tag name in @since text");
        assertFalse(html.contains("@deprecated"), "Tag name in @deprecated text");
    }

    @Test
    @InjectMojo(goal = "html")
    @Basedir("/test-projects/javadoc-tags")
    public void testNoRawTags(OsgiSpecHtmlMojo mojo) throws Exception {
        Output out = generate(mojo);
        Document xml = out.xml();

        assertEquals(List.of(), texts(xml, "//text()[contains(., '{@')]"));
        assertFalse(out.html().contains("{@"), "Raw Javadoc tag in the chapter");
        // javadoc2docbook.xsl links unresolved hrefs to UNRESOLVED
        assertFalse(out.html().contains("UNRESOLVED"), "Unresolved link in the chapter");
    }

    /**
     * Every link to a package of the chapter must match the pqn key of
     * javadoc2docbook.xsl, or the build reports an unresolved href.
     */
    @Test
    @InjectMojo(goal = "html")
    @Basedir("/test-projects/javadoc-tags")
    public void testLinksMatchTargets(OsgiSpecHtmlMojo mojo) throws Exception {
        Document xml = generate(mojo).xml();

        List<String> packages = texts(xml, "//package/@name");
        List<String> targets = new ArrayList<>();
        NodeList elements = (NodeList) XPATH.evaluate("//package|//class|//method|//field", xml,
                XPathConstants.NODESET);
        for (int i = 0; i < elements.getLength(); i++) {
            Element e = (Element) elements.item(i);
            targets.add(e.getAttribute("package") + "#" + e.getAttribute("qn"));
        }

        NodeList anchors = (NodeList) XPATH.evaluate("//a[contains(@href, '#')]", xml, XPathConstants.NODESET);
        int checked = 0;
        for (int i = 0; i < anchors.getLength(); i++) {
            Element a = (Element) anchors.item(i);
            String href = a.getAttribute("href");
            // Relative to the package that holds the link, as in the stylesheet
            String key = href.startsWith("#")
                    ? XPATH.evaluate("ancestor::package/@qn", a) + href
                    : href;
            if (packages.contains(key.substring(0, key.indexOf('#')))) {
                assertTrue(targets.contains(key), "No target for " + href);
                checked++;
            }
        }
        assertTrue(checked > 20, "Only " + checked + " links checked");
    }
}
