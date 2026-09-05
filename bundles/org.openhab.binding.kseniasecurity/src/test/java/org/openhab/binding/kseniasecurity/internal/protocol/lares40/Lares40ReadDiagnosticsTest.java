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

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

/**
 * Tests for {@link Lares40ReadDiagnostics}.
 *
 * @author Michele Pattera - Initial contribution
 */
public class Lares40ReadDiagnosticsTest {

    @Test
    public void testFormatsPayloadAndRedactsOnlyPinValues() {
        String summary = Lares40ReadDiagnostics.summarize(JsonParser.parseString("""
                {
                  "RESULT": "OK",
                  "RESULT_DETAIL": "READ_OK",
                  "ZONES": [
                    { "ID": "3", "DES": "Garage", "PIN": "123456" },
                    { "ID": "5", "DES": "Kitchen", "EMAIL": "test@example.invalid" }
                  ],
                  "ACCOUNT": { "PWD": "secret", "TOKEN": "token-value", "PIN": "654321" },
                  "ARBITRARY": "private value"
                }
                """));

        assertTrue(summary.contains("\"RESULT\":\"OK\""));
        assertTrue(summary.contains("\"RESULT_DETAIL\":\"READ_OK\""));
        assertTrue(summary.contains("\"DES\":\"Garage\""));
        assertTrue(summary.contains("\"DES\":\"Kitchen\""));
        assertTrue(summary.contains("\"EMAIL\":\"test@example.invalid\""));
        assertTrue(summary.contains("\"PWD\":\"secret\""));
        assertTrue(summary.contains("\"TOKEN\":\"token-value\""));
        assertTrue(summary.contains("\"ARBITRARY\":\"private value\""));
        assertTrue(summary.contains("\"PIN\":\"<redacted>\""));
        assertFalse(summary.contains("123456"));
        assertFalse(summary.contains("654321"));
    }

    @Test
    public void testFormatsMissingAndNonObjectPayloads() {
        assertTrue(Lares40ReadDiagnostics.summarize(null).contains("no payload"));
        assertEquals("[]", Lares40ReadDiagnostics.summarize(JsonParser.parseString("[]")));
        assertEquals("\"value\"", Lares40ReadDiagnostics.summarize(JsonParser.parseString("\"value\"")));
    }

    @Test
    public void testIncludesAllNonPinResponseValues() {
        String summary = Lares40ReadDiagnostics.summarize(JsonParser.parseString("""
                {
                  "ID_LOGIN": "session-id",
                  "SYSTEM_LANG": "IT",
                  "SESSION_STATE": "ACTIVE",
                  "FREEZE_STATE": "OFF",
                  "VER_LITE": {
                    "FW": "1.2.3",
                    "WS": "4.5.6",
                    "SERIAL": "private-serial"
                  },
                  "UNKNOWN_VALUE": "private value",
                  "PIN": "123456",
                  "NESTED": { "PIN": "654321", "VALUE": "shown" }
                }
                """));

        assertTrue(summary.contains("\"ID_LOGIN\":\"session-id\""));
        assertTrue(summary.contains("\"SYSTEM_LANG\":\"IT\""));
        assertTrue(summary.contains("\"SESSION_STATE\":\"ACTIVE\""));
        assertTrue(summary.contains("\"FREEZE_STATE\":\"OFF\""));
        assertTrue(summary.contains("\"FW\":\"1.2.3\""));
        assertTrue(summary.contains("\"WS\":\"4.5.6\""));
        assertTrue(summary.contains("\"SERIAL\":\"private-serial\""));
        assertTrue(summary.contains("\"UNKNOWN_VALUE\":\"private value\""));
        assertTrue(summary.contains("\"VALUE\":\"shown\""));
        assertTrue(summary.contains("\"PIN\":\"<redacted>\""));
        assertFalse(summary.contains("123456"));
        assertFalse(summary.contains("654321"));
    }

    @Test
    public void testRedactsExplicitFieldsWithoutChangingTheOriginalPayload() {
        var payload = JsonParser.parseString("""
                {
                  "PIN": "123456",
                  "4DIGIT_PIN": "T",
                  "PIN_OPTION": "visible",
                  "NESTED": [ { "pin": "654321" } ]
                }
                """);

        String summary = Lares40ReadDiagnostics.summarize(payload);

        assertTrue(summary.contains("\"PIN\":\"<redacted>\""));
        assertTrue(summary.contains("\"pin\":\"<redacted>\""));
        assertTrue(summary.contains("\"4DIGIT_PIN\":\"T\""));
        assertTrue(summary.contains("\"PIN_OPTION\":\"visible\""));
        assertFalse(summary.contains("123456"));
        assertFalse(summary.contains("654321"));
        assertEquals("123456", payload.getAsJsonObject().get("PIN").getAsString());
        assertEquals("654321",
                payload.getAsJsonObject().getAsJsonArray("NESTED").get(0).getAsJsonObject().get("pin").getAsString());
    }
}
