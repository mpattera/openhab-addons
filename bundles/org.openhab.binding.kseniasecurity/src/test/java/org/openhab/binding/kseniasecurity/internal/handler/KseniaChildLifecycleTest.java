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
package org.openhab.binding.kseniasecurity.internal.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.openhab.binding.kseniasecurity.internal.state.PartitionMetadataField;
import org.openhab.binding.kseniasecurity.internal.state.PartitionMetadataSnapshot;
import org.openhab.binding.kseniasecurity.internal.state.PartitionSnapshot;
import org.openhab.binding.kseniasecurity.internal.state.PartitionStateField;
import org.openhab.binding.kseniasecurity.internal.state.ZoneMetadataField;
import org.openhab.binding.kseniasecurity.internal.state.ZoneMetadataSnapshot;
import org.openhab.binding.kseniasecurity.internal.state.ZoneSnapshot;
import org.openhab.binding.kseniasecurity.internal.state.ZoneStateField;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandlerCallback;

/**
 * Exercises both child types through reconnects and asynchronous framework notifications.
 *
 * @author Michele Pattera - Initial contribution
 */
public class KseniaChildLifecycleTest {

    @Test
    public void testRecoveryWaitsForFreshEntityStateNotJustBridgeOrMetadata() {
        for (String type : List.of("zone", "partition")) {
            ChildFixture child = newChild(type);
            child.publishState().run();
            assertStatus(child, ThingStatus.ONLINE, ThingStatusDetail.NONE);
            when(child.bridge().getStatus()).thenReturn(ThingStatus.OFFLINE);
            child.invalidate().run();
            child.handler().bridgeStatusChanged(status(ThingStatus.OFFLINE));
            assertStatus(child, ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);

            when(child.bridge().getStatus()).thenReturn(ThingStatus.ONLINE);
            child.handler().bridgeStatusChanged(status(ThingStatus.ONLINE));
            assertStatus(child, ThingStatus.UNKNOWN, ThingStatusDetail.NONE);
            child.publishMetadata().run();
            child.publishEmptyState().run();
            assertStatus(child, ThingStatus.UNKNOWN, ThingStatusDetail.NONE);
            child.publishState().run();
            assertStatus(child, ThingStatus.ONLINE, ThingStatusDetail.NONE);
        }
    }

