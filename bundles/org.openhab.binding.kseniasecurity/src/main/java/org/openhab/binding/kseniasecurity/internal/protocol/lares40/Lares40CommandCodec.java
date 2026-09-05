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

import java.time.Instant;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.kseniasecurity.internal.CRC16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * Encodes and validates Lares 4.0 WebSocket commands.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public final class Lares40CommandCodec {

    private static final String CRC_FIELD = "\"" + Lares40ProtocolConstants.FIELD_CRC16 + "\"";

    private final Logger logger = LoggerFactory.getLogger(Lares40CommandCodec.class);
    private final Gson gson = new Gson();

    /**
     * Encodes a command and appends the protocol CRC.
     *
     * @param sender the command sender identifier
     * @param receiver the command receiver identifier
     * @param commandName the protocol command name
     * @param id the protocol command identifier
     * @param payloadType the protocol payload type
     * @param payload the command payload
     * @return an encoded command, or an empty result if it cannot be serialized
     */
    public Optional<String> encode(String sender, String receiver, String commandName, int id, String payloadType,
            Object payload) {
        try {
            Lares40Command command = new Lares40Command();
            command.sender = sender;
            command.receiver = receiver;
            command.command = commandName;
            command.id = String.valueOf(id);
            command.payloadType = payloadType;
            command.payload = gson.toJsonTree(payload);
            command.timestamp = String.valueOf(Instant.now().getEpochSecond());
            command.crc16 = "";

            String partialJson = gson.toJson(command);
            @Nullable
            String crcInput = getCrcInput(partialJson);
            if (crcInput == null) {
                logger.debug("Cannot encode a panel command because the CRC field is missing");
                return Optional.empty();
            }

            command.crc16 = CRC16.calculate(crcInput);
            return Optional.of(gson.toJson(command));
        } catch (RuntimeException e) {
            logger.debug("Could not encode a panel command", e);
            return Optional.empty();
        }
    }

    /**
     * Decodes a command and verifies its CRC.
     *
     * @param jsonText the raw WebSocket text frame
     * @return a validated command, or an empty result if the input is malformed or has an invalid CRC
     */
    public Optional<Lares40Command> decode(String jsonText) {
        @Nullable
        String crcInput = getCrcInput(jsonText);
        if (crcInput == null) {
            logger.debug("Ignoring a panel command without a CRC field");
            return Optional.empty();
        }

        try {
            @Nullable
            Lares40Command command = gson.fromJson(jsonText, Lares40Command.class);
            if (command == null) {
                logger.debug("Ignoring an empty panel command");
                return Optional.empty();
            }

            @Nullable
            String receivedCrc = command.crc16;
            if (receivedCrc == null || !CRC16.calculate(crcInput).equalsIgnoreCase(receivedCrc)) {
                logger.debug("Ignoring a panel command with an invalid CRC");
                return Optional.empty();
            }
            return Optional.of(command);
        } catch (RuntimeException e) {
            logger.debug("Could not decode a panel command", e);
            return Optional.empty();
        }
    }

    private static @Nullable String getCrcInput(String jsonText) {
        int crcFieldIndex = jsonText.indexOf(CRC_FIELD);
        return crcFieldIndex < 0 ? null : jsonText.substring(0, crcFieldIndex + CRC_FIELD.length());
    }
}
