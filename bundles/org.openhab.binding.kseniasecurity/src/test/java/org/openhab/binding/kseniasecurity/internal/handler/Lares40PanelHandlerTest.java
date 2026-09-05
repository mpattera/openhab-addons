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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLHandshakeException;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openhab.binding.kseniasecurity.internal.KseniaBindingConstants;
import org.openhab.binding.kseniasecurity.internal.KseniaWebSocketManager;
import org.openhab.binding.kseniasecurity.internal.protocol.lares40.Lares40Command;
import org.openhab.binding.kseniasecurity.internal.protocol.lares40.Lares40CommandCodec;
import org.openhab.binding.kseniasecurity.internal.protocol.lares40.Lares40PendingReadRequest;
import org.openhab.binding.kseniasecurity.internal.protocol.lares40.Lares40ProtocolConstants;
import org.openhab.binding.kseniasecurity.internal.protocol.lares40.Lares40ReadOperation;
import org.openhab.binding.kseniasecurity.internal.protocol.lares40.Lares40ReadRequest;
import org.openhab.binding.kseniasecurity.internal.protocol.lares40.Lares40ReadRequestManager;
import org.openhab.binding.kseniasecurity.internal.state.KseniaPanelInventory;
import org.openhab.binding.kseniasecurity.internal.state.PanelInventoryUpdates;
import org.openhab.binding.kseniasecurity.internal.state.PartitionMetadataField;
import org.openhab.binding.kseniasecurity.internal.state.PartitionMetadataSnapshot;
import org.openhab.binding.kseniasecurity.internal.state.PartitionSnapshot;
import org.openhab.binding.kseniasecurity.internal.state.ZoneCondition;
import org.openhab.binding.kseniasecurity.internal.state.ZoneMetadataField;
import org.openhab.binding.kseniasecurity.internal.state.ZoneMetadataSnapshot;
import org.openhab.binding.kseniasecurity.internal.state.ZoneSnapshot;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.net.http.WebSocketFactory;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.osgi.framework.BundleContext;

/**
 * Tests for {@link Lares40PanelHandler} protocol response handling and connection failure descriptions.
 *
 * @author Michele Pattera - Initial contribution
 */
public class Lares40PanelHandlerTest {

    private final List<Lares40PanelHandler> handlers = new ArrayList<>();

    @AfterEach
    public void disposeHandlers() {
        handlers.forEach(Lares40PanelHandler::dispose);
    }

    @Test
    public void testRenamingEitherChildReplaysDataAndContinuesReceivingChanges() {
        for (String type : List.of("zone", "partition")) {
            CapturingPanelHandler panel = newCapturingPanelHandler();
            panel.activateWithChildStates();
            ChildThing child = new ChildThing(panel, type);
            child.assertState(false);
            assertEquals(new StringType("zone".equals(type) ? "Entrance" : "Home"), child.states.get("description"));
            Thing oldThing = child.handler.getThing();
            child.states.clear();
            int sentCount = panel.sent.size();

            child.handler.thingUpdated(child.updatedThing(child.initialId, "Renamed"));

            child.assertState(false);
            assertEquals(new StringType("zone".equals(type) ? "Entrance" : "Home"), child.states.get("description"));
            assertEquals(sentCount, panel.sent.size(), "A rename does not need another panel request");
            // Delayed framework lifecycle callbacks must not remove the subscription owned by initialize().
            panel.childHandlerDisposed(child.handler, oldThing);
            panel.publishChildChange(type, child.initialId, true);
            child.assertState(true);
        }
    }

    @Test
    public void testChildIdChangesThroughBothConfigurationUpdatePaths() {
        for (String type : List.of("zone", "partition")) {
            for (boolean wholeThingUpdate : List.of(false, true)) {
                CapturingPanelHandler panel = newCapturingPanelHandler();
                panel.activateWithChildStates();
                ChildThing child = new ChildThing(panel, type);
                panel.publishChildChange(type, 5, true);

                if (wholeThingUpdate) {
                    child.handler.thingUpdated(child.updatedThing(5, "Other entity"));
                } else {
                    child.handler.handleConfigurationUpdate(Map.of("id", 5));
                }

                child.assertState(true);
                assertEquals(UnDefType.UNDEF, child.states.get("description"));
                clearInvocations(child.handler);
                panel.publishChildChange(type, child.initialId, true);
                child.verifyNoStateDelivery();
                panel.publishChildChange(type, 5, false);
                child.assertState(false);
            }
        }
    }

    @Test
    public void testDisposedChildIsRemovedAndReplacementWithSameUidReceivesUpdates() {
        for (String type : List.of("zone", "partition")) {
            CapturingPanelHandler panel = newCapturingPanelHandler();
            panel.activateWithChildStates();
            ChildThing removed = new ChildThing(panel, type);
            removed.handler.dispose();
            clearInvocations(removed.handler, removed.callback);
            ChildThing replacement = new ChildThing(panel, type);
            panel.childHandlerDisposed(removed.handler, removed.handler.getThing());
            panel.publishChildChange(type, replacement.initialId, true);

            removed.verifyNoStateDelivery();
            verifyNoInteractions(removed.callback);
            replacement.assertState(true);
        }
    }

    @Test
    public void testMovingChildToAnotherBridgeUnsubscribesFromTheOriginalBridge() {
        for (String type : List.of("zone", "partition")) {
            CapturingPanelHandler first = newCapturingPanelHandler();
            first.activateWithChildStates();
            CapturingPanelHandler second = newCapturingPanelHandler();
            when(second.getThing().getUID()).thenReturn(new ThingUID("kseniasecurity:panel:second"));
            second.activateWithChildStates();
            ChildThing child = new ChildThing(first, type);
            ThingUID secondUid = second.getThing().getUID();
            when(child.callback.getBridge(secondUid)).thenReturn(second.getThing());
            Thing moved = child.updatedThing(child.initialId, "Moved");
            when(moved.getBridgeUID()).thenReturn(secondUid);
            child.handler.thingUpdated(moved);
            child.assertState(false);
            clearInvocations(child.handler);

            first.publishChildChange(type, child.initialId, true);
            child.verifyNoStateDelivery();
            second.publishChildChange(type, child.initialId, true);
            child.assertState(true);
        }
    }

