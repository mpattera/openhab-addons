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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

/**
 * Tests for {@link Lares40EntityTraceFormatter}.
 *
 * @author Michele Pattera - Initial contribution
 */
public class Lares40EntityTraceFormatterTest {

    @Test
    public void testFormatsEverySafeStatusFieldIncludingUnknownFirmwareFields() {
        String formatted = Lares40EntityTraceFormatter.formatPayload(JsonParser.parseString("""
                {
                  "STATUS_ZONES": [
                    { "ID": "3", "STA": "R", "BYP": "NO", "LBL": "Garage", "OHM": "1200", "VAS": "1" }
                  ],
                  "STATUS_PARTITIONS": [ { "ID": "1", "ARM": "D", "AST": "OK", "TST": "OK" } ]
                }
                """));

        assertTrue(formatted.contains("STATUS_ZONES=[id=\"3\""));
        assertTrue(formatted.contains("STA=\"R\""));
        assertTrue(formatted.contains("LBL=\"Garage\""));
        assertTrue(formatted.contains("OHM=\"1200\""));
        assertTrue(formatted.contains("VAS=\"1\""));
        assertTrue(formatted.contains("STATUS_PARTITIONS=[id=\"1\""));
        assertTrue(formatted.contains("ARM=\"D\""));
    }

    @Test
    public void testFormatsChangesForTheRegisteredReceiverOnly() {
        String formatted = Lares40EntityTraceFormatter.formatChanges(JsonParser.parseString("""
                {
                  "receiver-a": { "STATUS_ZONES": [ { "ID": "3", "STA": "A" } ] },
                  "receiver-b": { "STATUS_ZONES": [ { "ID": "4", "STA": "R" } ] }
                }
                """), "receiver-a");

        assertTrue(formatted.contains("id=\"3\""));
        assertTrue(formatted.contains("STA=\"A\""));
        assertFalse(formatted.contains("id=\"4\""));
    }

    @Test
    public void testRedactsOnlyPinFieldsAndDoesNotExpandOtherPayloadSections() {
        String formatted = Lares40EntityTraceFormatter.formatPayload(JsonParser.parseString("""
                {
                  "ZONES": [ {
                    "ID": "3",
                    "DES": "Garage",
                    "PIN": "123456",
                    "4DIGIT_PIN": "T",
                    "ID_LOGIN": "session",
                    "PWD": "secret",
                    "NESTED": { "PIN": "654321", "VALUE": "shown" }
                  } ],
                  "USR_ALL": [ { "ID": "1", "PWD": "secret" } ]
                }
                """));

        assertTrue(formatted.contains("DES=\"Garage\""));
        assertTrue(formatted.contains("PIN=<redacted>"));
        assertTrue(formatted.contains("4DIGIT_PIN=\"T\""));
        assertTrue(formatted.contains("ID_LOGIN=\"session\""));
        assertTrue(formatted.contains("PWD=\"secret\""));
        assertTrue(formatted.contains("NESTED={\"PIN\":\"<redacted>\",\"VALUE\":\"shown\"}"));
        assertFalse(formatted.contains("123456"));
        assertFalse(formatted.contains("654321"));
        assertFalse(formatted.contains("USR_ALL"));
    }
}
