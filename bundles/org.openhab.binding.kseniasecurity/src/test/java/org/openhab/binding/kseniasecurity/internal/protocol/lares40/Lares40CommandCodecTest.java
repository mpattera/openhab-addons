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

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Lares40CommandCodec}.
 *
 * @author Michele Pattera - Initial contribution
 */
public class Lares40CommandCodecTest {

    private final Lares40CommandCodec codec = new Lares40CommandCodec();

    @Test
    public void testRoundTripProducesACrcValidatedCommand() {
        String encoded = codec.encode("sender", "receiver", Lares40ProtocolConstants.COMMAND_LOGIN, 7,
                Lares40ProtocolConstants.PAYLOAD_TYPE_SUPERVISOR,
                Map.of(Lares40ProtocolConstants.PAYLOAD_PIN, "123456")).orElseThrow();

        Lares40Command decoded = codec.decode(encoded).orElseThrow();

        assertEquals("sender", decoded.sender);
        assertEquals("receiver", decoded.receiver);
        assertEquals(Lares40ProtocolConstants.COMMAND_LOGIN, decoded.command);
        assertEquals("7", decoded.id);
    }

    @Test
    public void testRejectsMalformedAndCrcModifiedCommands() {
        assertTrue(codec.decode("not-json").isEmpty());

        String encoded = codec.encode("sender", "receiver", Lares40ProtocolConstants.COMMAND_LOGIN, 7,
                Lares40ProtocolConstants.PAYLOAD_TYPE_SUPERVISOR,
                Map.of(Lares40ProtocolConstants.PAYLOAD_PIN, "123456")).orElseThrow();
        String crcField = "\"" + Lares40ProtocolConstants.FIELD_CRC16 + "\":\"";
        int crcStart = encoded.lastIndexOf(crcField) + crcField.length();
        char originalCharacter = encoded.charAt(crcStart);
        char modifiedCharacter = originalCharacter == '0' ? '1' : '0';
        String modified = encoded.substring(0, crcStart) + modifiedCharacter + encoded.substring(crcStart + 1);

        assertTrue(codec.decode(modified).isEmpty());
    }
}
