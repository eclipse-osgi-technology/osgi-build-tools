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
 * Constant values {@value #CONST} and {@value #INT_CONST}, and a constant of
 * another type {@value LinkTags#NAME}.
 */
public interface ValueTags {
    /**
     * The constant, whose value is {@value}.
     */
    String CONST = "constant";

    /**
     * An integer constant.
     */
    int INT_CONST = 42;
}
