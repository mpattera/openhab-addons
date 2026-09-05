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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link KseniaPanelStateDispatcher}.
 *
 * @author Michele Pattera - Initial contribution
 */
public class KseniaPanelStateDispatcherTest {

    @Test
    public void testZoneUpdatesAreMergedAndReplayedToLateListeners() {
        KseniaPanelStateDispatcher dispatcher = new KseniaPanelStateDispatcher();
        dispatcher.updateZone(new ZoneStateUpdate(2, Map.of(ZoneStateField.REALTIME_STATE, "REST")));

        List<ZoneSnapshot> received = new ArrayList<>();
        dispatcher.addZoneListener(2, received::add);

        assertEquals(1, received.size());
        assertEquals(ZoneCondition.REST, received.getFirst().condition());
        assertNull(received.getFirst().alarmCycleState());

        dispatcher.updateZone(new ZoneStateUpdate(2, Map.of(ZoneStateField.ALARM_STATE, "ACTIVE")));

        assertEquals(2, received.size());
        ZoneSnapshot mergedSnapshot = received.getLast();
        assertEquals(ZoneCondition.REST, mergedSnapshot.condition());
        assertEquals(ZoneCycleState.ACTIVE, mergedSnapshot.alarmCycleState());
    }

    @Test
    public void testClearingSnapshotsRetainsListenersAndDoesNotRetainOldFields() {
        KseniaPanelStateDispatcher dispatcher = new KseniaPanelStateDispatcher();
        List<ZoneSnapshot> received = new ArrayList<>();
        AtomicInteger invalidations = new AtomicInteger();
        dispatcher.addZoneListener(2, new ZoneStateListener() {
            @Override
            public void onZoneStateUpdated(ZoneSnapshot state) {
                received.add(state);
            }

            @Override
            public void onZoneStateInvalidated() {
                invalidations.incrementAndGet();
            }
        });

        dispatcher.updateZone(new ZoneStateUpdate(2, Map.of(ZoneStateField.BYPASS_STATE, "NOT_BYPASSED")));
        dispatcher.clearSnapshots();
        dispatcher.updateZone(new ZoneStateUpdate(2, Map.of(ZoneStateField.REALTIME_STATE, "ALARM")));

        assertEquals(1, invalidations.get());
        assertEquals(2, received.size());
        ZoneSnapshot newSessionSnapshot = received.getLast();
        assertNull(newSessionSnapshot.bypassMode());
        assertEquals(ZoneCondition.ALARM, newSessionSnapshot.condition());
    }

    @Test
    public void testClearedPartitionFieldsDoNotRemainInTheMergedSnapshot() {
        KseniaPanelStateDispatcher dispatcher = new KseniaPanelStateDispatcher();
        List<PartitionSnapshot> received = new ArrayList<>();
        dispatcher.addPartitionListener(1, received::add);

        dispatcher.updatePartition(new PartitionStateUpdate(1,
                Map.of(PartitionStateField.ARMING_STATE, "ENTRY_DELAY", PartitionStateField.DELAY_REMAINING, "30")));
        dispatcher.updatePartition(new PartitionStateUpdate(1, Map.of(PartitionStateField.ARMING_STATE, "DISARMED"),
                Set.of(PartitionStateField.DELAY_REMAINING)));

        assertEquals(2, received.size());
        PartitionSnapshot mergedSnapshot = received.getLast();
        assertEquals(PartitionArmingState.DISARMED, mergedSnapshot.armingState());
        assertNull(mergedSnapshot.delayRemainingSeconds());
    }

    @Test
    public void testListenersOnlyReceiveTheirRegisteredIdAndCanBeRemoved() {
        KseniaPanelStateDispatcher dispatcher = new KseniaPanelStateDispatcher();
        List<PartitionSnapshot> firstPartition = new ArrayList<>();
        List<PartitionSnapshot> secondPartition = new ArrayList<>();
        PartitionStateListener firstListener = firstPartition::add;

        dispatcher.addPartitionListener(1, firstListener);
        dispatcher.addPartitionListener(2, secondPartition::add);
        dispatcher.updatePartition(new PartitionStateUpdate(1, Map.of(PartitionStateField.ARMING_STATE, "DISARMED")));

        assertEquals(1, firstPartition.size());
        assertEquals(0, secondPartition.size());

        dispatcher.removePartitionListener(1, firstListener);
        dispatcher.updatePartition(new PartitionStateUpdate(1, Map.of(PartitionStateField.ALARM_STATE, "ACTIVE")));

        assertEquals(1, firstPartition.size());
    }
}
