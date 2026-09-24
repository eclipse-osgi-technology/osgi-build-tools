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
 * A type with nested types: {@link Inner}, {@link Inner#Inner(int)},
 * {@link Inner#FIELD}, {@link Inner#method(Inner.Deep)}, {@link Inner.Deep},
 * {@link Kind} and {@link Kind#ONE}.
 */
public class Outer {
    /**
     * A nested class.
     */
    public static class Inner {
        /**
         * A field.
         */
        public static final String FIELD = "field";

        /**
         * Create a nested class.
         *
         * @param value Ignored.
         */
        public Inner(int value) {
        }

        /**
         * A method with a nested type parameter.
         *
         * @param deep Ignored.
         */
        public void method(Deep deep) {
        }

        /**
         * A type nested two levels deep.
         */
        public interface Deep {
            /**
             * A method.
             */
            void deep();
        }
    }

    /**
     * A nested enum.
     */
    public enum Kind {
        /**
         * The first kind.
         */
        ONE,

        /**
         * The second kind.
         */
        TWO
    }

    /**
     * A private nested type, which is not documented.
     */
    @SuppressWarnings("unused")
    private static class Hidden {
    }
}
