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
package org.openhab.binding.kseniasecurity.internal.protocol.lares40;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.openhab.binding.kseniasecurity.internal.state.PanelInventoryUpdates;
import org.openhab.binding.kseniasecurity.internal.state.PartitionMetadataField;
import org.openhab.binding.kseniasecurity.internal.state.PartitionMetadataSnapshot;
import org.openhab.binding.kseniasecurity.internal.state.ZoneMetadataField;
import org.openhab.binding.kseniasecurity.internal.state.ZoneMetadataSnapshot;

import com.google.gson.JsonParser;

/**
 * Tests for {@link Lares40InventoryDecoder}.
 *
 * @author Michele Pattera - Initial contribution
 */
public class Lares40InventoryDecoderTest {

    @Test
    public void testDecodesOnlyKnownZoneAndPartitionMetadata() {
        PanelInventoryUpdates updates = new Lares40InventoryDecoder().decode(JsonParser.parseString("""
                {
                  "RESULT": "OK",
                  "ZONES": [
                    { "ID": "2", "DES": "Entrance", "AN": "1", "PRT": "1" },
                    { "ID": "3" },
                    { "ID": "invalid", "DES": "Ignored" }
                  ],
                  "PARTITIONS": [
                    { "ID": "1", "DES": "Home", "TIN": "30", "TOUT": "45" }
                  ]
                }
                """));

        assertTrue(updates.zonesIncluded());
        assertTrue(updates.partitionsIncluded());
        assertEquals(2, updates.zones().size());
        ZoneMetadataSnapshot zone = Objects.requireNonNull(updates.zones().get(2));
        ZoneMetadataSnapshot zoneWithoutDescription = Objects.requireNonNull(updates.zones().get(3));
        assertEquals("Entrance", zone.get(ZoneMetadataField.DESCRIPTION));
        assertTrue(zoneWithoutDescription.values().isEmpty());
        assertEquals(1, updates.partitions().size());
        PartitionMetadataSnapshot partition = Objects.requireNonNull(updates.partitions().get(1));
        assertEquals("Home", partition.get(PartitionMetadataField.DESCRIPTION));
    }

    @Test
    public void testDistinguishesAnAbsentSectionFromAnEmptySection() {
        Lares40InventoryDecoder decoder = new Lares40InventoryDecoder();

        PanelInventoryUpdates absent = decoder.decode(JsonParser.parseString("{}"));
        assertFalse(absent.zonesIncluded());
        assertFalse(absent.partitionsIncluded());

        PanelInventoryUpdates empty = decoder.decode(JsonParser.parseString("""
                { "ZONES": [], "PARTITIONS": [] }
                """));
        assertTrue(empty.zonesIncluded());
        assertTrue(empty.partitionsIncluded());
        assertTrue(empty.zones().isEmpty());
        assertTrue(empty.partitions().isEmpty());
    }
}
