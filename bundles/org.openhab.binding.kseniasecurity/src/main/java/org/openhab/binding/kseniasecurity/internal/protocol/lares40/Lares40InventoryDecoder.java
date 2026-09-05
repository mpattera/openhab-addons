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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.kseniasecurity.internal.state.PanelInventoryUpdates;
import org.openhab.binding.kseniasecurity.internal.state.PartitionMetadataField;
import org.openhab.binding.kseniasecurity.internal.state.PartitionMetadataSnapshot;
import org.openhab.binding.kseniasecurity.internal.state.ZoneMetadataField;
import org.openhab.binding.kseniasecurity.internal.state.ZoneMetadataSnapshot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Decodes the static zone and partition inventory from a Lares {@code READ_RES} payload.
 *
 * <p>
 * Only explicitly modeled, non-sensitive fields are projected. Unknown fields are intentionally discarded instead
 * of retaining the raw response.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public final class Lares40InventoryDecoder {

    /**
     * Decodes inventory sections present in a response payload.
     *
     * @param payload the protocol payload
     * @return decoded sections, or an empty update when the payload is not an object
     */
    public PanelInventoryUpdates decode(@Nullable JsonElement payload) {
        if (payload == null || !payload.isJsonObject()) {
            return PanelInventoryUpdates.empty();
        }

        JsonObject object = payload.getAsJsonObject();
        @Nullable
        JsonArray zones = getArray(object, Lares40ProtocolConstants.PAYLOAD_TYPE_ZONES);
        @Nullable
        JsonArray partitions = getArray(object, Lares40ProtocolConstants.PAYLOAD_TYPE_PARTITIONS);
        return new PanelInventoryUpdates(decodeZones(zones), zones != null, decodePartitions(partitions),
                partitions != null);
    }

    private Map<Integer, ZoneMetadataSnapshot> decodeZones(@Nullable JsonArray zones) {
        if (zones == null) {
            return Map.of();
        }

        Map<Integer, ZoneMetadataSnapshot> snapshots = new HashMap<>();
        for (JsonElement element : zones) {
            if (!element.isJsonObject()) {
                continue;
            }

            @Nullable
            ZoneMetadataSnapshot snapshot = decodeZone(element.getAsJsonObject());
            if (snapshot != null) {
                snapshots.put(snapshot.id(), snapshot);
            }
        }
        return snapshots;
    }

    private Map<Integer, PartitionMetadataSnapshot> decodePartitions(@Nullable JsonArray partitions) {
        if (partitions == null) {
            return Map.of();
        }

        Map<Integer, PartitionMetadataSnapshot> snapshots = new HashMap<>();
        for (JsonElement element : partitions) {
            if (!element.isJsonObject()) {
                continue;
            }

            @Nullable
            PartitionMetadataSnapshot snapshot = decodePartition(element.getAsJsonObject());
            if (snapshot != null) {
                snapshots.put(snapshot.id(), snapshot);
            }
        }
        return snapshots;
    }

    private @Nullable ZoneMetadataSnapshot decodeZone(JsonObject data) {
        @Nullable
        Integer id = getId(data);
        if (id == null) {
            return null;
        }

        @Nullable
        String description = getText(data, Lares40ProtocolConstants.PAYLOAD_DESCRIPTION);
        return new ZoneMetadataSnapshot(id.intValue(),
                description == null ? Map.of() : Map.of(ZoneMetadataField.DESCRIPTION, description));
    }

    private @Nullable PartitionMetadataSnapshot decodePartition(JsonObject data) {
        @Nullable
        Integer id = getId(data);
        if (id == null) {
            return null;
        }

        @Nullable
        String description = getText(data, Lares40ProtocolConstants.PAYLOAD_DESCRIPTION);
        return new PartitionMetadataSnapshot(id.intValue(),
                description == null ? Map.of() : Map.of(PartitionMetadataField.DESCRIPTION, description));
    }

    private @Nullable JsonArray getArray(JsonObject object, String key) {
        @Nullable
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private @Nullable Integer getId(JsonObject object) {
        @Nullable
        String text = getText(object, Lares40ProtocolConstants.PAYLOAD_ENTRY_ID);
        if (text == null) {
            return null;
        }

        try {
            int id = Integer.parseInt(text);
            return id > 0 ? Integer.valueOf(id) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private @Nullable String getText(JsonObject object, String key) {
        @Nullable
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }

        try {
            return element.getAsString();
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }
}
