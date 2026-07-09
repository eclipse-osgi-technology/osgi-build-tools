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

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.eclipse.osgi.technology.buildtools.maven.docbook.OsgiSpecPdfMojo;
import org.junit.jupiter.api.Test;
@MojoTest
class MyMojoTest {

    @Test
    @InjectMojo(goal = "pdf")
    @Basedir("/test-projects/simple")
    public void testSomething(OsgiSpecPdfMojo mojo) throws Exception {
        mojo.execute();
    }
}