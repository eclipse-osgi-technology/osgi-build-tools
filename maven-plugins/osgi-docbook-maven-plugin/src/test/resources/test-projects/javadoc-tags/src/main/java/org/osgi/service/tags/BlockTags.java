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
 * Block tags.
 *
 * @since 1.2
 * @version 1.0
 * @author $Id$
 */
public class BlockTags {
    /**
     * An old method.
     *
     * @deprecated Use {@link #replacement()} instead.
     */
    @Deprecated
    public void old() {
    }

    /**
     * The replacement.
     *
     * @since 1.1
     */
    public void replacement() {
    }
}
