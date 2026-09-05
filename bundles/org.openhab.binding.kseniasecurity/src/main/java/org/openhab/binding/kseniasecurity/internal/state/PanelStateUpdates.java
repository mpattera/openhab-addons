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

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A group of state updates decoded from one panel notification.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public record PanelStateUpdates(List<ZoneStateUpdate> zoneUpdates, List<PartitionStateUpdate> partitionUpdates) {

    /**
     * Creates a state update group with immutable lists.
     *
     * @param zoneUpdates the zone updates in the notification
     * @param partitionUpdates the partition updates in the notification
     */
    public PanelStateUpdates {
        zoneUpdates = List.copyOf(zoneUpdates);
        partitionUpdates = List.copyOf(partitionUpdates);
    }

    /**
     * Returns an empty state update group.
     *
     * @return an empty group
     */
    public static PanelStateUpdates empty() {
        return new PanelStateUpdates(List.of(), List.of());
    }

    /**
     * Indicates whether the notification did not contain a usable state update.
     *
     * @return {@code true} if both update lists are empty
     */
    public boolean isEmpty() {
        return zoneUpdates.isEmpty() && partitionUpdates.isEmpty();
    }
}
