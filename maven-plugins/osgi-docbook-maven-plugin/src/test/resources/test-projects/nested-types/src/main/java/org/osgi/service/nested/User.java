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

package org.osgi.service.nested;

/**
 * A type that refers to nested types of another type.
 *
 * @see Outer.Inner#method(Outer.Inner.Deep)
 * @see Outer.Inner.Deep#deep()
 */
public class User {
    /**
     * Use a nested type.
     *
     * @param kind The {@link Outer.Kind} to use.
     */
    public void use(Outer.Kind kind) {
    }
}
