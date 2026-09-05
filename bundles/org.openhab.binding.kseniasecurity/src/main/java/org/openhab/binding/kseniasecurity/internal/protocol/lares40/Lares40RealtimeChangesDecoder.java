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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.kseniasecurity.internal.state.PanelStateUpdates;
import org.openhab.binding.kseniasecurity.internal.state.PartitionArmingState;
import org.openhab.binding.kseniasecurity.internal.state.PartitionEventState;
import org.openhab.binding.kseniasecurity.internal.state.PartitionStateField;
import org.openhab.binding.kseniasecurity.internal.state.PartitionStateUpdate;
import org.openhab.binding.kseniasecurity.internal.state.ZoneBypassMode;
import org.openhab.binding.kseniasecurity.internal.state.ZoneCondition;
import org.openhab.binding.kseniasecurity.internal.state.ZoneCycleState;
import org.openhab.binding.kseniasecurity.internal.state.ZoneFaultState;
import org.openhab.binding.kseniasecurity.internal.state.ZoneStateField;
import org.openhab.binding.kseniasecurity.internal.state.ZoneStateUpdate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Decodes Lares 4.0 real-time state messages into protocol-independent state updates.
 *
 * <p>
 * Invalid or incomplete entries are ignored so that one malformed entry cannot prevent valid state changes in the
 * same notification from being processed.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public class Lares40RealtimeChangesDecoder {

    private static final String FIELD_ZONE_REALTIME_STATE = "STA";
    private static final String FIELD_ZONE_BYPASS_STATE = "BYP";
    private static final String FIELD_ZONE_TAMPER_STATE = "T";
    private static final String FIELD_ZONE_TAMPER_MEMORY_STATE = "TM";
    private static final String FIELD_ZONE_ALARM_STATE = "A";
    private static final String FIELD_ZONE_ALARM_MEMORY_STATE = "AM";
    private static final String FIELD_ZONE_FAULT_MEMORY = "FM";
    private static final String FIELD_PARTITION_REALTIME_STATE = "ARM";
    private static final String FIELD_PARTITION_DELAY_TIMER = "T";
    private static final String FIELD_PARTITION_ALARM_STATE = "AST";
    private static final String FIELD_PARTITION_TAMPER_STATE = "TST";

    /**
     * Decodes a {@code REALTIME(CHANGES)} message addressed to a specific receiver.
     *
     * @param command the decoded protocol command
     * @param receiver the registration receiver identifier expected in the payload
     * @return the decoded updates, or an empty group if the payload is not a valid changes notification
     */
    public PanelStateUpdates decodeChanges(Lares40Command command, String receiver) {
        @Nullable
        JsonElement payload = command.payload;
        if (payload == null || !payload.isJsonObject()) {
            return PanelStateUpdates.empty();
        }

        @Nullable
        JsonElement receiverElement = payload.getAsJsonObject().get(receiver);
        if (receiverElement == null || !receiverElement.isJsonObject()) {
            return PanelStateUpdates.empty();
        }

        JsonObject receiverData = receiverElement.getAsJsonObject();
        return decodeStatusPayload(receiverData);
    }

    /**
     * Decodes the initial state carried directly in a {@code REALTIME(REGISTER_ACK)} payload.
     *
     * @param command the decoded protocol command
     * @return the decoded updates, or an empty group if the payload is not an object
     */
    public PanelStateUpdates decodeRegisterAcknowledgement(Lares40Command command) {
        @Nullable
        JsonElement payload = command.payload;
        if (payload == null || !payload.isJsonObject()) {
            return PanelStateUpdates.empty();
        }

        return decodeStatusPayload(payload.getAsJsonObject());
    }

    private PanelStateUpdates decodeStatusPayload(JsonObject statusPayload) {
        return new PanelStateUpdates(decodeZones(statusPayload), decodePartitions(statusPayload));
    }

    private List<ZoneStateUpdate> decodeZones(JsonObject statusPayload) {
        @Nullable
        JsonArray zones = getArray(statusPayload, Lares40ProtocolConstants.PAYLOAD_STATUS_ZONES);
        if (zones == null) {
            return List.of();
        }

        List<ZoneStateUpdate> updates = new ArrayList<>();
        for (JsonElement zoneElement : zones) {
            if (!zoneElement.isJsonObject()) {
                continue;
            }

            @Nullable
            ZoneStateUpdate update = decodeZone(zoneElement.getAsJsonObject());
            if (update != null) {
                updates.add(update);
            }
        }
        return updates;
    }

    private List<PartitionStateUpdate> decodePartitions(JsonObject statusPayload) {
        @Nullable
        JsonArray partitions = getArray(statusPayload, Lares40ProtocolConstants.PAYLOAD_STATUS_PARTITIONS);
        if (partitions == null) {
            return List.of();
        }

        List<PartitionStateUpdate> updates = new ArrayList<>();
        for (JsonElement partitionElement : partitions) {
            if (!partitionElement.isJsonObject()) {
                continue;
            }

            @Nullable
            PartitionStateUpdate update = decodePartition(partitionElement.getAsJsonObject());
            if (update != null) {
                updates.add(update);
            }
        }
        return updates;
    }

    private @Nullable ZoneStateUpdate decodeZone(JsonObject zoneData) {
        @Nullable
        Integer id = getId(zoneData, Lares40ProtocolConstants.PAYLOAD_ENTRY_ID);
        if (id == null) {
            return null;
        }

        Map<ZoneStateField, String> values = new EnumMap<>(ZoneStateField.class);

        @Nullable
        String condition = getText(zoneData, FIELD_ZONE_REALTIME_STATE);
        if (condition != null) {
            values.put(ZoneStateField.REALTIME_STATE, toZoneCondition(condition).name());
        }

        @Nullable
        String bypassMode = getText(zoneData, FIELD_ZONE_BYPASS_STATE);
        if (bypassMode != null) {
            values.put(ZoneStateField.BYPASS_STATE, toZoneBypassMode(bypassMode).name());
        }

        @Nullable
        String tamperCycleState = getFirstText(zoneData, FIELD_ZONE_TAMPER_STATE, FIELD_ZONE_TAMPER_MEMORY_STATE);
        if (tamperCycleState != null) {
            values.put(ZoneStateField.TAMPER_STATE, toZoneCycleState(tamperCycleState).name());
        }

        @Nullable
        String alarmCycleState = getFirstText(zoneData, FIELD_ZONE_ALARM_STATE, FIELD_ZONE_ALARM_MEMORY_STATE);
        if (alarmCycleState != null) {
            values.put(ZoneStateField.ALARM_STATE, toZoneCycleState(alarmCycleState).name());
        }

        @Nullable
        String faultMemory = getText(zoneData, FIELD_ZONE_FAULT_MEMORY);
        if (faultMemory != null) {
            values.put(ZoneStateField.FAULT_STATE, toZoneFaultState(faultMemory).name());
        }
        return new ZoneStateUpdate(id, values);
    }

    private @Nullable PartitionStateUpdate decodePartition(JsonObject partitionData) {
        @Nullable
        Integer id = getId(partitionData, Lares40ProtocolConstants.PAYLOAD_ENTRY_ID);
        if (id == null) {
            return null;
        }

        Map<PartitionStateField, String> values = new EnumMap<>(PartitionStateField.class);
        Set<PartitionStateField> clearedFields = EnumSet.noneOf(PartitionStateField.class);

        @Nullable
        String armingState = getText(partitionData, FIELD_PARTITION_REALTIME_STATE);
        if (armingState != null) {
            values.put(PartitionStateField.ARMING_STATE, toPartitionArmingState(armingState).name());
            clearedFields.add(PartitionStateField.DELAY_REMAINING);
        }

        @Nullable
        String delayRemaining = getText(partitionData, FIELD_PARTITION_DELAY_TIMER);
        if (delayRemaining != null) {
            @Nullable
            String seconds = toDelaySeconds(delayRemaining);
            if (seconds == null) {
                clearedFields.add(PartitionStateField.DELAY_REMAINING);
            } else {
                values.put(PartitionStateField.DELAY_REMAINING, seconds);
            }
        }

        @Nullable
        String alarmState = getText(partitionData, FIELD_PARTITION_ALARM_STATE);
        if (alarmState != null) {
            values.put(PartitionStateField.ALARM_STATE, toPartitionAlarmState(alarmState).name());
        }

        @Nullable
        String tamperState = getText(partitionData, FIELD_PARTITION_TAMPER_STATE);
        if (tamperState != null) {
            values.put(PartitionStateField.TAMPER_STATE, toPartitionTamperState(tamperState).name());
        }
        return new PartitionStateUpdate(id, values, clearedFields);
    }

    private ZoneCondition toZoneCondition(String value) {
        return switch (value) {
            case "R" -> ZoneCondition.REST;
            case "A" -> ZoneCondition.ALARM;
            case "FM" -> ZoneCondition.FAULT;
            case "T" -> ZoneCondition.TAMPER;
            case "E" -> ZoneCondition.ERROR;
            default -> ZoneCondition.UNKNOWN;
        };
    }

    private ZoneBypassMode toZoneBypassMode(String value) {
        return switch (value) {
            case "NO" -> ZoneBypassMode.NOT_BYPASSED;
            case "AUTO" -> ZoneBypassMode.AUTO_BYPASSED;
            case "MAN_I" -> ZoneBypassMode.MANUAL_BYPASS_DURING_ARMING;
            case "MAN_M" -> ZoneBypassMode.MANUAL_BYPASS;
            default -> ZoneBypassMode.UNKNOWN;
        };
    }

    private ZoneCycleState toZoneCycleState(String value) {
        return switch (value) {
            case "C" -> ZoneCycleState.ACTIVE;
            case "M", "T" -> ZoneCycleState.MEMORY;
            case "N", "F" -> ZoneCycleState.INACTIVE;
            default -> ZoneCycleState.UNKNOWN;
        };
    }

    private ZoneFaultState toZoneFaultState(String value) {
        return switch (value) {
            case "T" -> ZoneFaultState.ACTIVE;
            case "F" -> ZoneFaultState.INACTIVE;
            default -> ZoneFaultState.UNKNOWN;
        };
    }

    private PartitionArmingState toPartitionArmingState(String value) {
        return switch (value) {
            case "D" -> PartitionArmingState.DISARMED;
            case "DA" -> PartitionArmingState.ARMED_DELAYED;
            case "IA" -> PartitionArmingState.ARMED_IMMEDIATE;
            case "IT" -> PartitionArmingState.ENTRY_DELAY;
            case "OT" -> PartitionArmingState.EXIT_DELAY;
            default -> PartitionArmingState.UNKNOWN;
        };
    }

    private PartitionEventState toPartitionAlarmState(String value) {
        return switch (value) {
            case "OK" -> PartitionEventState.INACTIVE;
            case "AL" -> PartitionEventState.ACTIVE;
            case "AM" -> PartitionEventState.MEMORY;
            default -> PartitionEventState.UNKNOWN;
        };
    }

    private PartitionEventState toPartitionTamperState(String value) {
        return switch (value) {
            case "OK" -> PartitionEventState.INACTIVE;
            case "TAM" -> PartitionEventState.ACTIVE;
            case "TM" -> PartitionEventState.MEMORY;
            default -> PartitionEventState.UNKNOWN;
        };
    }

    private @Nullable String toDelaySeconds(String value) {
        try {
            int seconds = Integer.parseInt(value);
            return seconds >= 0 && seconds <= 65535 ? Integer.toString(seconds) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private @Nullable JsonArray getArray(JsonObject object, String key) {
        @Nullable
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private @Nullable Integer getId(JsonObject object, String key) {
        @Nullable
        String idText = getText(object, key);
        if (idText == null) {
            return null;
        }

        try {
            int id = Integer.parseInt(idText);
            return id > 0 ? id : null;
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

    private @Nullable String getFirstText(JsonObject object, String primaryKey, String alternateKey) {
        @Nullable
        String primaryValue = getText(object, primaryKey);
        return primaryValue != null ? primaryValue : getText(object, alternateKey);
    }
}