    @Test
    public void testBridgeReconfigurationKeepsSubscriptionsButDiscardsSessionData() {
        CapturingPanelHandler panel = newCapturingPanelHandler();
        panel.activateWithChildStates();
        List<ChildThing> children = List.of(new ChildThing(panel, "zone"), new ChildThing(panel, "partition"));
        panel.nextConnection = new CompletableFuture<>();

        panel.handleConfigurationUpdate(Map.of("responseTimeout", 120));
        panel.completeConnection();
        for (ChildThing child : children) {
            assertEquals(UnDefType.UNDEF, child.states.get("realtimeState"));
            assertEquals(UnDefType.UNDEF, child.states.get("description"));
        }
        panel.activateWithChildStates();
        for (ChildThing child : children) {
            child.handler.bridgeStatusChanged(panel.getThing().getStatusInfo());
            child.assertState(false);
            panel.publishChildChange(child.type, child.initialId, true);
            child.assertState(true);
        }
        panel.dispose();
        assertEquals(120, panel.scheduledTasks.getLast().delaySeconds());
    }

    /** Real child handlers and their inherited update methods, connected to the bridge's actual dispatcher. */
    private static class ChildThing {
        private final String type;
        private final int initialId;
        private final ThingUID uid;
        private final ThingUID bridgeUid;
        private final ThingHandlerCallback callback = Objects.requireNonNull(mock(ThingHandlerCallback.class));
        private final Map<String, State> states = new HashMap<>();
        private final AtomicReference<ThingStatusInfo> status = new AtomicReference<>(
                new ThingStatusInfo(ThingStatus.INITIALIZING, ThingStatusDetail.NONE, null));
        private final BaseThingHandler handler;

        ChildThing(CapturingPanelHandler panel, String type) {
            this.type = type;
            initialId = "zone".equals(type) ? 2 : 1;
            uid = new ThingUID("kseniasecurity", type, "test", "child");
            bridgeUid = panel.getThing().getUID();
            Thing thing = updatedThing(initialId, "Original");
            handler = "zone".equals(type) ? spy(new KseniaZoneHandler(thing)) : spy(new KseniaPartitionHandler(thing));
            when(thing.getHandler()).thenReturn(handler);
            when(callback.getBridge(bridgeUid)).thenReturn(panel.getThing());
            doAnswer(invocation -> {
                status.set(invocation.getArgument(1));
                return null;
            }).when(callback).statusUpdated(any(Thing.class), any(ThingStatusInfo.class));
            doAnswer(invocation -> {
                states.put(invocation.<ChannelUID> getArgument(0).getId(), invocation.getArgument(1));
                return null;
            }).when(callback).stateUpdated(any(ChannelUID.class), any(State.class));
            handler.setCallback(callback);
            handler.initialize();
        }

        Thing updatedThing(int id, String label) {
            Thing thing = Objects.requireNonNull(mock(Thing.class));
            when(thing.getUID()).thenReturn(uid);
            when(thing.getBridgeUID()).thenReturn(bridgeUid);
            when(thing.getLabel()).thenReturn(label);
            when(thing.getConfiguration()).thenReturn(new Configuration(Map.of("id", id)));
            when(thing.getStatusInfo()).thenAnswer(invocation -> status.get());
            when(thing.getStatus()).thenAnswer(invocation -> status.get().getStatus());
            return thing;
        }

        void assertState(boolean active) {
            assertEquals(ThingStatus.ONLINE, status.get().getStatus());
            assertEquals(
                    new StringType(
                            "zone".equals(type) ? active ? "ALARM" : "REST" : active ? "ARMED_IMMEDIATE" : "DISARMED"),
                    states.get("realtimeState"));
        }

        void verifyNoStateDelivery() {
            if (handler instanceof KseniaZoneHandler zone) {
                verify(zone, never()).onZoneStateUpdated(any(ZoneSnapshot.class));
            } else if (handler instanceof KseniaPartitionHandler partition) {
                verify(partition, never()).onPartitionStateUpdated(any(PartitionSnapshot.class));
            }
        }
    }

    @Test
    public void testDisposeSendsCorrelatedLogoutForBothRolesWithoutBlockingOrChangingThingStatus() {
        for (Map.Entry<String, String> role : Map.of("USER", "USER", "SUPERVISOR", "IP_SUPERV").entrySet()) {
            CapturingPanelHandler handler = newCapturingPanelHandler(role.getKey());
            handler.handlePanelMessage(new Lares40CommandCodec().encode("panel", "client", "LOGIN_RES", 65535,
                    role.getValue(), Map.of("RESULT", "OK", "ID_LOGIN", "1")).orElseThrow());
            handler.handlePanelMessage(encodeInitialInventoryResponse(handler.pendingRead().commandId()));
            handler.handlePanelMessage(encodeRegisterAcknowledgement());
            clearInvocations(handler.callback);

            handler.dispose();

            Lares40Command logout = handler.sent.getLast();
            assertEquals("LOGOUT", logout.command);
            assertEquals(role.getValue(), logout.payloadType);
            assertEquals("{\"ID_LOGIN\":\"1\"}", Objects.requireNonNull(logout.payload).toString());
            assertEquals(90, handler.scheduledTasks.getLast().delaySeconds());
            assertTrue(handler.cleanupTasks.isEmpty());
            verify(handler.session, never()).close();
            handler.connections.getLast().onWebSocketText(encodeLogoutResponse(logout, "OK"));
            verify(handler.scheduledTasks.getLast().future()).cancel(false);
            assertEquals(1, handler.cleanupTasks.size());
            handler.cleanupTasks.removeFirst().run();
            verify(handler.session).close();
            verifyNoInteractions(handler.callback);
        }
    }

    @Test
    public void testLogoutDuringPendingReadIgnoresOldResponsesAndAmbiguousLegacyGeneric() {
        CapturingPanelHandler handler = newCapturingPanelHandler();
        handler.handlePanelMessage(encodeLoginResponse());
        Lares40PendingReadRequest read = handler.pendingRead();
        ScheduledTask readTimeout = handler.scheduledTasks.getLast();
        handler.queueDiagnosticRead("ALL");
        handler.dispose();
        Lares40Command logout = handler.sent.getLast();
        assertNotEquals(Integer.toString(read.commandId()), logout.id);
        assertNotEquals("65535", logout.id);
        verify(readTimeout.future()).cancel(false);
        clearInvocations(handler.callback);

        KseniaWebSocketManager closing = handler.connections.getLast();
        closing.onWebSocketText(encodeGenericErrorResponse(read.commandId()));
        closing.onWebSocketText(encodeGenericErrorResponse(0));
        closing.onWebSocketText(encodeInitialInventoryResponse(read.commandId()));
        closing.onWebSocketText(encodeRegisterAcknowledgement());
        readTimeout.task().run();
        handler.queueDiagnosticRead("ZONES");
        assertEquals(List.of("READ:MULTI_TYPES", "LOGOUT:IP_SUPERV"), handler.sentCommands());
        assertTrue(handler.cleanupTasks.isEmpty());
        assertFalse(handler.inventory.isComplete());
        assertTrue(handler.readRequests.getPendingRequest().isEmpty());
        verifyNoInteractions(handler.callback);

        closing.onWebSocketText(encodeLogoutResponse(logout, "OK"));
        assertEquals(1, handler.cleanupTasks.size());
    }