    @Test
    public void testRegistrationSnapshotBeforeBridgeOnlineIsRetainedButNotMarkedOnlineYet() {
        for (String type : List.of("zone", "partition")) {
            ChildFixture child = newChild(type);
            when(child.bridge().getStatus()).thenReturn(ThingStatus.OFFLINE);
            child.publishState().run();
            assertStatus(child, ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            when(child.bridge().getStatus()).thenReturn(ThingStatus.ONLINE);
            child.handler().bridgeStatusChanged(status(ThingStatus.ONLINE));
            assertStatus(child, ThingStatus.ONLINE, ThingStatusDetail.NONE);
        }
    }

    @Test
    public void testLateBridgeNotificationsUseCurrentStatusWithoutDiscardingFreshState() {
        for (String type : List.of("zone", "partition")) {
            ChildFixture child = newChild(type);
            child.publishState().run();
            child.handler().bridgeStatusChanged(status(ThingStatus.OFFLINE));
            assertStatus(child, ThingStatus.ONLINE, ThingStatusDetail.NONE);
            when(child.bridge().getStatus()).thenReturn(ThingStatus.OFFLINE);
            child.handler().bridgeStatusChanged(status(ThingStatus.ONLINE));
            assertStatus(child, ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    @Test
    public void testDisposalIgnoresLateUpdatesAndReinitializationDropsOldSnapshots() {
        for (String type : List.of("zone", "partition")) {
            ChildFixture child = newChild(type);
            child.publishState().run();
            child.publishMetadata().run();
            child.handler().dispose();
            clearInvocations(child.callback());
            child.publishState().run();
            child.publishMetadata().run();
            child.invalidate().run();
            child.handler().bridgeStatusChanged(status(ThingStatus.ONLINE));
            verifyNoInteractions(child.callback());

            child.handler().initialize();
            assertStatus(child, ThingStatus.UNKNOWN, ThingStatusDetail.NONE);
        }
    }

    @Test
    public void testBridgeNotificationsDoNotOverwriteConfigurationErrors() {
        for (String type : List.of("zone", "partition")) {
            ChildFixture child = newChild(type, 0);
            child.handler().bridgeStatusChanged(status(ThingStatus.ONLINE));
            child.publishState().run();
            assertStatus(child, ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
        }
    }

    private static void assertStatus(ChildFixture child, ThingStatus status, ThingStatusDetail detail) {
        assertEquals(status, child.status().get().getStatus());
        assertEquals(detail, child.status().get().getStatusDetail());
    }

    private static ThingStatusInfo status(ThingStatus status) {
        return new ThingStatusInfo(status, ThingStatusDetail.NONE, null);
    }

    private static ChildFixture newChild(String type) {
        return newChild(type, 1);
    }

    private static ChildFixture newChild(String type, int id) {
        Thing thing = Objects.requireNonNull(mock(Thing.class));
        Bridge bridge = Objects.requireNonNull(mock(Bridge.class));
        ThingHandlerCallback callback = Objects.requireNonNull(mock(ThingHandlerCallback.class));
        ThingUID bridgeUid = new ThingUID("kseniasecurity:panel:test");
        when(thing.getUID()).thenReturn(new ThingUID("kseniasecurity", type, "test"));
        when(thing.getBridgeUID()).thenReturn(bridgeUid);
        when(thing.getConfiguration()).thenReturn(new Configuration(Map.of("id", id)));
        when(callback.getBridge(bridgeUid)).thenReturn(bridge);
        when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);
        AtomicReference<ThingStatusInfo> status = new AtomicReference<>(status(ThingStatus.INITIALIZING));
        when(thing.getStatusInfo()).thenAnswer(invocation -> status.get());
        when(thing.getStatus()).thenAnswer(invocation -> status.get().getStatus());
        doAnswer(invocation -> {
            status.set(invocation.getArgument(1));
            return null;
        }).when(callback).statusUpdated(eq(thing), any(ThingStatusInfo.class));
        ChildFixture fixture;
        if ("zone".equals(type)) {
            KseniaZoneHandler handler = new KseniaZoneHandler(thing);
            fixture = new ChildFixture(thing, bridge, callback, handler, status,
                    () -> handler
                            .onZoneStateUpdated(new ZoneSnapshot(1, Map.of(ZoneStateField.REALTIME_STATE, "REST"))),
                    () -> handler.onZoneStateUpdated(ZoneSnapshot.initial(1)),
                    () -> handler.onZoneMetadataUpdated(
                            new ZoneMetadataSnapshot(1, Map.of(ZoneMetadataField.DESCRIPTION, "Entrance"))),
                    handler::onZoneStateInvalidated);
        } else {
            KseniaPartitionHandler handler = new KseniaPartitionHandler(thing);
            fixture = new ChildFixture(thing, bridge, callback, handler, status,
                    () -> handler.onPartitionStateUpdated(
                            new PartitionSnapshot(1, Map.of(PartitionStateField.ARMING_STATE, "DISARMED"))),
                    () -> handler.onPartitionStateUpdated(PartitionSnapshot.initial(1)),
                    () -> handler.onPartitionMetadataUpdated(
                            new PartitionMetadataSnapshot(1, Map.of(PartitionMetadataField.DESCRIPTION, "Home"))),
                    handler::onPartitionStateInvalidated);
        }
        fixture.handler().setCallback(callback);
        fixture.handler().initialize();
        return fixture;
    }

    private record ChildFixture(Thing thing, Bridge bridge, ThingHandlerCallback callback, BaseThingHandler handler,
            AtomicReference<ThingStatusInfo> status, Runnable publishState, Runnable publishEmptyState,
            Runnable publishMetadata, Runnable invalidate) {
    }
}
