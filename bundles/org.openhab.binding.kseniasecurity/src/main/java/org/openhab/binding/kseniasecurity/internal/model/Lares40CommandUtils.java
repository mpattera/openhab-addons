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
package org.openhab.binding.kseniasecurity.internal.model;

import java.time.Instant;
import java.util.Optional;

import org.openhab.binding.kseniasecurity.internal.CRC16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Lares40CommandUtils {

    private static final Logger logger = LoggerFactory.getLogger(Lares40CommandUtils.class);

    private static final Gson gson = new Gson();

    public static String buildCommand(String sender, String receiver, String cmd, int id, String payloadType,
            Object payload) {
        try {
            Lares40Command command = new Lares40Command();
            command.sender = sender;
            command.receiver = receiver;
            command.cmd = cmd;
            command.id = String.valueOf(id);
            command.payloadType = payloadType;
            command.payload = new GsonBuilder().create().toJsonTree(payload);
            command.timestamp = String.valueOf(Instant.now().getEpochSecond());
            command.crc16 = "";

            String partialJson = gson.toJson(command);
            String crc_key = "\"" + Lares40Command.KEY_CRC16 + "\"";
            String base = partialJson.substring(0, partialJson.indexOf(crc_key) + crc_key.length());
            command.crc16 = CRC16.calculate(base);
            String commandJson = gson.toJson(command);

            return commandJson;
        } catch (Exception e) {
            logger.error("Error while building command {}", e.getMessage());

            return "{}";
        }
    }

    public static Optional<Lares40Command> decodeCommand(String jsonText) {
        try {
            Lares40Command command = gson.fromJson(jsonText, Lares40Command.class);

            String crc_key = "\"" + Lares40Command.KEY_CRC16 + "\"";
            String base = jsonText.substring(0, jsonText.indexOf(crc_key) + crc_key.length());
            String crc = CRC16.calculate(base);
            if (!crc.equalsIgnoreCase(command.crc16)) {
                logger.error("Command CRC check failed");
                return Optional.empty();
            }

            return Optional.of(command);
        } catch (Exception e) {
            logger.error("Error while decoding command {}", e.getMessage());
            return Optional.empty();
        }
    }
}