    @Test
    public void testLogoutDuringRegisterDoesNotReuseTheRegisterId() {
        CapturingPanelHandler handler = newCapturingPanelHandler();
        handler.handlePanelMessage(encodeLoginResponse());
        handler.handlePanelMessage(encodeInitialInventoryResponse(handler.pendingRead().commandId()));
        handler.dispose();
        assertNotEquals("65535", handler.sent.getLast().id);
        handler.connections.getLast().onWebSocketText(encodeGenericErrorResponse(65535));
        handler.connections.getLast().onWebSocketText(encodeGenericErrorResponse(0));
        assertTrue(handler.cleanupTasks.isEmpty());
        handler.scheduledTasks.getLast().task().run();
        assertEquals(1, handler.cleanupTasks.size());
    }

    @Test
    public void testLogoutFailureGenericAndTimeoutAlwaysCleanUpWithoutRetry() throws Exception {
        for (String outcome : List.of("FAIL", "GENERIC", "LEGACY_GENERIC", "TIMEOUT", "CLOSE", "ERROR")) {
            CapturingPanelHandler handler = newCapturingPanelHandler();
            handler.handlePanelMessage(encodeLoginResponse());
            handler.handlePanelMessage(encodeInitialInventoryResponse(handler.pendingRead().commandId()));
            handler.handlePanelMessage(encodeRegisterAcknowledgement());
            handler.dispose();
            Lares40Command logout = handler.sent.getLast();
            KseniaWebSocketManager closing = handler.connections.getLast();
            ScheduledTask timeout = handler.scheduledTasks.getLast();
            int taskCount = handler.scheduledTasks.size();
            clearInvocations(handler.callback);

            switch (outcome) {
                case "FAIL" -> closing.onWebSocketText(encodeLogoutResponse(logout, "FAIL"));
                case "GENERIC" -> closing.onWebSocketText(
                        encodeGenericErrorResponse(Integer.parseInt(Objects.requireNonNull(logout.id))));
                case "LEGACY_GENERIC" -> closing.onWebSocketText(encodeGenericErrorResponse(0));
                case "CLOSE" -> closing.onWebSocketClose(1000, "closed by panel");
                case "ERROR" -> closing.onWebSocketError(new IllegalStateException("Connection lost"));
                default -> timeout.task().run();
            }
            timeout.task().run();
            handler.dispose();
            assertEquals(1, handler.cleanupTasks.size());
            assertEquals(taskCount, handler.scheduledTasks.size());
            handler.cleanupTasks.getFirst().run();
            verify(handler.clients.getLast()).stop();
            verifyNoInteractions(handler.callback);
        }
    }

    @Test
    public void testUnauthenticatedOrOfflineDisposalDoesNotSendLogout() {
        for (boolean offline : List.of(false, true)) {
            CapturingPanelHandler handler = newCapturingPanelHandler();
            if (offline) {
                handler.handlePanelMessage(encodeLoginResponse());
                handler.handleUnexpectedConnectionClosed(1006, null);
            }
            int sentCount = handler.sent.size();
            handler.dispose();
            assertEquals(sentCount, handler.sent.size());
            assertEquals(1, handler.cleanupTasks.size());
            handler.dispose();
            assertEquals(1, handler.cleanupTasks.size());
        }
    }

    @Test
    public void testLogoutWriteFailureStillSchedulesCleanupWithoutReconnect() {
        CapturingPanelHandler handler = newCapturingPanelHandler();
        handler.handlePanelMessage(encodeLoginResponse());
        RemoteEndpoint remote = handler.session.getRemote();
        doThrow(new IllegalStateException("Write rejected")).when(remote).sendString(anyString(),
                any(WriteCallback.class));
        clearInvocations(handler.callback);
        handler.dispose();
        assertEquals(1, handler.cleanupTasks.size());
        verify(handler.scheduledTasks.getLast().future()).cancel(false);
        assertEquals(90, handler.scheduledTasks.getLast().delaySeconds());
        verifyNoInteractions(handler.callback);
    }

    @Test
    public void testRetiredSessionCannotAffectReinitializedHandler() throws Exception {
        CapturingPanelHandler handler = newCapturingPanelHandler();
        handler.handlePanelMessage(encodeLoginResponse());
        handler.dispose();
        Lares40Command logout = handler.sent.getLast();
        KseniaWebSocketManager previous = handler.connections.getLast();
        WebSocketClient previousClient = handler.clients.getLast();
        ScheduledTask timeout = handler.scheduledTasks.getLast();

        handler.startConnection();
        KseniaWebSocketManager current = handler.connections.getLast();
        int taskCount = handler.scheduledTasks.size();
        previous.onWebSocketText(encodeLogoutResponse(logout, "OK"));
        previous.onWebSocketText(encodeLoginResponse());
        timeout.task().run();
        previous.onWebSocketError(new IllegalStateException("Late error"));
        assertEquals(1, handler.cleanupTasks.size());
        handler.cleanupTasks.removeFirst().run();
        verify(previousClient).stop();
        verify(handler.clients.getLast(), never()).stop();
        verify(handler.session, never()).close();
        assertTrue(current.isConnected());
        assertEquals(taskCount, handler.scheduledTasks.size());
        verifyNoInteractions(handler.callback);
        current.onWebSocketText(encodeLoginResponse());
        assertEquals("READ:MULTI_TYPES", handler.sentCommands().getLast());
    }

    private static String encodeLogoutResponse(Lares40Command request, String result) {
        return new Lares40CommandCodec()
                .encode("panel", "client", "LOGOUT_RES", Integer.parseInt(Objects.requireNonNull(request.id)),
                        Objects.requireNonNull(request.payloadType), Map.of("RESULT", result, "ID_LOGIN", "1"))
                .orElseThrow();
    }

    @Test
    public void testConfiguredRolesAreTranslatedToWireLoginTypes() {
        for (Map.Entry<String, String> role : Map.of("USER", "USER", "SUPERVISOR", "IP_SUPERV").entrySet()) {
            CapturingPanelHandler handler = newCapturingPanelHandler(role.getKey());
            Lares40Command login = handler.logins.getFirst();
            assertEquals("LOGIN", login.command);
            assertEquals(role.getValue(), login.payloadType);
            assertEquals("123456", Objects.requireNonNull(login.payload).getAsJsonObject().get("PIN").getAsString());
        }
    }

