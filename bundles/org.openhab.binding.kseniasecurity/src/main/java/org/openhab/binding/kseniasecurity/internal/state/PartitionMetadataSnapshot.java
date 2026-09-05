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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Static metadata known for one alarm partition.
 *
 * <p>
 * The field map deliberately mirrors the state snapshot model: new metadata can be added without growing a
 * positional constructor or changing the listener contract.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public record PartitionMetadataSnapshot(int id, Map<PartitionMetadataField, String> values) {

    /**
     * Creates a snapshot with an immutable field map.
     *
     * @param id the panel partition identifier
     * @param values the metadata values indexed by semantic field
     */
    public PartitionMetadataSnapshot {
        values = Map.copyOf(values);
    }

    /**
     * Returns a metadata value, if it was supplied by the panel.
     *
     * @param field the semantic field to look up
     * @return the value, or {@code null} when unavailable
     */
    public @Nullable String get(PartitionMetadataField field) {
        return values.get(field);
    }
}
