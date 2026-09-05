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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Holds static panel inventory independently from the real-time state session.
 *
 * <p>
 * Inventory is valid only for the current panel connection. It is replaced by a successful inventory response and
 * invalidated when that connection ends, while registered child listeners remain available for the next response.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public final class KseniaPanelInventory {

    private final Object zoneLock = new Object();
    private final Map<Integer, ZoneMetadataSnapshot> zones = new HashMap<>();
    private final Map<Integer, Set<ZoneMetadataListener>> zoneListeners = new HashMap<>();
    private boolean zoneInventoryLoaded;

    private final Object partitionLock = new Object();
    private final Map<Integer, PartitionMetadataSnapshot> partitions = new HashMap<>();
    private final Map<Integer, Set<PartitionMetadataListener>> partitionListeners = new HashMap<>();
    private boolean partitionInventoryLoaded;

    /**
     * Registers a listener and replays known metadata immediately.
     *
     * @param zoneId the panel zone identifier
     * @param listener the listener to register
     */
    public void addZoneListener(int zoneId, ZoneMetadataListener listener) {
        synchronized (zoneLock) {
            @Nullable
            Set<ZoneMetadataListener> listeners = zoneListeners.get(zoneId);
            if (listeners == null) {
                listeners = new HashSet<>();
                zoneListeners.put(zoneId, listeners);
            }
            listeners.add(listener);
            @Nullable
            ZoneMetadataSnapshot snapshot = zones.get(zoneId);
            if (snapshot != null) {
                listener.onZoneMetadataUpdated(snapshot);
            }
        }
    }

    /**
     * Removes a zone metadata listener.
     *
     * @param zoneId the panel zone identifier
     * @param listener the listener to remove
     */
    public void removeZoneListener(int zoneId, ZoneMetadataListener listener) {
        synchronized (zoneLock) {
            removeListener(zoneListeners, zoneId, listener);
        }
    }

    /**
     * Registers a listener and replays known metadata immediately.
     *
     * @param partitionId the panel partition identifier
     * @param listener the listener to register
     */
    public void addPartitionListener(int partitionId, PartitionMetadataListener listener) {
        synchronized (partitionLock) {
            @Nullable
            Set<PartitionMetadataListener> listeners = partitionListeners.get(partitionId);
            if (listeners == null) {
                listeners = new HashSet<>();
                partitionListeners.put(partitionId, listeners);
            }
            listeners.add(listener);
            @Nullable
            PartitionMetadataSnapshot snapshot = partitions.get(partitionId);
            if (snapshot != null) {
                listener.onPartitionMetadataUpdated(snapshot);
            }
        }
    }

    /**
     * Removes a partition metadata listener.
     *
     * @param partitionId the panel partition identifier
     * @param listener the listener to remove
     */
    public void removePartitionListener(int partitionId, PartitionMetadataListener listener) {
        synchronized (partitionLock) {
            removeListener(partitionListeners, partitionId, listener);
        }
    }

    /**
     * Replaces every inventory section explicitly included in the decoded response.
     *
     * @param updates the decoded inventory sections
     */
    public void apply(PanelInventoryUpdates updates) {
        if (updates.zonesIncluded()) {
            replaceZones(updates.zones());
        }
        if (updates.partitionsIncluded()) {
            replacePartitions(updates.partitions());
        }
    }

    /**
     * Indicates whether both zone and partition sections have been loaded successfully.
     *
     * @return {@code true} when the initial inventory is complete, including an explicitly empty section
     */
    public boolean isComplete() {
        synchronized (zoneLock) {
            synchronized (partitionLock) {
                return zoneInventoryLoaded && partitionInventoryLoaded;
            }
        }
    }

    /**
     * Invalidates the current connection's inventory while keeping registered listeners.
     *
     * <p>
     * A subsequent successful inventory response is therefore replayed to the same child handlers.
     */
    public void clearSnapshots() {
        synchronized (zoneLock) {
            zones.clear();
            zoneInventoryLoaded = false;
            for (Set<ZoneMetadataListener> listeners : zoneListeners.values()) {
                for (ZoneMetadataListener listener : listeners) {
                    listener.onZoneMetadataInvalidated();
                }
            }
        }
        synchronized (partitionLock) {
            partitions.clear();
            partitionInventoryLoaded = false;
            for (Set<PartitionMetadataListener> listeners : partitionListeners.values()) {
                for (PartitionMetadataListener listener : listeners) {
                    listener.onPartitionMetadataInvalidated();
                }
            }
        }
    }

    /** Clears inventory and listeners when the bridge is disposed. */
    public void clear() {
        synchronized (zoneLock) {
            zones.clear();
            zoneListeners.clear();
            zoneInventoryLoaded = false;
        }
        synchronized (partitionLock) {
            partitions.clear();
            partitionListeners.clear();
            partitionInventoryLoaded = false;
        }
    }

    private void replaceZones(Map<Integer, ZoneMetadataSnapshot> updatedZones) {
        synchronized (zoneLock) {
            zones.clear();
            zones.putAll(updatedZones);
            zoneInventoryLoaded = true;
            for (Map.Entry<Integer, Set<ZoneMetadataListener>> entry : zoneListeners.entrySet()) {
                @Nullable
                ZoneMetadataSnapshot snapshot = zones.get(entry.getKey());
                for (ZoneMetadataListener listener : entry.getValue()) {
                    if (snapshot == null) {
                        listener.onZoneMetadataInvalidated();
                    } else {
                        listener.onZoneMetadataUpdated(snapshot);
                    }
                }
            }
        }
    }

    private void replacePartitions(Map<Integer, PartitionMetadataSnapshot> updatedPartitions) {
        synchronized (partitionLock) {
            partitions.clear();
            partitions.putAll(updatedPartitions);
            partitionInventoryLoaded = true;
            for (Map.Entry<Integer, Set<PartitionMetadataListener>> entry : partitionListeners.entrySet()) {
                @Nullable
                PartitionMetadataSnapshot snapshot = partitions.get(entry.getKey());
                for (PartitionMetadataListener listener : entry.getValue()) {
                    if (snapshot == null) {
                        listener.onPartitionMetadataInvalidated();
                    } else {
                        listener.onPartitionMetadataUpdated(snapshot);
                    }
                }
            }
        }
    }

    private static <T> void removeListener(Map<Integer, Set<T>> listenersById, int id, T listener) {
        @Nullable
        Set<T> listeners = listenersById.get(id);
        if (listeners == null) {
            return;
        }

        listeners.remove(listener);
        if (listeners.isEmpty()) {
            listenersById.remove(id);
        }
    }
}
