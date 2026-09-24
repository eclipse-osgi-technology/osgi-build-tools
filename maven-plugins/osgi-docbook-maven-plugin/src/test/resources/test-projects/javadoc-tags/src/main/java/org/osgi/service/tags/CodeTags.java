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
 * Inline code in a comparison {@code a < b}, and the literal {@literal <x>}.
 */
public interface CodeTags {
    /**
     * Returns {@code true} if the request should be serviced, {@code false} if
     * the request should not be serviced.
     *
     * @return {@code true} to service the request
     */
    boolean service();
}
