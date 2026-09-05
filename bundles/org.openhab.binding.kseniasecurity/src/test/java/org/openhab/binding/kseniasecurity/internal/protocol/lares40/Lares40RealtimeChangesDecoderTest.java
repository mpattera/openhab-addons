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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.openhab.binding.kseniasecurity.internal.state.PanelStateUpdates;
import org.openhab.binding.kseniasecurity.internal.state.PartitionStateField;
import org.openhab.binding.kseniasecurity.internal.state.PartitionStateUpdate;
import org.openhab.binding.kseniasecurity.internal.state.ZoneStateField;
import org.openhab.binding.kseniasecurity.internal.state.ZoneStateUpdate;

import com.google.gson.JsonParser;

/**
 * Tests for {@link Lares40RealtimeChangesDecoder}.
 *
 * @author Michele Pattera - Initial contribution
 */
public class Lares40RealtimeChangesDecoderTest {

    @Test
    public void testDecodesChangesIntoSemanticZoneAndPartitionFields() {
        Lares40Command command = new Lares40Command();
        command.payload = JsonParser.parseString("""
                {
                    "receiver-1": {
                    "STATUS_ZONES": [
                      { "ID": "3", "STA": "R", "BYP": "NO", "T": "N", "A": "C", "FM": "F" }
                    ],
                    "STATUS_PARTITIONS": [
                      { "ID": "1", "ARM": "IT", "T": "30", "AST": "AL", "TST": "TM" }
                    ]
                  }
                }
                """);

        PanelStateUpdates updates = new Lares40RealtimeChangesDecoder().decodeChanges(command, "receiver-1");

        assertEquals(1, updates.zoneUpdates().size());
        ZoneStateUpdate zone = updates.zoneUpdates().getFirst();
        assertEquals(3, zone.id());
        assertEquals("REST", zone.values().get(ZoneStateField.REALTIME_STATE));
        assertEquals("NOT_BYPASSED", zone.values().get(ZoneStateField.BYPASS_STATE));
        assertEquals("INACTIVE", zone.values().get(ZoneStateField.TAMPER_STATE));
        assertEquals("ACTIVE", zone.values().get(ZoneStateField.ALARM_STATE));
        assertEquals("INACTIVE", zone.values().get(ZoneStateField.FAULT_STATE));

        assertEquals(1, updates.partitionUpdates().size());
        PartitionStateUpdate partition = updates.partitionUpdates().getFirst();
        assertEquals(1, partition.id());
        assertEquals("ENTRY_DELAY", partition.values().get(PartitionStateField.ARMING_STATE));
        assertEquals("30", partition.values().get(PartitionStateField.DELAY_REMAINING));
        assertEquals("ACTIVE", partition.values().get(PartitionStateField.ALARM_STATE));
        assertEquals("MEMORY", partition.values().get(PartitionStateField.TAMPER_STATE));
    }

    @Test
    public void testDecodesRegisterAcknowledgementWithDirectStatusPayload() {
        Lares40Command command = new Lares40Command();
        command.payload = JsonParser.parseString("""
                {
                  "RESULT": "OK",
                  "STATUS_ZONES": [
                    { "ID": "3", "STA": "A", "BYP": "AUTO", "TM": "F", "AM": "T", "FM": "T" }
                  ],
                  "STATUS_PARTITIONS": [
                    { "ID": "1", "ARM": "IA", "AST": "OK", "TST": "TAM" }
                  ]
                }
                """);

        PanelStateUpdates updates = new Lares40RealtimeChangesDecoder().decodeRegisterAcknowledgement(command);

        ZoneStateUpdate zone = updates.zoneUpdates().getFirst();
        assertEquals("ALARM", zone.values().get(ZoneStateField.REALTIME_STATE));
        assertEquals("AUTO_BYPASSED", zone.values().get(ZoneStateField.BYPASS_STATE));
        assertEquals("INACTIVE", zone.values().get(ZoneStateField.TAMPER_STATE));
        assertEquals("MEMORY", zone.values().get(ZoneStateField.ALARM_STATE));
        assertEquals("ACTIVE", zone.values().get(ZoneStateField.FAULT_STATE));

        PartitionStateUpdate partition = updates.partitionUpdates().getFirst();
        assertEquals("ARMED_IMMEDIATE", partition.values().get(PartitionStateField.ARMING_STATE));
        assertEquals("INACTIVE", partition.values().get(PartitionStateField.ALARM_STATE));
        assertEquals("ACTIVE", partition.values().get(PartitionStateField.TAMPER_STATE));
        assertTrue(partition.clearedFields().contains(PartitionStateField.DELAY_REMAINING));
    }