    @Test
    public void testLoginGenericFailureGoesOfflineAndRetriesWithANewTransport() {
        for (int responseId : List.of(65535, 0)) {
            CapturingPanelHandler handler = newCapturingPanelHandler();
            ScheduledTask loginTimeout = handler.scheduledTasks.getFirst();

            handler.connections.getFirst().onWebSocketText(encodeGenericErrorResponse(responseId, "PAYLOAD_TYPE"));

            verify(handler.callback).statusUpdated(handler.getThing(), new ThingStatusInfo(ThingStatus.OFFLINE,
                    ThingStatusDetail.COMMUNICATION_ERROR, "Panel LOGIN failed (PAYLOAD_TYPE)"));
            assertTrue(handler.sent.isEmpty());
            verify(loginTimeout.future()).cancel(false);
            int scheduledCount = handler.scheduledTasks.size();
            loginTimeout.task().run();
            assertEquals(scheduledCount, handler.scheduledTasks.size());

            handler.runRetry();
            assertEquals(2, handler.connections.size());
            assertEquals(2, handler.logins.size());
            assertEquals("IP_SUPERV", handler.logins.getLast().payloadType);
            handler.connections.getFirst().onWebSocketText(encodeLoginResponse());
            assertTrue(handler.sent.isEmpty());
            handler.connections.getLast().onWebSocketText(encodeLoginResponse());
            assertEquals(List.of("READ:MULTI_TYPES"), handler.sentCommands());
        }
    }

    @Test
    public void testRegisterGenericFailureClearsInventoryAndQueuedDiagnosticsBeforeRetry() {
        for (int responseId : List.of(65535, 0)) {
            CapturingPanelHandler handler = newCapturingPanelHandler();
            handler.handlePanelMessage(encodeLoginResponse());
            handler.handlePanelMessage(encodeInitialInventoryResponse(handler.pendingRead().commandId()));
            handler.queueDiagnosticRead("ALL");

            handler.handlePanelMessage(encodeGenericErrorResponse(responseId, "PAYLOAD_TYPE"));

            verify(handler.callback).statusUpdated(handler.getThing(), new ThingStatusInfo(ThingStatus.OFFLINE,
                    ThingStatusDetail.COMMUNICATION_ERROR, "Panel REGISTER failed (PAYLOAD_TYPE)"));
            assertFalse(handler.inventory.isComplete());
            assertTrue(handler.readRequests.getPendingRequest().isEmpty());
            handler.runRetry();
            handler.handlePanelMessage(encodeLoginResponse());
            handler.handlePanelMessage(encodeInitialInventoryResponse(handler.pendingRead().commandId()));
            handler.handlePanelMessage(encodeRegisterAcknowledgement());
            assertEquals(List.of("READ:MULTI_TYPES", "REALTIME:REGISTER", "READ:MULTI_TYPES", "REALTIME:REGISTER"),
                    handler.sentCommands());
        }
    }

    @Test
    public void testLoginAndRegisterTimeoutsGoOfflineAndScheduleOnlyOneRetry() {
        for (String request : List.of("LOGIN", "REGISTER")) {
            CapturingPanelHandler handler = newCapturingPanelHandler();
            if ("REGISTER".equals(request)) {
                handler.handlePanelMessage(encodeLoginResponse());
                handler.handlePanelMessage(encodeInitialInventoryResponse(handler.pendingRead().commandId()));
            }
            ScheduledTask timeout = handler.scheduledTasks.getLast();
            assertEquals(90, timeout.delaySeconds());
            int previousTaskCount = handler.scheduledTasks.size();

            timeout.task().run();
            timeout.task().run();

            verify(handler.callback).statusUpdated(handler.getThing(), new ThingStatusInfo(ThingStatus.OFFLINE,
                    ThingStatusDetail.COMMUNICATION_ERROR, "Panel " + request + " response timed out"));
            assertEquals(previousTaskCount + 1, handler.scheduledTasks.size());
            assertEquals(60, handler.scheduledTasks.getLast().delaySeconds());
            assertFalse(handler.inventory.isComplete());
        }
    }

    @Test
    public void testExpiredTimeoutFromPreviousLoginCannotEndTheNewLogin() {
        CapturingPanelHandler handler = newCapturingPanelHandler();
        ScheduledTask previousTimeout = handler.scheduledTasks.getFirst();
        handler.handleUnexpectedConnectionClosed(1006, null);
        handler.runRetry();
        clearInvocations(handler.callback);
        int taskCount = handler.scheduledTasks.size();

        previousTimeout.task().run();

        assertEquals(taskCount, handler.scheduledTasks.size());
        verifyNoInteractions(handler.callback);
        handler.handlePanelMessage(encodeLoginResponse());
        assertEquals(List.of("READ:MULTI_TYPES"), handler.sentCommands());
    }

    @Test
    public void testArbitraryDiagnosticReadsUseConfiguredTimeoutAndActualTransportFrames() {
        CapturingPanelHandler handler = newCapturingPanelHandler();
        handler.handlePanelMessage(encodeLoginResponse());
        assertEquals(90, handler.scheduledTasks.getLast().delaySeconds());
        handler.handlePanelMessage(encodeInitialInventoryResponse(handler.pendingRead().commandId()));
        handler.handlePanelMessage(encodeRegisterAcknowledgement());

        handler.queueDiagnosticRead("STATUS_NEW_ENTITY 3 9");
        Lares40Command request = handler.sent.getLast();
        assertEquals("READ", request.command);
        assertEquals("STATUS_NEW_ENTITY", request.payloadType);
        assertEquals("[\"3\",\"9\"]",
                Objects.requireNonNull(request.payload).getAsJsonObject().get("ID_ITEMS_RANGE").toString());
        assertEquals(90, handler.scheduledTasks.getLast().delaySeconds());

        handler.queueDiagnosticRead("OUTPUTS");
        handler.handlePanelMessage(encodeGenericErrorResponse(handler.pendingRead().commandId(), "PAYLOAD_TYPE"));
        assertEquals("OUTPUTS", handler.sent.getLast().payloadType);
        assertEquals("[\"ALL\",\"ALL\"]", Objects.requireNonNull(handler.sent.getLast().payload).getAsJsonObject()
                .get("ID_ITEMS_RANGE").toString());
        assertEquals(List.of("LOGIN", "READ", "REALTIME", "READ", "READ"), handler.completedWrites.stream()
                .map(message -> new Lares40CommandCodec().decode(message).orElseThrow().command).toList());
    }

