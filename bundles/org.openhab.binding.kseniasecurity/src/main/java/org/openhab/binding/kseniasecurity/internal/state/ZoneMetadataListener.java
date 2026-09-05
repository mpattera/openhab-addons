/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.kseniasecurity.internal.state;

import org.eclipse.jdt.annotation.NonNullByDefault;

/** Receives static metadata snapshots for a zone. */
@FunctionalInterface
@NonNullByDefault
public interface ZoneMetadataListener {

    /**
     * Called when the panel inventory contains metadata for the registered zone.
     *
     * @param metadata the complete metadata snapshot
     */
    void onZoneMetadataUpdated(ZoneMetadataSnapshot metadata);

    /**
     * Called when a successful inventory no longer contains the registered zone or the panel connection ends.
     */
    default void onZoneMetadataInvalidated() {
    }
}