    @Test
    public void testMapsEveryDocumentedZoneAndPartitionStateCode() {
        Lares40Command command = new Lares40Command();
        command.payload = JsonParser.parseString("""
                {
                  "receiver-1": {
                    "STATUS_ZONES": [
                      { "ID": "1", "STA": "R",  "BYP": "NO",    "T": "N", "A": "N", "FM": "F" },
                      { "ID": "2", "STA": "A",  "BYP": "AUTO",  "T": "C", "A": "C", "FM": "T" },
                      { "ID": "3", "STA": "FM", "BYP": "MAN_I", "T": "M", "A": "M", "FM": "F" },
                      { "ID": "4", "STA": "T",  "BYP": "MAN_M", "T": "N", "A": "N", "FM": "F" },
                      { "ID": "5", "STA": "E",  "BYP": "NO",    "T": "N", "A": "N", "FM": "F" }
                    ],
                    "STATUS_PARTITIONS": [
                      { "ID": "1", "ARM": "D",  "AST": "OK", "TST": "OK" },
                      { "ID": "2", "ARM": "DA", "AST": "AL", "TST": "TAM" },
                      { "ID": "3", "ARM": "IA", "AST": "AM", "TST": "TM" },
                      { "ID": "4", "ARM": "IT", "T": "15", "AST": "OK", "TST": "OK" },
                      { "ID": "5", "ARM": "OT", "T": "20", "AST": "OK", "TST": "OK" }
                    ]
                  }
                }
                """);

        PanelStateUpdates updates = new Lares40RealtimeChangesDecoder().decodeChanges(command, "receiver-1");

        assertEquals(List.of("REST", "ALARM", "FAULT", "TAMPER", "ERROR"), updates.zoneUpdates().stream()
                .map(update -> update.values().get(ZoneStateField.REALTIME_STATE)).toList());
        assertEquals(
                List.of("NOT_BYPASSED", "AUTO_BYPASSED", "MANUAL_BYPASS_DURING_ARMING", "MANUAL_BYPASS",
                        "NOT_BYPASSED"),
                updates.zoneUpdates().stream().map(update -> update.values().get(ZoneStateField.BYPASS_STATE))
                        .toList());
        assertEquals(List.of("INACTIVE", "ACTIVE", "MEMORY", "INACTIVE", "INACTIVE"), updates.zoneUpdates().stream()
                .map(update -> update.values().get(ZoneStateField.TAMPER_STATE)).toList());
        assertEquals(List.of("INACTIVE", "ACTIVE", "MEMORY", "INACTIVE", "INACTIVE"),
                updates.zoneUpdates().stream().map(update -> update.values().get(ZoneStateField.ALARM_STATE)).toList());

        assertEquals(List.of("DISARMED", "ARMED_DELAYED", "ARMED_IMMEDIATE", "ENTRY_DELAY", "EXIT_DELAY"),
                updates.partitionUpdates().stream().map(update -> update.values().get(PartitionStateField.ARMING_STATE))
                        .toList());
        assertEquals(List.of("INACTIVE", "ACTIVE", "MEMORY", "INACTIVE", "INACTIVE"), updates.partitionUpdates()
                .stream().map(update -> update.values().get(PartitionStateField.ALARM_STATE)).toList());
        assertEquals(List.of("INACTIVE", "ACTIVE", "MEMORY", "INACTIVE", "INACTIVE"), updates.partitionUpdates()
                .stream().map(update -> update.values().get(PartitionStateField.TAMPER_STATE)).toList());
    }

