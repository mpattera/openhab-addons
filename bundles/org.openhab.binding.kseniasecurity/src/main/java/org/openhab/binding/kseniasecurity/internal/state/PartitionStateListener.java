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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Receives complete state snapshots for a partition.
 *
 * @author Michele Pattera - Initial contribution
 */
@FunctionalInterface
@NonNullByDefault
public interface PartitionStateListener {

    /**
     * Called when the panel has a new state for the registered partition.
     *
     * @param state the complete state snapshot
     */
    void onPartitionStateUpdated(PartitionSnapshot state);

    /**
     * Called when the bridge discards the cached state for a new panel session.
     *
     * <p>
     * Implementations should no longer expose the previously received state as current.
     */
    default void onPartitionStateInvalidated() {
    }
}
