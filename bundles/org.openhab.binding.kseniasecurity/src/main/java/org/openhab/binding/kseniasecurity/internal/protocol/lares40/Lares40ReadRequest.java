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

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * One read operation expressed in Lares 4.0 protocol terms.
 *
 * <p>
 * This class deliberately models only the protocol request. Its caller decides whether a read is diagnostic,
 * metadata retrieval, or a future discovery operation.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public record Lares40ReadRequest(String payloadType, List<String> types, String rangeStart, String rangeEnd) {

    /**
     * Creates an immutable request.
     */
    public Lares40ReadRequest {
        types = List.copyOf(types);
    }

    /**
     * Creates a diagnostic read from {@code PAYLOAD_TYPE} or {@code PAYLOAD_TYPE rangeStart rangeEnd}.
     * Payload types are not restricted to those known by this binding; the panel validates their support.
     *
     * @param command the command value received from openHAB
     * @return the corresponding request, or empty when the command syntax is invalid
     */
    public static Optional<Lares40ReadRequest> forDiagnosticCommand(String command) {
        String normalizedCommand = command.strip().toUpperCase(Locale.ROOT);
        if (normalizedCommand.isEmpty()) {
            return Optional.empty();
        }
        String[] parts = normalizedCommand.split("\\s+");
        if (parts.length == 1) {
            return Optional.of(singleType(parts[0]));
        }
        if (parts.length == 3) {
            return Optional.of(new Lares40ReadRequest(parts[0], List.of(), parts[1], parts[2]));
        }
        return Optional.empty();
    }

    /**
     * Creates the static zone and partition inventory request used during bridge initialization.
     *
     * @return the initial inventory request
     */
    public static Lares40ReadRequest initialInventory() {
        return multipleTypes();
    }

    /**
     * Builds the payload object of a {@code READ} command.
     *
     * @param loginId the active panel login identifier
     * @return the JSON payload
     */
    public JsonObject createPayload(String loginId) {
        JsonObject payload = new JsonObject();
        payload.addProperty(Lares40ProtocolConstants.PAYLOAD_LOGIN_ID, loginId);
        if (!types.isEmpty()) {
            JsonArray typeArray = new JsonArray();
            types.forEach(typeArray::add);
            payload.add(Lares40ProtocolConstants.PAYLOAD_TYPES, typeArray);
        }

        JsonArray range = new JsonArray();
        range.add(rangeStart);
        range.add(rangeEnd);
        payload.add(Lares40ProtocolConstants.PAYLOAD_ITEMS_RANGE, range);
        return payload;
    }

    private static Lares40ReadRequest singleType(String payloadType) {
        return new Lares40ReadRequest(payloadType, List.of(), Lares40ProtocolConstants.PAYLOAD_ITEMS_RANGE_ALL,
                Lares40ProtocolConstants.PAYLOAD_ITEMS_RANGE_ALL);
    }

    private static Lares40ReadRequest multipleTypes() {
        return new Lares40ReadRequest(Lares40ProtocolConstants.PAYLOAD_TYPE_MULTIPLE_TYPES,
                List.of(Lares40ProtocolConstants.PAYLOAD_TYPE_ZONES, Lares40ProtocolConstants.PAYLOAD_TYPE_PARTITIONS),
                Lares40ProtocolConstants.PAYLOAD_ITEMS_RANGE_ALL, Lares40ProtocolConstants.PAYLOAD_ITEMS_RANGE_ALL);
    }
}
