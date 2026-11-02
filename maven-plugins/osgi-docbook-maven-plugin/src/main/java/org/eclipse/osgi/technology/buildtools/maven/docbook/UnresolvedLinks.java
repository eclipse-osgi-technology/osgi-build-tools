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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Transformer;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.saxon.jaxp.TransformerImpl;
import net.sf.saxon.s9api.Message;

/**
 * Receives the {@code xsl:message} output of the DocBook stylesheets and
 * collects the links whose target is not in the book. A chapter, or the
 * chapters of one repository, link to other specifications, which only the
 * complete Core and Compendium books contain. Such a link is a warning when
 * its target is in the index of the published specifications. In a complete
 * book every link must resolve, so there it fails the build.
 */
final class UnresolvedLinks implements Consumer<Message> {

    /** The bundled index of the chapters and packages published on docs.osgi.org */
    static final String INDEX = "/docbook/links/osgi-spec-index.xml";

    private static final String INDEX_NS = "urn:org.osgi:docbook:links";

    /** The label of the section that the OSGi stylesheets put in front of a message */
    private static final Pattern SECTION = Pattern.compile("^\\[(?<section>[^\\]]*)\\]\\s*");

    /** The messages of the DocBook and OSGi stylesheets for a link whose target is not in the book */
    private static final List<Pattern> UNRESOLVED = List.of(
            Pattern.compile("Error: no ID for constraint linkend: (?<id>\\S+)\\."),
            Pattern.compile("XLink to nonexistent id: #?(?<id>\\S+)"),
            Pattern.compile("XRef to nonexistent id: (?<id>\\S+)"),
            Pattern.compile("ERROR: xref linking to #?(?<id>\\S+) has no generated link text\\."),
            Pattern.compile("\\d*Endterm points to nonexistent ID: (?<id>\\S+)"));

    private static final Pattern PROBLEM = Pattern.compile("(?i)\\b(error|warning)\\b");

    private static final Logger LOG = LoggerFactory.getLogger(UnresolvedLinks.class);

    private final boolean fail;

    private final URL index;

    /** The ids that are not in the book, with the labels of the sections linking to them */
    private final Map<String, Set<String>> links = new TreeMap<>();

    /**
     * @param fail true to fail the build on a link whose target is not in the
     *            book
     * @param index the index of the published specifications
     */
    UnresolvedLinks(boolean fail, URL index) {
        this.fail = fail;
        this.index = index;
    }

    /**
     * Receive the messages of a transformation
     *
     * @param transformer a Saxon transformer
     */
    void install(Transformer transformer) {
        if (transformer instanceof TransformerImpl saxon) {
            saxon.getUnderlyingXsltTransformer().setMessageHandler(this);
        }
    }

    @Override
    public void accept(Message message) {
        message(message.getStringValue(), message.isTerminate());
    }

    /**
     * Collect a link whose target is not in the book, log any other message
     *
     * @param text the message
     * @param terminate true if the message ends the transformation
     */
    void message(String text, boolean terminate) {
        String message = text.strip();
        String section = null;
        String rest = message;
        Matcher s = SECTION.matcher(message);
        if (s.find()) {
            section = s.group("section").strip();
            rest = message.substring(s.end());
        }
        for (Pattern p : UNRESOLVED) {
            Matcher m = p.matcher(rest);
            if (m.matches()) {
                Set<String> sections = links.computeIfAbsent(m.group("id"), id -> new LinkedHashSet<>());
                if (section != null && !section.isEmpty()) {
                    sections.add(section);
                }
                return;
            }
        }
        if (terminate) {
            LOG.error("{}", message);
        } else if (PROBLEM.matcher(message).find()) {
            LOG.warn("{}", message);
        } else {
            LOG.info("{}", message);
        }
    }

    /**
     * @return the ids that are not in the book, with the labels of the
     *         sections linking to them
     */
    Map<String, Set<String>> links() {
        return links;
    }

    /**
     * Log the links whose target is not in the book: once per id
     *
     * @throws MojoExecutionException if the index cannot be read
     * @throws MojoFailureException if there are such links and they fail the
     *             build
     */
    void report() throws MojoExecutionException, MojoFailureException {
        if (links.isEmpty()) {
            return;
        }
        if (fail) {
            links.forEach((id, sections) -> LOG.error("Link to {}{} has no target in the book", id, from(sections)));
            throw new MojoFailureException("Links without a target in the book: " + links.keySet());
        }
        Index published = Index.read(index);
        links.forEach((id, sections) -> {
            String target = published.find(id);
            if (target != null) {
                LOG.warn("Link to {}{} is to another specification, {}; it resolves in the complete book", id,
                        from(sections), target);
            } else {
                LOG.error("Link to {}{} has no target, neither in this specification nor in a published one", id,
                        from(sections));
            }
        });
    }

    private static String from(Set<String> sections) {
        return sections.isEmpty() ? "" : " from " + String.join(", ", sections);
    }

    /**
     * The chapters, sections and packages of the published specifications,
     * see {@code links/osgi-spec-index.xml}
     *
     * @param targets a description of each chapter and section, by id
     * @param packages a description of each package, by name
     */
    record Index(Map<String, String> targets, Map<String, String> packages) {

        static Index read(URL url) throws MojoExecutionException {
            Map<String, String> targets = new HashMap<>();
            Map<String, String> packages = new HashMap<>();
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            try (InputStream is = url.openStream()) {
                XMLStreamReader r = factory.createXMLStreamReader(is);
                String book = "";
                while (r.hasNext()) {
                    if (r.next() != XMLStreamReader.START_ELEMENT || !INDEX_NS.equals(r.getNamespaceURI())) {
                        continue;
                    }
                    switch (r.getLocalName()) {
                        case "book" -> book = attribute(r, "path");
                        case "target" -> targets.putIfAbsent(attribute(r, "id"),
                                (attribute(r, "label") + " " + attribute(r, "title")).strip() + " in " + book);
                        case "package" -> packages.putIfAbsent(attribute(r, "name"),
                                "package " + attribute(r, "name") + " in " + book);
                        default -> {
                            // the root
                        }
                    }
                }
                r.close();
            } catch (IOException | XMLStreamException e) {
                throw new MojoExecutionException("Failed to read the link index " + url, e);
            }
            return new Index(targets, packages);
        }

        private static String attribute(XMLStreamReader r, String name) {
            String value = r.getAttributeValue(null, name);
            return value == null ? "" : value;
        }

        /**
         * As {@code osgi:external-target} of {@code custom-html-links.xsl}: an
         * indexed chapter or section, the package holding a javadoc id (such
         * as {@code org.osgi.service.event-version}), or else the chapter that
         * the id is scoped to ({@code chapter-local})
         *
         * @param id a link target
         * @return the description of the published target, or null
         */
        String find(String id) {
            String target = targets.get(id);
            if (target == null && id.startsWith("org.osgi.")) {
                // the longest match, so org.osgi.service.cm.annotations wins over org.osgi.service.cm
                target = packages.keySet().stream()
                        .filter(p -> id.equals(p) || id.startsWith(p + ".") || id.startsWith(p + "-"))
                        .max((a, b) -> Integer.compare(a.length(), b.length())).map(packages::get).orElse(null);
            }
            int dash = id.indexOf('-');
            if (target == null && dash > 0) {
                target = targets.get(id.substring(0, dash));
            }
            return target;
        }
    }
}
