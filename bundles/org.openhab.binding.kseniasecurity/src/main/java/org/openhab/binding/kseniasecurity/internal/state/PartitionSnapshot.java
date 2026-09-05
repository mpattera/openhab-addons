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
package org.openhab.binding.kseniasecurity.internal.state;

import java.util.EnumMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The current best-known state for one alarm partition.
 *
 * <p>
 * A field absent from {@link #values()} has not been supplied since the bridge started.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public record PartitionSnapshot(int id, Map<PartitionStateField, String> values) {

    /**
     * Creates a snapshot with an immutable field map.
     *
     * @param id the panel partition identifier
     * @param values the known values, indexed by their semantic field
     */
    public PartitionSnapshot {
        values = Map.copyOf(values);
    }

    /**
     * Creates an empty snapshot for a partition that has not yet reported any fields.
     *
     * @param id the panel partition identifier
     * @return the initial snapshot
     */
    public static PartitionSnapshot initial(int id) {
        return new PartitionSnapshot(id, Map.of());
    }

    /**
     * Returns a new snapshot with the fields supplied in {@code update} applied.
     *
     * @param update the partial update to apply
     * @return the merged snapshot
     */
    public PartitionSnapshot merge(PartitionStateUpdate update) {
        if (id != update.id()) {
            throw new IllegalArgumentException("Cannot merge a state update for a different partition");
        }

        Map<PartitionStateField, String> mergedValues = new EnumMap<>(PartitionStateField.class);
        mergedValues.putAll(values);
        for (PartitionStateField clearedField : update.clearedFields()) {
            mergedValues.remove(clearedField);
        }
        mergedValues.putAll(update.values());
        return new PartitionSnapshot(id, mergedValues);
    }

    /**
     * Returns a known value for a field, if the panel has supplied it.
     *
     * @param field the semantic field to look up
     * @return the known value, or {@code null} when it is not known yet
     */
    public @Nullable String get(PartitionStateField field) {
        return values.get(field);
    }

    /**
     * Returns the current arming state.
     *
     * @return the arming state, or {@code null} when it has not been reported yet
     */
    public @Nullable PartitionArmingState armingState() {
        return getEnum(PartitionStateField.ARMING_STATE, PartitionArmingState.class);
    }

    /**
     * Returns the remaining entry or exit delay in seconds.
     *
     * @return the remaining seconds, or {@code null} when no valid delay is known
     */
    public @Nullable Integer delayRemainingSeconds() {
        @Nullable
        String value = get(PartitionStateField.DELAY_REMAINING);
        if (value == null) {
            return null;
        }

        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Returns the partition alarm state.
     *
     * @return the alarm state, or {@code null} when it has not been reported yet
     */
    public @Nullable PartitionEventState alarmState() {
        return getEnum(PartitionStateField.ALARM_STATE, PartitionEventState.class);
    }

    /**
     * Returns the partition tamper state.
     *
     * @return the tamper state, or {@code null} when it has not been reported yet
     */
    public @Nullable PartitionEventState tamperState() {
        return getEnum(PartitionStateField.TAMPER_STATE, PartitionEventState.class);
    }

    private <T extends Enum<T>> @Nullable T getEnum(PartitionStateField field, Class<T> type) {
        @Nullable
        String value = get(field);
        if (value == null) {
            return null;
        }

        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
