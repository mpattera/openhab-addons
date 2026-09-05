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
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Formats diagnostic protocol payloads with sensitive field values redacted.
 *
 * <p>
 * Trace logging is used to investigate firmware-specific fields. An explicit set of protocol field constants defines
 * which values are redacted; all other fields remain visible, including session identifiers and site descriptions.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public final class Lares40ReadDiagnostics {

    private static final Set<String> SENSITIVE_FIELDS = Set.of(Lares40ProtocolConstants.PAYLOAD_PIN);

    private Lares40ReadDiagnostics() {
    }

    /**
     * Formats a protocol payload while redacting sensitive field values.
     *
     * @param payload the protocol payload
     * @return the payload formatted for a diagnostic trace
     */
    public static String summarize(@Nullable JsonElement payload) {
        if (payload == null || payload.isJsonNull()) {
            return "<no payload>";
        }
        return redactSensitiveValues(payload).toString();
    }

    /**
     * Creates an independent copy of a JSON value with sensitive field values redacted recursively.
     *
     * @param value JSON value to copy
     * @return copied JSON value with the explicitly selected fields redacted
     */
    static JsonElement redactSensitiveValues(JsonElement value) {
        if (value.isJsonNull() || value.isJsonPrimitive()) {
            return value.deepCopy();
        }
        if (value.isJsonArray()) {
            JsonArray redactedArray = new JsonArray();
            for (JsonElement entry : value.getAsJsonArray()) {
                redactedArray.add(redactSensitiveValues(entry));
            }
            return redactedArray;
        }

        JsonObject redactedObject = new JsonObject();
        List<Entry<String, JsonElement>> entries = new ArrayList<>(value.getAsJsonObject().entrySet());
        entries.sort(Comparator.comparing(Entry::getKey));
        for (Entry<String, JsonElement> entry : entries) {
            redactedObject.add(entry.getKey(), isSensitiveField(entry.getKey()) ? new JsonPrimitive("<redacted>")
                    : redactSensitiveValues(entry.getValue()));
        }
        return redactedObject;
    }

    /**
     * Checks whether the complete field name is explicitly marked as sensitive, ignoring case.
     *
     * @param fieldName protocol field name
     * @return {@code true} if the value must be redacted
     */
    static boolean isSensitiveField(String fieldName) {
        return SENSITIVE_FIELDS.contains(fieldName.toUpperCase(Locale.ROOT));
    }
}
