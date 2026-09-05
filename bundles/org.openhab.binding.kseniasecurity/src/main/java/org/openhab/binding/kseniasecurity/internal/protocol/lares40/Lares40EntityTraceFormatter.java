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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Formats zone and partition fields for protocol trace logging.
 *
 * <p>
 * Only zone, partition, and their status entity arrays are expanded here. The accompanying payload trace contains
 * the entire response with sensitive values redacted. Every field received in the selected entity entries is shown so
 * that later firmware additions can be investigated from a trace. The formatter redacts explicitly selected fields,
 * but entity descriptions and other values can be included; trace logging should therefore be enabled only temporarily
 * for diagnostics.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public final class Lares40EntityTraceFormatter {

    private static final List<String> ENTITY_ARRAY_KEYS = List.of(Lares40ProtocolConstants.PAYLOAD_TYPE_ZONES,
            Lares40ProtocolConstants.PAYLOAD_TYPE_PARTITIONS, Lares40ProtocolConstants.PAYLOAD_STATUS_ZONES,
            Lares40ProtocolConstants.PAYLOAD_STATUS_PARTITIONS);

    private Lares40EntityTraceFormatter() {
    }

    /**
     * Formats selected entity arrays found directly in a protocol payload.
     *
     * @param payload the protocol payload
     * @return entity entries with every received field, or a reason why none could be formatted
     */
    public static String formatPayload(@Nullable JsonElement payload) {
        if (payload == null || payload.isJsonNull()) {
            return "<no payload>";
        }
        if (!payload.isJsonObject()) {
            return "<non-object payload>";
        }
        return formatObject(payload.getAsJsonObject());
    }

    /**
     * Formats selected entity arrays in a {@code REALTIME(CHANGES)} payload for one receiver.
     *
     * @param payload the protocol payload
     * @param receiver the registration receiver identifier
     * @return entity entries with every received field, or a reason why none could be formatted
     */
    public static String formatChanges(@Nullable JsonElement payload, String receiver) {
        if (payload == null || payload.isJsonNull() || !payload.isJsonObject()) {
            return "<missing changes payload>";
        }

        @Nullable
        JsonElement receiverData = payload.getAsJsonObject().get(receiver);
        if (receiverData == null || !receiverData.isJsonObject()) {
            return "<no changes for this receiver>";
        }
        return formatObject(receiverData.getAsJsonObject());
    }

    private static String formatObject(JsonObject payload) {
        List<String> collections = new ArrayList<>();
        for (String arrayKey : ENTITY_ARRAY_KEYS) {
            @Nullable
            JsonElement entries = payload.get(arrayKey);
            if (entries == null || !entries.isJsonArray()) {
                continue;
            }
            collections.add(arrayKey + "=" + formatEntries(entries.getAsJsonArray()));
        }
        return collections.isEmpty() ? "<no zone or partition entries>" : String.join(", ", collections);
    }

    private static String formatEntries(JsonArray entries) {
        List<String> formattedEntries = new ArrayList<>();
        int index = 0;
        for (JsonElement entry : entries) {
            if (!entry.isJsonObject()) {
                formattedEntries.add("entry[" + index++ + "]=<non-object>");
                continue;
            }
            formattedEntries.add(formatEntry(entry.getAsJsonObject(), index++));
        }
        return formattedEntries.toString();
    }

    private static String formatEntry(JsonObject entry, int index) {
        @Nullable
        JsonElement identifier = entry.get(Lares40ProtocolConstants.PAYLOAD_ENTRY_ID);
        String identifierText = identifier == null ? "entry[" + index + "]" : "id=" + formatValue("ID", identifier);

        List<Entry<String, JsonElement>> fields = new ArrayList<>(entry.entrySet());
        fields.sort(Comparator.comparing(Entry::getKey));
        List<String> formattedFields = new ArrayList<>();
        for (Entry<String, JsonElement> field : fields) {
            formattedFields.add(field.getKey() + "=" + formatValue(field.getKey(), field.getValue()));
        }
        return identifierText + " {" + String.join(", ", formattedFields) + "}";
    }

    private static String formatValue(String key, JsonElement value) {
        if (Lares40ReadDiagnostics.isSensitiveField(key)) {
            return "<redacted>";
        }
        return Lares40ReadDiagnostics.redactSensitiveValues(value).toString();
    }
}
