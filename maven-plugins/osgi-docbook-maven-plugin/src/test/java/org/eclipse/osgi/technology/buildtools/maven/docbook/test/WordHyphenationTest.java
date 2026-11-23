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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.io.ObjectInputStream;

import org.apache.fop.hyphenation.HyphenationTree;
import org.eclipse.osgi.technology.buildtools.maven.docbook.WordHyphenation;
import org.junit.jupiter.api.Test;

public class WordHyphenationTest {

    @Test
    public void testNamesAreNotHyphenated() throws Exception {
        HyphenationTree en;
        try (InputStream in = getClass().getResourceAsStream("/fop/hyph/en.hyp");
                ObjectInputStream ois = new ObjectInputStream(in)) {
            en = (HyphenationTree) ois.readObject();
        }
        WordHyphenation hyphenation = new WordHyphenation(en);

        // a word is hyphenated as before
        assertNotNull(hyphenation.hyphenate("framework", 2, 2));
        assertNotNull(hyphenation.hyphenate("ServicePermission", 2, 2));
        // FOP 2 hyphenates the parts of a name, FOP 1.1 did not
        assertNotNull(en.hyphenate("org.osgi.framework", 2, 2));
        assertNull(hyphenation.hyphenate("org.osgi.framework", 2, 2));
        assertNull(hyphenation.hyphenate("org.osgi.framework.system.packages", 2, 2));
        assertNull(hyphenation.hyphenate("FRAMEWORK_SYSTEMPACKAGES", 2, 2));
    }
}
