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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Tests for {@link Lares40ReadRequest}.
 *
 * @author Michele Pattera - Initial contribution
 */
public class Lares40ReadRequestTest {

    @Test
    public void testAcceptsAnyDiagnosticPayloadTypeWithoutAnAllowlist() {
        assertSingleTypeRequest("zones", Lares40ProtocolConstants.PAYLOAD_TYPE_ZONES);
        assertSingleTypeRequest("partitions", Lares40ProtocolConstants.PAYLOAD_TYPE_PARTITIONS);
        assertSingleTypeRequest("cfg_all", Lares40ProtocolConstants.PAYLOAD_TYPE_CONFIGURATION_ALL);
        assertSingleTypeRequest("usr_all", Lares40ProtocolConstants.PAYLOAD_TYPE_USER_ALL);
        assertSingleTypeRequest("status_all", Lares40ProtocolConstants.PAYLOAD_TYPE_STATUS_ALL);
        assertSingleTypeRequest("all", Lares40ProtocolConstants.PAYLOAD_TYPE_ALL);

        assertSingleTypeRequest("outputs", "OUTPUTS");
        assertSingleTypeRequest("  cfg_future_entity  ", "CFG_FUTURE_ENTITY");
        assertSingleTypeRequest("multi_types", "MULTI_TYPES");
        assertSingleTypeRequest("multy_types", "MULTY_TYPES");
    }

    @Test
    public void testBuildsTheProtocolReadPayload() {
        Lares40ReadRequest request = Lares40ReadRequest.initialInventory();

        JsonObject payload = request.createPayload("login-1");

        assertEquals("login-1", payload.get(Lares40ProtocolConstants.PAYLOAD_LOGIN_ID).getAsString());
        JsonArray types = payload.getAsJsonArray(Lares40ProtocolConstants.PAYLOAD_TYPES);
        assertEquals(
                List.of(Lares40ProtocolConstants.PAYLOAD_TYPE_ZONES, Lares40ProtocolConstants.PAYLOAD_TYPE_PARTITIONS),
                types.asList().stream().map(element -> element.getAsString()).toList());
        JsonArray range = payload.getAsJsonArray(Lares40ProtocolConstants.PAYLOAD_ITEMS_RANGE);
        assertEquals(Lares40ProtocolConstants.PAYLOAD_ITEMS_RANGE_ALL, range.get(0).getAsString());
        assertEquals(Lares40ProtocolConstants.PAYLOAD_ITEMS_RANGE_ALL, range.get(1).getAsString());
    }

    @Test
    public void testRejectsMalformedDiagnosticCommands() {
        assertTrue(Lares40ReadRequest.forDiagnosticCommand("").isEmpty());
        assertTrue(Lares40ReadRequest.forDiagnosticCommand(" \t ").isEmpty());
        assertTrue(Lares40ReadRequest.forDiagnosticCommand("READ ZONES").isEmpty());
        assertTrue(Lares40ReadRequest.forDiagnosticCommand("ZONES 7").isEmpty());
        assertTrue(Lares40ReadRequest.forDiagnosticCommand("ZONES 1 2 3").isEmpty());
    }

    @Test
    public void testBuildsDiagnosticPayloadWithExplicitRangeAndNoTypesList() {
        Lares40ReadRequest request = Lares40ReadRequest.forDiagnosticCommand("  status_zones\t7  10 ").orElseThrow();
        assertEquals("STATUS_ZONES", request.payloadType());
        JsonObject payload = request.createPayload("login-2");
        assertEquals("login-2", payload.get("ID_LOGIN").getAsString());
        assertFalse(payload.has("TYPES"));
        assertEquals(List.of("7", "10"), payload.getAsJsonArray("ID_ITEMS_RANGE").asList().stream()
                .map(element -> element.getAsString()).toList());
    }

    private static void assertSingleTypeRequest(String command, String expectedPayloadType) {
        Lares40ReadRequest request = Lares40ReadRequest.forDiagnosticCommand(command).orElseThrow();
        assertEquals(expectedPayloadType, request.payloadType());
        assertTrue(request.types().isEmpty());
        assertEquals(Lares40ProtocolConstants.PAYLOAD_ITEMS_RANGE_ALL, request.rangeStart());
        assertEquals(Lares40ProtocolConstants.PAYLOAD_ITEMS_RANGE_ALL, request.rangeEnd());
    }
}