    @Test
    public void testUnrelatedLoginResponsesDoNotCompleteThePendingRequest() {
        CapturingPanelHandler handler = newCapturingPanelHandler();
        ScheduledTask timeout = handler.scheduledTasks.getFirst();
        handler.handlePanelMessage(new Lares40CommandCodec()
                .encode("panel", "client", "LOGIN_RES", 42, "IP_SUPERV", Map.of("RESULT", "OK", "ID_LOGIN", "1"))
                .orElseThrow());
        handler.handlePanelMessage(encodeGenericErrorResponse(42));
        handler.handlePanelMessage(new Lares40CommandCodec()
                .encode("panel", "client", "LOGIN_RES", 65535, "USER", Map.of("RESULT", "OK", "ID_LOGIN", "1"))
                .orElseThrow());

        assertTrue(handler.sent.isEmpty());
        verifyNoInteractions(handler.callback);
        timeout.task().run();
        verify(handler.callback).statusUpdated(handler.getThing(), new ThingStatusInfo(ThingStatus.OFFLINE,
                ThingStatusDetail.COMMUNICATION_ERROR, "Panel LOGIN response timed out"));
    }

    @Test
    public void testRejectedCredentialsDoNotCauseRepeatedLoginAttempts() {
        CapturingPanelHandler handler = newCapturingPanelHandler();
        ScheduledTask timeout = handler.scheduledTasks.getFirst();

        handler.handlePanelMessage(new Lares40CommandCodec()
                .encode("panel", "client", "LOGIN_RES", 65535, "IP_SUPERV", Map.of("RESULT", "FAIL")).orElseThrow());
        timeout.task().run();
        handler.handlePanelMessage(encodeLoginResponse());

        verify(handler.callback).statusUpdated(handler.getThing(), new ThingStatusInfo(ThingStatus.OFFLINE,
                ThingStatusDetail.CONFIGURATION_ERROR, "Panel login was rejected"));
        assertEquals(1, handler.scheduledTasks.size());
        assertTrue(handler.sent.isEmpty());
    }

    @Test
    public void testLoginReadsInventoryBeforeRegisterAndGoesOnlineOnlyAfterAcknowledgement() {
        CapturingPanelHandler handler = newCapturingPanelHandler();

        handler.handlePanelMessage(encodeLoginResponse());

        assertEquals(List.of("READ:MULTI_TYPES"), handler.sentCommands());
        Lares40PendingReadRequest request = handler.pendingRead();
        assertEquals(Lares40ReadOperation.INITIAL_INVENTORY, request.operation());
        assertEquals("1", Objects.requireNonNull(handler.sent.getFirst().payload).getAsJsonObject()
                .get(Lares40ProtocolConstants.PAYLOAD_LOGIN_ID).getAsString());

        handler.handlePanelMessage(encodeInitialInventoryResponse(request.commandId()));

        assertEquals(List.of("READ:MULTI_TYPES", "REALTIME:REGISTER"), handler.sentCommands());
        assertTrue(handler.inventory.isComplete());
        assertTrue(handler.readRequests.getPendingRequest().isEmpty());
        verifyNoInteractions(handler.callback);

        handler.handlePanelMessage(encodeRegisterAcknowledgement());

        assertEquals(List.of("READ:MULTI_TYPES", "REALTIME:REGISTER"), handler.sentCommands());
        verify(handler.callback).statusUpdated(handler.getThing(),
                new ThingStatusInfo(ThingStatus.ONLINE, ThingStatusDetail.NONE, null));
    }

    @Test
    public void testDiagnosticReadsWaitUntilRegisterAcknowledgement() {
        CapturingPanelHandler handler = newCapturingPanelHandler();
        handler.handlePanelMessage(encodeLoginResponse());
        Lares40PendingReadRequest inventoryRead = handler.pendingRead();

        handler.queueDiagnosticRead("PARTITIONS");
        assertEquals(List.of("READ:MULTI_TYPES"), handler.sentCommands());

        handler.handlePanelMessage(encodeInitialInventoryResponse(inventoryRead.commandId()));
        handler.queueDiagnosticRead("ZONES");
        handler.handlePanelMessage(encodeGenericErrorResponse(42000));
        assertEquals(List.of("READ:MULTI_TYPES", "REALTIME:REGISTER"), handler.sentCommands());
        assertTrue(handler.readRequests.getPendingRequest().isEmpty());

        handler.handlePanelMessage(encodeRegisterAcknowledgement());
        assertEquals(List.of("READ:MULTI_TYPES", "REALTIME:REGISTER", "READ:PARTITIONS"), handler.sentCommands());

        handler.handlePanelMessage(encodeGenericErrorResponse(handler.pendingRead().commandId()));
        assertEquals(List.of("READ:MULTI_TYPES", "REALTIME:REGISTER", "READ:PARTITIONS", "READ:ZONES"),
                handler.sentCommands());
    }

    @Test
    public void testMatchingAndLegacyGenericInventoryErrorsStillStartRegister() {
        for (boolean legacyResponse : List.of(false, true)) {
            CapturingPanelHandler handler = newCapturingPanelHandler();
            handler.handlePanelMessage(encodeLoginResponse());

            handler.handlePanelMessage(
                    encodeGenericErrorResponse(legacyResponse ? 0 : handler.pendingRead().commandId()));

            assertEquals(List.of("READ:MULTI_TYPES", "REALTIME:REGISTER"), handler.sentCommands());
            assertFalse(handler.inventory.isComplete());
            assertTrue(handler.readRequests.getPendingRequest().isEmpty());
        }
    }

    @Test
    public void testInventoryTimeoutStartsRegisterOnlyOnceAndIgnoresLateInventory() {
        CapturingPanelHandler handler = newCapturingPanelHandler();
        handler.handlePanelMessage(encodeLoginResponse());
        Lares40PendingReadRequest request = handler.pendingRead();

        handler.handleReadTimeout(request);
        handler.handleReadTimeout(request);
        handler.handlePanelMessage(encodeInitialInventoryResponse(request.commandId()));

        assertEquals(List.of("READ:MULTI_TYPES", "REALTIME:REGISTER"), handler.sentCommands());
        assertFalse(handler.inventory.isComplete());
        assertTrue(handler.readRequests.getPendingRequest().isEmpty());
    }