    @Test
    public void testKeepsZoneConditionSeparateFromAlarmAndTamperEvents() {
        Lares40Command command = new Lares40Command();
        command.payload = JsonParser.parseString("""
                {
                  "receiver-1": {
                    "STATUS_ZONES": [
                      { "ID": "1", "STA": "A", "A": "N", "T": "N" },
                      { "ID": "2", "STA": "T", "A": "N", "T": "N" }
                    ]
                  }
                }
                """);

        PanelStateUpdates updates = new Lares40RealtimeChangesDecoder().decodeChanges(command, "receiver-1");

        ZoneStateUpdate alarmCondition = updates.zoneUpdates().getFirst();
        assertEquals("ALARM", alarmCondition.values().get(ZoneStateField.REALTIME_STATE));
        assertEquals("INACTIVE", alarmCondition.values().get(ZoneStateField.ALARM_STATE));

        ZoneStateUpdate tamperCondition = updates.zoneUpdates().get(1);
        assertEquals("TAMPER", tamperCondition.values().get(ZoneStateField.REALTIME_STATE));
        assertEquals("INACTIVE", tamperCondition.values().get(ZoneStateField.TAMPER_STATE));
    }

    @Test
    public void testUnknownWireValuesBecomeUnknownSemanticValues() {
        Lares40Command command = new Lares40Command();
        command.payload = JsonParser.parseString("""
                {
                  "receiver-1": {
                    "STATUS_ZONES": [
                      { "ID": "3", "STA": "?", "BYP": "?", "T": "?", "A": "?", "FM": "?" }
                    ],
                    "STATUS_PARTITIONS": [
                      { "ID": "1", "ARM": "?", "AST": "?", "TST": "?" }
                    ]
                  }
                }
                """);

        PanelStateUpdates updates = new Lares40RealtimeChangesDecoder().decodeChanges(command, "receiver-1");

        ZoneStateUpdate zone = updates.zoneUpdates().getFirst();
        assertEquals("UNKNOWN", zone.values().get(ZoneStateField.REALTIME_STATE));
        assertEquals("UNKNOWN", zone.values().get(ZoneStateField.BYPASS_STATE));
        assertEquals("UNKNOWN", zone.values().get(ZoneStateField.TAMPER_STATE));
        assertEquals("UNKNOWN", zone.values().get(ZoneStateField.ALARM_STATE));
        assertEquals("UNKNOWN", zone.values().get(ZoneStateField.FAULT_STATE));

        PartitionStateUpdate partition = updates.partitionUpdates().getFirst();
        assertEquals("UNKNOWN", partition.values().get(PartitionStateField.ARMING_STATE));
        assertEquals("UNKNOWN", partition.values().get(PartitionStateField.ALARM_STATE));
        assertEquals("UNKNOWN", partition.values().get(PartitionStateField.TAMPER_STATE));
    }

    @Test
    public void testIgnoresOtherReceiversAndInvalidEntryIds() {
        Lares40Command command = new Lares40Command();
        command.payload = JsonParser.parseString("""
                {
                  "receiver-1": {
                    "STATUS_ZONES": [
                      { "ID": "not-a-number", "STA": "R" },
                      { "ID": "0", "STA": "R" },
                      { "STA": "R" }
                    ]
                  }
                }
                """);

        Lares40RealtimeChangesDecoder decoder = new Lares40RealtimeChangesDecoder();
        assertTrue(decoder.decodeChanges(command, "other-receiver").isEmpty());
        assertTrue(decoder.decodeChanges(command, "receiver-1").isEmpty());
    }
}
