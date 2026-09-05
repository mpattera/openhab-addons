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

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Static inventory sections decoded from one panel response.
 *
 * <p>
 * The inclusion flags distinguish an absent section from an explicitly empty section, which is important when a
 * successful full inventory confirms that no entity of a type is configured.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public record PanelInventoryUpdates(Map<Integer, ZoneMetadataSnapshot> zones, boolean zonesIncluded,
        Map<Integer, PartitionMetadataSnapshot> partitions, boolean partitionsIncluded) {

    /** Creates an immutable group of inventory sections. */
    public PanelInventoryUpdates {
        zones = Map.copyOf(zones);
        partitions = Map.copyOf(partitions);
    }

    /**
     * Returns an update containing no usable inventory sections.
     *
     * @return the empty update
     */
    public static PanelInventoryUpdates empty() {
        return new PanelInventoryUpdates(Map.of(), false, Map.of(), false);
    }
}
