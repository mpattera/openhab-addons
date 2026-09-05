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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link KseniaPanelInventory}.
 *
 * @author Michele Pattera - Initial contribution
 */
public class KseniaPanelInventoryTest {

    @Test
    public void testReplaysMetadataToLateListeners() {
        KseniaPanelInventory inventory = new KseniaPanelInventory();
        inventory.apply(new PanelInventoryUpdates(
                Map.of(2, new ZoneMetadataSnapshot(2, Map.of(ZoneMetadataField.DESCRIPTION, "Entrance"))), true,
                Map.of(1, new PartitionMetadataSnapshot(1, Map.of(PartitionMetadataField.DESCRIPTION, "Home"))), true));

        List<ZoneMetadataSnapshot> zoneUpdates = new ArrayList<>();
        List<PartitionMetadataSnapshot> partitionUpdates = new ArrayList<>();
        inventory.addZoneListener(2, zoneUpdates::add);
        inventory.addPartitionListener(1, partitionUpdates::add);

        assertTrue(inventory.isComplete());
        assertEquals("Entrance", zoneUpdates.getFirst().get(ZoneMetadataField.DESCRIPTION));
        assertEquals("Home", partitionUpdates.getFirst().get(PartitionMetadataField.DESCRIPTION));
    }

    @Test
    public void testSuccessfulReplacementInvalidatesADeletedMetadataEntry() {
        KseniaPanelInventory inventory = new KseniaPanelInventory();
        AtomicInteger invalidations = new AtomicInteger();
        inventory.addZoneListener(2, new ZoneMetadataListener() {
            @Override
            public void onZoneMetadataUpdated(ZoneMetadataSnapshot metadata) {
            }

            @Override
            public void onZoneMetadataInvalidated() {
                invalidations.incrementAndGet();
            }
        });

        inventory.apply(new PanelInventoryUpdates(
                Map.of(2, new ZoneMetadataSnapshot(2, Map.of(ZoneMetadataField.DESCRIPTION, "Entrance"))), true,
                Map.of(), false));
        inventory.apply(new PanelInventoryUpdates(Map.of(), true, Map.of(), false));

        assertEquals(1, invalidations.get());
        assertFalse(inventory.isComplete());
    }

    @Test
    public void testConnectionInvalidationClearsMetadataButRetainsListeners() {
        KseniaPanelInventory inventory = new KseniaPanelInventory();
        AtomicInteger invalidations = new AtomicInteger();
        List<ZoneMetadataSnapshot> updates = new ArrayList<>();
        inventory.addZoneListener(2, new ZoneMetadataListener() {
            @Override
            public void onZoneMetadataUpdated(ZoneMetadataSnapshot metadata) {
                updates.add(metadata);
            }

            @Override
            public void onZoneMetadataInvalidated() {
                invalidations.incrementAndGet();
            }
        });

        inventory.apply(new PanelInventoryUpdates(
                Map.of(2, new ZoneMetadataSnapshot(2, Map.of(ZoneMetadataField.DESCRIPTION, "Entrance"))), true,
                Map.of(), true));
        inventory.clearSnapshots();
        assertFalse(inventory.isComplete());
        inventory.apply(new PanelInventoryUpdates(
                Map.of(2, new ZoneMetadataSnapshot(2, Map.of(ZoneMetadataField.DESCRIPTION, "New entrance"))), true,
                Map.of(), true));

        assertTrue(inventory.isComplete());
        assertEquals(1, invalidations.get());
        assertEquals(2, updates.size());
        assertEquals("New entrance", updates.getLast().get(ZoneMetadataField.DESCRIPTION));
    }
}
