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

// Only imported here, so references to it resolve in the scope of this type
import org.osgi.service.tags.other.Remote;

/**
 * A type whose documentation is inherited.
 */
public interface InheritBase {
    /**
     * Look up the value of a {@link Remote} key.
     *
     * @param key The key, never {@code null}.
     * @return The value of {@link Remote#remote()}.
     * @throws IllegalArgumentException If the key is not a {@link Remote}
     *         key.
     */
    int lookup(String key) throws IllegalArgumentException;
}
