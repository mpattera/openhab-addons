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

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openhab.binding.kseniasecurity.internal.KseniaBindingConstants;
import org.openhab.binding.kseniasecurity.internal.state.PartitionArmingState;
import org.openhab.binding.kseniasecurity.internal.state.PartitionSnapshot;
import org.openhab.binding.kseniasecurity.internal.state.PartitionStateField;
import org.openhab.binding.kseniasecurity.internal.state.ZoneCondition;
import org.openhab.binding.kseniasecurity.internal.state.ZoneSnapshot;
import org.openhab.binding.kseniasecurity.internal.state.ZoneStateField;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.types.RefreshType;

/**
 * Tests that newly linked items receive the semantic state already known by their child Thing handler.
 *
 * @author Michele Pattera - Initial contribution
 */
public class KseniaChildHandlerRefreshTest {

    @Test
    public void testZoneRefreshReplaysOnlyTheRequestedCachedChannel() {
        Thing thing = newThing("zone", "entrance", 2);
        ThingHandlerCallback callback = nonNullMock(ThingHandlerCallback.class);
        KseniaZoneHandler handler = new KseniaZoneHandler(thing);
        handler.setCallback(callback);
        handler.initialize();
        clearInvocations(callback);

        handler.onZoneStateUpdated(
                new ZoneSnapshot(2, Map.of(ZoneStateField.REALTIME_STATE, ZoneCondition.REST.name())));
        clearInvocations(callback);

        ChannelUID channelUID = new ChannelUID(thing.getUID(), KseniaBindingConstants.CHANNEL_ZONE_REST_STATUS);
        handler.handleCommand(channelUID, RefreshType.REFRESH);

        verify(callback).stateUpdated(channelUID, OnOffType.ON);
    }

    @Test
    public void testPartitionRefreshReplaysOnlyTheRequestedCachedChannel() {
        Thing thing = newThing("partition", "home", 1);
        ThingHandlerCallback callback = nonNullMock(ThingHandlerCallback.class);
        KseniaPartitionHandler handler = new KseniaPartitionHandler(thing);
        handler.setCallback(callback);
        handler.initialize();
        clearInvocations(callback);

        handler.onPartitionStateUpdated(new PartitionSnapshot(1,
                Map.of(PartitionStateField.ARMING_STATE, PartitionArmingState.ARMED_IMMEDIATE.name())));
        clearInvocations(callback);

        ChannelUID channelUID = new ChannelUID(thing.getUID(), KseniaBindingConstants.CHANNEL_PARTITION_ARM_STATUS);
        handler.handleCommand(channelUID, RefreshType.REFRESH);

        verify(callback).stateUpdated(channelUID, OnOffType.ON);
    }

    @Test
    public void testRefreshDoesNotReplayStateAfterConnectionInvalidation() {
        Thing thing = newThing("zone", "entrance", 2);
        ThingHandlerCallback callback = nonNullMock(ThingHandlerCallback.class);
        KseniaZoneHandler handler = new KseniaZoneHandler(thing);
        handler.setCallback(callback);
        handler.initialize();
        clearInvocations(callback);

        handler.onZoneStateUpdated(
                new ZoneSnapshot(2, Map.of(ZoneStateField.REALTIME_STATE, ZoneCondition.REST.name())));
        handler.onZoneStateInvalidated();
        clearInvocations(callback);

        ChannelUID channelUID = new ChannelUID(thing.getUID(), KseniaBindingConstants.CHANNEL_ZONE_REST_STATUS);
        handler.handleCommand(channelUID, RefreshType.REFRESH);

        verifyNoInteractions(callback);
    }

    private static Thing newThing(String type, String id, int entityId) {
        Thing thing = nonNullMock(Thing.class);
        ThingUID thingUID = new ThingUID(KseniaBindingConstants.BINDING_ID, type, id);
        when(thing.getUID()).thenReturn(thingUID);
        when(thing.getConfiguration()).thenReturn(new Configuration(Map.of("id", entityId)));
        return thing;
    }

    /**
     * Mockito does not provide JDT nullness annotations, although a successful mock creation returns an instance.
     */
    @SuppressWarnings("null")
    private static <T> T nonNullMock(Class<T> type) {
        return mock(type);
    }
}