    @Test
    public void testRejectedOrIncompleteInventoryDoesNotPreventMonitoring() {
        List<Map<String, Object>> payloads = List.of(Map.of("RESULT", "FAIL", "RESULT_DETAIL", "ACCESS_PAYLOAD_TYPE"),
                Map.of("RESULT", "OK"), Map.of("RESULT", "OK", "ZONES", List.of()));
        for (Map<String, Object> payload : payloads) {
            CapturingPanelHandler handler = newCapturingPanelHandler();
            handler.handlePanelMessage(encodeLoginResponse());
            String response = new Lares40CommandCodec()
                    .encode("panel", "client", "READ_RES", handler.pendingRead().commandId(), "MULTI_TYPES", payload)
                    .orElseThrow();

            handler.handlePanelMessage(response);

            assertEquals(List.of("READ:MULTI_TYPES", "REALTIME:REGISTER"), handler.sentCommands());
            assertFalse(handler.inventory.isComplete());
        }
    }

    @Test
    public void testUnsolicitedAcknowledgementAndDuplicateLoginCannotSkipInventory() {
        CapturingPanelHandler handler = newCapturingPanelHandler();
        handler.handlePanelMessage(encodeLoginResponse());
        Lares40PendingReadRequest request = handler.pendingRead();

        handler.handlePanelMessage(encodeRegisterAcknowledgement());
        handler.handlePanelMessage(encodeLoginResponse());

        assertEquals(List.of("READ:MULTI_TYPES"), handler.sentCommands());
        assertEquals(request, handler.pendingRead());
        verifyNoInteractions(handler.callback);
    }

    @Test
    public void testReconnectReadsFreshInventoryAndDropsOldResponsesTimeoutsAndQueuedDiagnostics() {
        CapturingPanelHandler handler = newCapturingPanelHandler();
        handler.handlePanelMessage(encodeLoginResponse());
        Lares40PendingReadRequest oldRead = handler.pendingRead();
        handler.handlePanelMessage(encodeInitialInventoryResponse(oldRead.commandId()));
        assertTrue(handler.inventory.isComplete());
        handler.queueDiagnosticRead("ALL");

        handler.handleUnexpectedConnectionClosed(1006, null);
        assertFalse(handler.inventory.isComplete());
        handler.runRetry();
        handler.handlePanelMessage(encodeLoginResponse());
        Lares40PendingReadRequest newRead = handler.pendingRead();
        handler.handleReadTimeout(oldRead);
        handler.handlePanelMessage(encodeInitialInventoryResponse(oldRead.commandId()));

        assertEquals(newRead, handler.pendingRead());
        assertFalse(handler.inventory.isComplete());
        handler.handlePanelMessage(encodeInitialInventoryResponse(newRead.commandId()));
        handler.handlePanelMessage(encodeRegisterAcknowledgement());

        assertEquals(List.of("READ:MULTI_TYPES", "REALTIME:REGISTER", "READ:MULTI_TYPES", "REALTIME:REGISTER"),
                handler.sentCommands());
        assertTrue(handler.inventory.isComplete());
        assertTrue(handler.readRequests.getPendingRequest().isEmpty());
    }

    @Test
    public void testDisconnectionDuringInitialReadDoesNotStartRegister() {
        CapturingPanelHandler handler = newCapturingPanelHandler();
        handler.handlePanelMessage(encodeLoginResponse());
        Lares40PendingReadRequest request = handler.pendingRead();

        handler.handleUnexpectedConnectionClosed(1006, null);
        handler.handleReadTimeout(request);
        handler.handlePanelMessage(encodeInitialInventoryResponse(request.commandId()));

        assertEquals(List.of("READ:MULTI_TYPES"), handler.sentCommands());
        assertFalse(handler.inventory.isComplete());
    }

    @Test
    public void testChangesBeforeAcknowledgementAreAppliedAfterTheInitialState() {
        CapturingPanelHandler handler = newCapturingPanelHandler();
        KseniaZoneHandler zoneHandler = Objects.requireNonNull(mock(KseniaZoneHandler.class));
        when(zoneHandler.getThing()).thenReturn(Objects.requireNonNull(mock(Thing.class)));
        handler.addZoneHandler(2, zoneHandler);
        handler.handlePanelMessage(encodeLoginResponse());
        handler.handlePanelMessage(encodeInitialInventoryResponse(handler.pendingRead().commandId()));

        String receiver = Objects.requireNonNull(handler.sent.getLast().sender);
        handler.handlePanelMessage(new Lares40CommandCodec()
                .encode("panel", "client", "REALTIME", 0, "CHANGES",
                        Map.of(receiver, Map.of("STATUS_ZONES", List.of(Map.of("ID", "2", "STA", "A")))))
                .orElseThrow());

        var snapshots = ArgumentCaptor.forClass(ZoneSnapshot.class);
        verify(zoneHandler, never()).onZoneStateUpdated(snapshots.capture());

        handler.handlePanelMessage(
                new Lares40CommandCodec()
                        .encode("panel", "client", "REALTIME_RES", 65535, "REGISTER_ACK",
                                Map.of("RESULT", "OK", "STATUS_ZONES", List.of(Map.of("ID", "2", "STA", "R"))))
                        .orElseThrow());

        verify(zoneHandler, times(2)).onZoneStateUpdated(snapshots.capture());
        assertEquals(List.of(ZoneCondition.REST, ZoneCondition.ALARM),
                snapshots.getAllValues().stream().map(ZoneSnapshot::condition).toList());
    }

    @Test
    public void testClassifiesSelfSignedCertificateBootstrapFailures() {
        assertEquals("Panel host cannot be resolved",
                getDescription(new RuntimeException(new UnknownHostException("lares.local"))));
        assertEquals("Panel is unreachable", getDescription(new ConnectException("Connection refused")));
        assertEquals("Panel is unreachable", getDescription(new NoRouteToHostException("No route to host")));
        assertEquals("Connection to the panel timed out", getDescription(new SocketTimeoutException("Timed out")));
        assertEquals("Secure connection to the panel failed",
                getDescription(new SSLHandshakeException("Protocol error")));
        assertEquals("Could not retrieve the panel certificate",
                Lares40PanelHandler.describeSelfSignedCertificateTrustFailure(new CertificateException("Invalid")));
    }

