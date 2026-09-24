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
 * A type that inherits documentation.
 */
public class InheritSub implements InheritBase {
    /**
     * {@inheritDoc}
     */
    @Override
    public int lookup(String key) throws IllegalArgumentException {
        return 0;
    }
}
