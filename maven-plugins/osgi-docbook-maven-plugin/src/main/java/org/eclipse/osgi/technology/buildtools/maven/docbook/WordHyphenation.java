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
package org.eclipse.osgi.technology.buildtools.maven.docbook;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.hyphenation.Hyphenation;
import org.apache.fop.hyphenation.HyphenationTree;
import org.apache.fop.hyphenation.HyphenationTreeCache;
import org.apache.fop.hyphenation.Hyphenator;

/**
 * Hyphenation as FOP 1.1 did it, with which the specifications were published:
 * a word with a non-letter between letters, such as a package or class name
 * (org.osgi.framework, ServicePermission[...]), is not hyphenated. FOP 2
 * splits such a word at its non-letters and hyphenates every part
 * (org.osgi.frame-work); the char[] method of {@link HyphenationTree} still
 * has the old behaviour.
 * <p>
 * FOP takes the hyphenation properties from the block only, so hyphenate on
 * an inline cannot switch the hyphenation of code off.
 */
public final class WordHyphenation extends HyphenationTree {

    private static final long serialVersionUID = 1L;

    private final HyphenationTree patterns;

    /**
     * @param patterns the hyphenation patterns of a language
     */
    public WordHyphenation(HyphenationTree patterns) {
        this.patterns = patterns;
    }

    @Override
    public Hyphenation hyphenate(String word, int remainCharCount, int pushCharCount) {
        char[] w = word.toCharArray();
        return patterns.hyphenate(w, 0, w.length, remainCharCount, pushCharCount);
    }

    /**
     * Puts the hyphenation of a language into the hyphenation cache of FOP,
     * where the layout looks it up.
     *
     * @param userAgent the user agent of the rendering
     * @param language the language of the book
     */
    static void install(FOUserAgent userAgent, String language) {
        HyphenationTree patterns = Hyphenator.getHyphenationTree(language, "none",
                userAgent.getHyphenationResourceResolver(), userAgent.getHyphenationPatternNames(), userAgent);
        if (patterns != null && !(patterns instanceof WordHyphenation)) {
            userAgent.getHyphenationTreeCache().cache(HyphenationTreeCache.constructLlccKey(language, "none"),
                    new WordHyphenation(patterns));
        }
    }
}
