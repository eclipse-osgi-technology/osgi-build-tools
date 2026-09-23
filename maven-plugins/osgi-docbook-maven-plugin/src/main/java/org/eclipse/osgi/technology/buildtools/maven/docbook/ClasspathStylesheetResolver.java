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

import java.net.URI;
import java.net.URL;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a stylesheet bundled on the plugin classpath, together with the
 * modules and documents it imports, includes or loads.
 * <p>
 * The main stylesheet is given an opaque internal system id so that relative
 * references from it are resolved against {@code /docbook/xsl/}.
 */
final class ClasspathStylesheetResolver implements URIResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ClasspathStylesheetResolver.class);

    private static final String STYLESHEET_ROOT = "/docbook/xsl/";

    private final String internalId;
    private final String stylesheet;

    /**
     * @param internalId the system id given to the main stylesheet, e.g. {@code internal:/fo}
     * @param stylesheet the main stylesheet file name, relative to {@code /docbook/xsl/}
     */
    ClasspathStylesheetResolver(String internalId, String stylesheet) {
        this.internalId = internalId;
        this.stylesheet = stylesheet;
    }

    /**
     * @return the system id of the main stylesheet
     */
    String internalId() {
        return internalId;
    }

    /**
     * @return a source for the main stylesheet
     * @throws TransformerException if the stylesheet cannot be found
     */
    Source stylesheet() throws TransformerException {
        return resolve("", internalId);
    }

    @Override
    public Source resolve(String href, String base) throws TransformerException {
        try {
            if(internalId.equals(base)) {
                LOG.debug("Resolving include {} for style sheet {}", href, internalId);
                URL url;
                String systemId = null;
                if("".equals(href)) {
                    url = getClass().getResource(STYLESHEET_ROOT + stylesheet);
                    systemId = internalId;
                } else {
                    // normalize ".." segments: Class.getResource rejects them
                    url = getClass().getResource(
                        URI.create(STYLESHEET_ROOT + href).normalize().toString());
                }
                if(url == null) {
                    LOG.error("No file {} with base {}", href, base);
                    throw new TransformerException("Failed to find file");
                } else {
                    StreamSource source = new StreamSource(url.openStream());
                    source.setSystemId(systemId != null ? systemId : url.toURI().toASCIIString());
                    return source;
                }
            } else if (base != null && "".equals(href)) {
                // document('') means the module itself; URI.resolve would drop its file name
                LOG.debug("Resolving the style sheet module {} itself", base);
                StreamSource source = new StreamSource(URI.create(base).toURL().openStream());
                source.setSystemId(base);
                return source;
            } else if (base != null && base.startsWith("jar:") && !href.contains(":")) {
                // jar: URIs are opaque, so resolve the entry path textually
                LOG.debug("Resolving include {} inside jar {}", href, base);
                int sep = base.indexOf("!/");
                String entry = base.substring(sep + 1);
                String resolved = URI.create(entry).resolve(href).normalize().toString();
                URL url = getClass().getResource(resolved);
                if (url == null) {
                    LOG.error("No jar resource {} resolved from {} + {}", resolved, base, href);
                    throw new TransformerException("Failed to find file");
                }
                StreamSource source = new StreamSource(url.openStream());
                source.setSystemId(url.toURI().toASCIIString());
                return source;
            } else {
                LOG.debug("Resolving include {} for file {}", href, base);
                URI uri = base == null ? URI.create(href) : URI.create(base).resolve(href);
                StreamSource source = new StreamSource(uri.toURL().openStream());
                source.setSystemId(uri.toASCIIString());
                return source;
            }
        } catch (TransformerException te) {
            throw te;
        } catch (Exception e) {
            LOG.error("Failed to resolve the URI", e);
            throw new TransformerException("Failed to resolve the URI", e);
        }
    }
}
