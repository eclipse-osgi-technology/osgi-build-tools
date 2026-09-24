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
 * References in see tags.
 *
 * @see #field
 * @see org.osgi.service.tags.ValueTags#CONST CONST
 * @see #method(String)
 * @see org.osgi.service.tags.other.Remote#remote()
 * @see org.osgi.service.tags.other
 * @see "OSGi Core Release 8"
 * @see <a href="https://docs.osgi.org">OSGi Specifications</a>
 */
public class SeeTags {
    /**
     * A field.
     */
    public String field;

    /**
     * A method.
     *
     * @param argument Ignored.
     */
    public void method(String argument) {
    }
}
