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