    @Test
    public void testGenericErrorCompletesOnlyTheMatchingOrLegacyDiagnosticRead() {
        Lares40ReadRequestManager readRequestManager = new Lares40ReadRequestManager();
        Lares40PanelHandler handler = newPanelHandler(readRequestManager);
        Lares40PendingReadRequest pendingRequest = readRequestManager.begin(Lares40ReadOperation.DIAGNOSTIC,
                Lares40ReadRequest.forDiagnosticCommand("MULTI_TYPES").orElseThrow()).orElseThrow();

        handler.handlePanelMessage(encodeGenericErrorResponse(pendingRequest.commandId() + 1));
        assertFalse(readRequestManager.getPendingRequest().isEmpty());

        handler.handlePanelMessage(encodeGenericErrorResponse(pendingRequest.commandId()));
        assertTrue(readRequestManager.getPendingRequest().isEmpty());

        readRequestManager.begin(Lares40ReadOperation.DIAGNOSTIC,
                Lares40ReadRequest.forDiagnosticCommand("PARTITIONS").orElseThrow()).orElseThrow();
        handler.handlePanelMessage(encodeGenericErrorResponse(0));
        assertTrue(readRequestManager.getPendingRequest().isEmpty());
    }

    @Test
    public void testInitialInventoryResponseUpdatesTheMetadataInventory() {
        Lares40ReadRequestManager readRequestManager = new Lares40ReadRequestManager();
        KseniaPanelInventory inventory = new KseniaPanelInventory();
        List<ZoneMetadataSnapshot> zoneMetadata = new ArrayList<>();
        inventory.addZoneListener(2, zoneMetadata::add);
        Lares40PanelHandler handler = newPanelHandler(readRequestManager, inventory);
        Lares40PendingReadRequest pendingRequest = readRequestManager
                .begin(Lares40ReadOperation.INITIAL_INVENTORY, Lares40ReadRequest.initialInventory()).orElseThrow();

        handler.handlePanelMessage(encodeInitialInventoryResponse(pendingRequest.commandId()));

        assertTrue(readRequestManager.getPendingRequest().isEmpty());
        assertTrue(inventory.isComplete());
        assertEquals(1, zoneMetadata.size());
        assertEquals("Entrance", zoneMetadata.getFirst().get(ZoneMetadataField.DESCRIPTION));
    }

    @Test
    public void testConnectionEndInvalidatesThePanelInventory() {
        Lares40ReadRequestManager readRequestManager = new Lares40ReadRequestManager();
        KseniaPanelInventory inventory = new KseniaPanelInventory();
        inventory.apply(new PanelInventoryUpdates(
                Map.of(2, new ZoneMetadataSnapshot(2, Map.of(ZoneMetadataField.DESCRIPTION, "Entrance"))), true,
                Map.of(1, new PartitionMetadataSnapshot(1, Map.of(PartitionMetadataField.DESCRIPTION, "Home"))), true));
        Lares40PanelHandler handler = newPanelHandler(readRequestManager, inventory);

        handler.handleUnexpectedConnectionClosed(1006, null);

        assertFalse(inventory.isComplete());
    }

    private static String getDescription(Throwable cause) {
        return Lares40PanelHandler
                .describeSelfSignedCertificateTrustFailure(new CertificateException("Bootstrap failed", cause));
    }

    private CapturingPanelHandler newCapturingPanelHandler() {
        return newCapturingPanelHandler("SUPERVISOR");
    }

    private CapturingPanelHandler newCapturingPanelHandler(String loginType) {
        CapturingPanelHandler handler = new CapturingPanelHandler(new Lares40ReadRequestManager(),
                new KseniaPanelInventory(), Objects.requireNonNull(mock(WebSocketFactory.class)), loginType);
        handlers.add(handler);
        handler.startConnection();
        return handler;
    }

    private static String encodeLoginResponse() {
        return new Lares40CommandCodec()
                .encode("panel", "client", "LOGIN_RES", 65535, "IP_SUPERV", Map.of("RESULT", "OK", "ID_LOGIN", "1"))
                .orElseThrow();
    }

    private static String encodeRegisterAcknowledgement() {
        return new Lares40CommandCodec()
                .encode("panel", "client", "REALTIME_RES", 65535, "REGISTER_ACK",
                        Map.of("RESULT", "OK", "STATUS_ZONES", List.of(), "STATUS_PARTITIONS", List.of()))
                .orElseThrow();
    }

    /** Captures actual protocol frames without opening a connection to a panel. */
    private static class CapturingPanelHandler extends Lares40PanelHandler {
        private final List<Lares40Command> sent = new ArrayList<>();
        private final List<Lares40Command> logins = new ArrayList<>();
        private final List<String> completedWrites = new ArrayList<>();
        private final List<ScheduledTask> scheduledTasks = new ArrayList<>();
        private final List<KseniaWebSocketManager> connections = new ArrayList<>();
        private final List<WebSocketClient> clients = new ArrayList<>();
        private volatile CompletableFuture<KseniaWebSocketManager> nextConnection = new CompletableFuture<>();
        private volatile Session session = Objects.requireNonNull(mock(Session.class));
        private final List<Runnable> cleanupTasks = new ArrayList<>();
        private final Lares40ReadRequestManager readRequests;
        private final KseniaPanelInventory inventory;
        private final ThingHandlerCallback callback = Objects.requireNonNull(mock(ThingHandlerCallback.class));

