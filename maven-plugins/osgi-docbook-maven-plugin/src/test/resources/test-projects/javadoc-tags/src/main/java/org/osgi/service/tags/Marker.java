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

import java.lang.annotation.Annotation;

/**
 * An annotation with a literal.
 */
public @interface Marker {
    /**
     * The value of the marker.
     *
     * @return The value.
     */
    String value() default "";

    /**
     * An instance of the marker.
     */
    final class Literal implements Marker {
        @Override
        public String value() {
            return "";
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Marker.class;
        }
    }
}
