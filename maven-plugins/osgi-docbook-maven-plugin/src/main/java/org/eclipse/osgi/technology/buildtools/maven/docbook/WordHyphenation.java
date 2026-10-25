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
