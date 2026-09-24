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

package org.osgi.service.tags;

/**
 * Links to a field {@link #NAME}, a method with a label
 * {@link #method(String) labelled method}, a constructor
 * {@link #LinkTags(int)}, a nested type {@link Nested}, a type in another
 * package of the chapter {@link org.osgi.service.tags.other.Remote}, a JDK type
 * {@link String}, a type in another specification
 * {@link org.osgi.annotation.versioning.Version}, and a plain link
 * {@linkplain #NAME plain name}. An unknown type {@link NoSuchType} is text.
 */
public class LinkTags {
    /**
     * The name property.
     */
    public static final String NAME = "name";

    /**
     * Create the example.
     *
     * @param value Ignored.
     */
    public LinkTags(int value) {
    }

    /**
     * A method.
     *
     * @param argument Ignored.
     */
    public void method(String argument) {
    }

    /**
     * A nested type, which is not written to the chapter.
     */
    public static class Nested {
    }
}
