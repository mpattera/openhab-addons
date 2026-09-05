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
 * Caches panel state and dispatches complete snapshots to interested child handlers.
 *
 * <p>
 * State is merged before it is dispatched, because real-time notifications may contain only the fields that changed.
 * Delivery for each entity type is serialized to prevent a late-listener replay from being delivered after a newer
 * state update.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public class KseniaPanelStateDispatcher {

    private final Object zoneLock = new Object();
    private final Map<Integer, ZoneSnapshot> zoneSnapshots = new HashMap<>();
    private final Map<Integer, Set<ZoneStateListener>> zoneListeners = new HashMap<>();

    private final Object partitionLock = new Object();
    private final Map<Integer, PartitionSnapshot> partitionSnapshots = new HashMap<>();
    private final Map<Integer, Set<PartitionStateListener>> partitionListeners = new HashMap<>();

    /**
     * Registers a listener and immediately replays the last state, if one is known.
     *
     * @param zoneId the panel zone identifier
     * @param listener the listener to register
     */
    public void addZoneListener(int zoneId, ZoneStateListener listener) {
        synchronized (zoneLock) {
            @Nullable
            Set<ZoneStateListener> listeners = zoneListeners.get(zoneId);
            if (listeners == null) {
                listeners = new HashSet<>();
                zoneListeners.put(zoneId, listeners);
            }
            listeners.add(listener);
            @Nullable
            ZoneSnapshot snapshot = zoneSnapshots.get(zoneId);
            if (snapshot != null) {
                listener.onZoneStateUpdated(snapshot);
            }
        }
    }

    /**
     * Removes a previously registered zone listener.
     *
     * @param zoneId the panel zone identifier
     * @param listener the listener to remove
     */
    public void removeZoneListener(int zoneId, ZoneStateListener listener) {
        synchronized (zoneLock) {
            Set<ZoneStateListener> listeners = zoneListeners.get(zoneId);
            if (listeners != null) {
                listeners.remove(listener);
                if (listeners.isEmpty()) {
                    zoneListeners.remove(zoneId);
                }
            }
        }
    }

    /**
     * Merges and dispatches a zone update.
     *
     * @param update the partial update received from the panel
     */
    public void updateZone(ZoneStateUpdate update) {
        if (update.id() <= 0) {
            return;
        }

        synchronized (zoneLock) {
            ZoneSnapshot current = zoneSnapshots.get(update.id());
            ZoneSnapshot snapshot = (current == null ? ZoneSnapshot.initial(update.id()) : current).merge(update);
            zoneSnapshots.put(update.id(), snapshot);
            for (ZoneStateListener listener : zoneListeners.getOrDefault(update.id(), Set.of())) {
                listener.onZoneStateUpdated(snapshot);
            }
        }
    }

    /**
     * Registers a listener and immediately replays the last state, if one is known.
     *
     * @param partitionId the panel partition identifier
     * @param listener the listener to register
     */
    public void addPartitionListener(int partitionId, PartitionStateListener listener) {
        synchronized (partitionLock) {
            @Nullable
            Set<PartitionStateListener> listeners = partitionListeners.get(partitionId);
            if (listeners == null) {
                listeners = new HashSet<>();
                partitionListeners.put(partitionId, listeners);
            }
            listeners.add(listener);
            @Nullable
            PartitionSnapshot snapshot = partitionSnapshots.get(partitionId);
            if (snapshot != null) {
                listener.onPartitionStateUpdated(snapshot);
            }
        }
    }

    /**
     * Removes a previously registered partition listener.
     *
     * @param partitionId the panel partition identifier
     * @param listener the listener to remove
     */
    public void removePartitionListener(int partitionId, PartitionStateListener listener) {
        synchronized (partitionLock) {
            Set<PartitionStateListener> listeners = partitionListeners.get(partitionId);
            if (listeners != null) {
                listeners.remove(listener);
                if (listeners.isEmpty()) {
                    partitionListeners.remove(partitionId);
                }
            }
        }
    }

    /**
     * Merges and dispatches a partition update.
     *
     * @param update the partial update received from the panel
     */
    public void updatePartition(PartitionStateUpdate update) {
        if (update.id() <= 0) {
            return;
        }

        synchronized (partitionLock) {
            PartitionSnapshot current = partitionSnapshots.get(update.id());
            PartitionSnapshot snapshot = (current == null ? PartitionSnapshot.initial(update.id()) : current)
                    .merge(update);
            partitionSnapshots.put(update.id(), snapshot);
            for (PartitionStateListener listener : partitionListeners.getOrDefault(update.id(), Set.of())) {
                listener.onPartitionStateUpdated(snapshot);
            }
        }
    }

    /**
     * Discards all cached panel state while keeping the registered child handlers.
     *
     * <p>
     * This is used when a WebSocket session ends, so a child cannot continue to expose values received in the
     * previous session.
     */
    public void clearSnapshots() {
        synchronized (zoneLock) {
            zoneSnapshots.clear();
            for (Set<ZoneStateListener> listeners : zoneListeners.values()) {
                for (ZoneStateListener listener : listeners) {
                    listener.onZoneStateInvalidated();
                }
            }
        }
        synchronized (partitionLock) {
            partitionSnapshots.clear();
            for (Set<PartitionStateListener> listeners : partitionListeners.values()) {
                for (PartitionStateListener listener : listeners) {
                    listener.onPartitionStateInvalidated();
                }
            }
        }
    }

    /** Clears cached state and registered listeners when the bridge is disposed. */
    public void clear() {
        synchronized (zoneLock) {
            zoneSnapshots.clear();
            zoneListeners.clear();
        }
        synchronized (partitionLock) {
            partitionSnapshots.clear();
            partitionListeners.clear();
        }
    }
}