        CapturingPanelHandler(Lares40ReadRequestManager readRequests, KseniaPanelInventory inventory,
                WebSocketFactory factory, String loginType) {
            super(Objects.requireNonNull(mock(Bridge.class)), factory,
                    Objects.requireNonNull(mock(BundleContext.class)), readRequests, inventory);
            this.readRequests = readRequests;
            this.inventory = inventory;
            when(getThing().getUID()).thenReturn(new ThingUID(KseniaBindingConstants.BINDING_ID, "panel", "test"));
            when(getThing().getHandler()).thenReturn(this);
            AtomicReference<ThingStatusInfo> panelStatus = new AtomicReference<>(
                    new ThingStatusInfo(ThingStatus.INITIALIZING, ThingStatusDetail.NONE, null));
            when(getThing().getStatusInfo()).thenAnswer(invocation -> panelStatus.get());
            when(getThing().getStatus()).thenAnswer(invocation -> panelStatus.get().getStatus());
            doAnswer(invocation -> {
                panelStatus.set(invocation.getArgument(1));
                return null;
            }).when(callback).statusUpdated(eq(getThing()), any(ThingStatusInfo.class));
            when(getThing().getConfiguration()).thenReturn(new Configuration(Map.of("host", "lares.local", "port", 80,
                    "useSecureConnection", false, "trustSelfSignedCertificate", false, "reconnectInterval", 60,
                    "responseTimeout", 90, "loginType", loginType, "loginCode", "123456")));
            when(factory.createWebSocketClient(anyString())).thenAnswer(creation -> {
                WebSocketClient client = Objects.requireNonNull(mock(WebSocketClient.class));
                clients.add(client);
                Session connectedSession = Objects.requireNonNull(mock(Session.class));
                session = connectedSession;
                when(client.isRunning()).thenReturn(true);
                when(connectedSession.isOpen()).thenReturn(true);
                RemoteEndpoint remote = Objects.requireNonNull(mock(RemoteEndpoint.class));
                when(connectedSession.getRemote()).thenReturn(remote);
                doAnswer(invocation -> {
                    String message = invocation.getArgument(0);
                    Lares40Command command = new Lares40CommandCodec().decode(message).orElseThrow();
                    if (Lares40ProtocolConstants.COMMAND_LOGIN.equals(command.command)) {
                        logins.add(command);
                    } else {
                        sent.add(command);
                    }
                    invocation.<WriteCallback> getArgument(1).writeSuccess();
                    return null;
                }).when(remote).sendString(anyString(), any(WriteCallback.class));
                try {
                    doAnswer(invocation -> {
                        KseniaWebSocketManager manager = invocation.getArgument(0);
                        connections.add(manager);
                        nextConnection.complete(manager);
                        return CompletableFuture.completedFuture(connectedSession);
                    }).when(client).connect(any(), any(URI.class), any(ClientUpgradeRequest.class));
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                return client;
            });
            setCallback(callback);
        }

        void startConnection() {
            nextConnection = new CompletableFuture<>();
            initialize();
            completeConnection();
        }

        void completeConnection() {
            try {
                nextConnection.get(5, TimeUnit.SECONDS).onWebSocketConnect(session);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            } catch (ExecutionException | TimeoutException e) {
                throw new AssertionError(e);
            }
            clearInvocations(callback);
        }

        void activateWithChildStates() {
            handlePanelMessage(encodeLoginResponse());
            handlePanelMessage(encodeInitialInventoryResponse(pendingRead().commandId()));
            handlePanelMessage(
                    new Lares40CommandCodec()
                            .encode("panel", "client", "REALTIME_RES", 65535, "REGISTER_ACK",
                                    Map.of("RESULT", "OK", "STATUS_ZONES", List.of(Map.of("ID", "2", "STA", "R")),
                                            "STATUS_PARTITIONS", List.of(Map.of("ID", "1", "ARM", "D"))))
                            .orElseThrow());
        }

        void publishChildChange(String type, int id, boolean active) {
            String receiver = Objects.requireNonNull(logins.getLast().sender);
            String payloadType = "zone".equals(type) ? "STATUS_ZONES" : "STATUS_PARTITIONS";
            Map<String, String> entry = "zone".equals(type)
                    ? Map.of("ID", Integer.toString(id), "STA", active ? "A" : "R")
                    : Map.of("ID", Integer.toString(id), "ARM", active ? "IA" : "D");
            handlePanelMessage(new Lares40CommandCodec().encode("panel", "client", "REALTIME", 0, "CHANGES",
                    Map.of(receiver, Map.of(payloadType, List.of(entry)))).orElseThrow());
        }

        void runRetry() {
            ScheduledTask retry = scheduledTasks.getLast();
            assertEquals(60, retry.delaySeconds());
            retry.task().run();
            connections.getLast().onWebSocketConnect(session);
        }

        @Override
        void executeTransportCleanup(Runnable task) {
            cleanupTasks.add(task);
        }

        @Override
        ScheduledFuture<?> scheduleProtocolTask(Runnable task, int delaySeconds) {
            ScheduledFuture<?> future = Objects.requireNonNull(mock(ScheduledFuture.class));
            scheduledTasks.add(new ScheduledTask(task, delaySeconds, future));
            return future;
        }

        @Override
        public void messageSent(KseniaWebSocketManager manager, String message) {
            completedWrites.add(message);
            super.messageSent(manager, message);
        }

        List<String> sentCommands() {
            return sent.stream().map(command -> command.command + ":" + command.payloadType).toList();
        }

        Lares40PendingReadRequest pendingRead() {
            return readRequests.getPendingRequest().orElseThrow();
        }

        void queueDiagnosticRead(String command) {
            handleCommand(new ChannelUID(getThing().getUID(), KseniaBindingConstants.CHANNEL_PANEL_DIAGNOSTIC_READ),
                    new StringType(command));
        }
    }

    private record ScheduledTask(Runnable task, int delaySeconds, ScheduledFuture<?> future) {
    }

    private static Lares40PanelHandler newPanelHandler(Lares40ReadRequestManager readRequestManager) {
        return new Lares40PanelHandler(mock(Bridge.class), mock(WebSocketFactory.class), mock(BundleContext.class),
                readRequestManager);
    }

    private static Lares40PanelHandler newPanelHandler(Lares40ReadRequestManager readRequestManager,
            KseniaPanelInventory inventory) {
        return new Lares40PanelHandler(mock(Bridge.class), mock(WebSocketFactory.class), mock(BundleContext.class),
                readRequestManager, inventory);
    }

    private static String encodeGenericErrorResponse(int commandId) {
        return encodeGenericErrorResponse(commandId, "ACCESS_PAYLOAD_TYPE");
    }

    private static String encodeGenericErrorResponse(int commandId, String detail) {
        return new Lares40CommandCodec().encode("panel", "client", Lares40ProtocolConstants.COMMAND_GENERIC, commandId,
                Lares40ProtocolConstants.PAYLOAD_TYPE_ERROR, Map.of(Lares40ProtocolConstants.PAYLOAD_RESULT, "FAIL",
                        Lares40ProtocolConstants.PAYLOAD_RESULT_DETAIL, detail))
                .orElseThrow();
    }

    private static String encodeInitialInventoryResponse(int commandId) {
        return new Lares40CommandCodec().encode("panel", "client", Lares40ProtocolConstants.COMMAND_READ_RESPONSE,
                commandId, Lares40ProtocolConstants.PAYLOAD_TYPE_MULTIPLE_TYPES,
                Map.of(Lares40ProtocolConstants.PAYLOAD_RESULT, "OK", Lares40ProtocolConstants.PAYLOAD_TYPE_ZONES,
                        List.of(Map.of(Lares40ProtocolConstants.PAYLOAD_ENTRY_ID, "2",
                                Lares40ProtocolConstants.PAYLOAD_DESCRIPTION, "Entrance")),
                        Lares40ProtocolConstants.PAYLOAD_TYPE_PARTITIONS,
                        List.of(Map.of(Lares40ProtocolConstants.PAYLOAD_ENTRY_ID, "1",
                                Lares40ProtocolConstants.PAYLOAD_DESCRIPTION, "Home"))))
                .orElseThrow();
    }
}
