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

package org.osgi.service.varargs;

import java.util.List;

/**
 * Methods with varargs and array parameters: {@link #names(String...)},
 * {@link #array(String[])}, {@link #items(int, Item...)} and
 * {@link #rows(String[]...)}.
 *
 * @see #lists(List...)
 */
public class Varargs {
    /**
     * An array field.
     */
    public static final String[] ARRAY = {};

    /**
     * Create with varargs.
     *
     * @param items The items.
     */
    public Varargs(Item... items) {
    }

    /**
     * A varargs method.
     *
     * @param names The names.
     */
    public void names(String... names) {
    }

    /**
     * An array method, which is not varargs.
     *
     * @param names The names.
     * @return The names.
     */
    public String[] array(String[] names) {
        return names;
    }

    /**
     * A varargs method with a leading parameter.
     *
     * @param count The count.
     * @param items The items.
     */
    public void items(int count, Item... items) {
    }

    /**
     * A two dimensional array method.
     *
     * @param rows The rows.
     */
    public void matrix(String[][] rows) {
    }

    /**
     * A varargs method of arrays.
     *
     * @param rows The rows.
     */
    public void rows(String[]... rows) {
    }

    /**
     * A varargs method with a generic component type.
     *
     * @param lists The lists.
     */
    @SafeVarargs
    public final void lists(List<String>... lists) {
    }
}
