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

import java.io.IOException;

/**
 * A type whose method has no comment of its own.
 */
public class InheritPlain implements InheritBase {
    @Override
    public int lookup(String key) throws IllegalArgumentException {
        return 0;
    }

    @Override
    public void read() throws IOException {
    }
}
