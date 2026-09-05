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

import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A partial status update for one alarm partition.
 *
 * <p>
 * Fields absent from both {@link #values()} and {@link #clearedFields()} were not supplied by the panel. A field in
 * {@code clearedFields} was explicitly invalidated by the protocol adapter.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public record PartitionStateUpdate(int id, Map<PartitionStateField, String> values,
        Set<PartitionStateField> clearedFields) {

    /**
     * Creates a partial update that does not explicitly clear any field.
     *
     * @param id the panel partition identifier
     * @param values the fields included in this partial update
     */
    public PartitionStateUpdate(int id, Map<PartitionStateField, String> values) {
        this(id, values, Set.of());
    }

    /**
     * Creates an update with an immutable field map.
     *
     * @param id the panel partition identifier
     * @param values the fields included in this partial update
     * @param clearedFields fields explicitly invalidated by this partial update
     */
    public PartitionStateUpdate {
        values = Map.copyOf(values);
        clearedFields = Set.copyOf(clearedFields);
    }
}
