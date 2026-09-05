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
package org.openhab.binding.kseniasecurity.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.ByteBuffer;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests for {@link KseniaWebSocketManager} transport lifecycle handling.
 *
 * @author Michele Pattera - Initial contribution
 */
public class KseniaWebSocketManagerTest {

    @Test
    public void testDisablesTheJettyIdleTimeoutForTheLongLivedPanelConnection() {
        WebSocketClient client = mock(WebSocketClient.class);
        KseniaWebSocketListener listener = mock(KseniaWebSocketListener.class);

        new KseniaWebSocketManager(client, listener, "kseniasecurity:panel:test");

        verify(client).setMaxIdleTimeout(0);
    }

    @Test
    public void testErrorThenCloseDeliversOneTerminalCallback() {
        WebSocketClient client = mock(WebSocketClient.class);
        KseniaWebSocketListener listener = mock(KseniaWebSocketListener.class);
        KseniaWebSocketManager manager = new KseniaWebSocketManager(client, listener, "kseniasecurity:panel:test");
        connect(manager, client);

        IllegalStateException cause = new IllegalStateException("Connection reset");
        manager.onWebSocketError(cause);
        manager.onWebSocketClose(1006, "abnormal closure");

        verify(listener).connectionError(same(manager), same(cause));
        verify(listener, never()).connectionClosed(any(KseniaWebSocketManager.class), anyInt(), any());
    }

    @Test
    public void testCloseThenErrorDeliversOneTerminalCallback() {
        WebSocketClient client = mock(WebSocketClient.class);
        KseniaWebSocketListener listener = mock(KseniaWebSocketListener.class);
        KseniaWebSocketManager manager = new KseniaWebSocketManager(client, listener, "kseniasecurity:panel:test");
        connect(manager, client);

        manager.onWebSocketClose(1001, "going away");
        manager.onWebSocketError(new IllegalStateException("Connection reset"));

        verify(listener).connectionClosed(manager, 1001, "going away");
        verify(listener, never()).connectionError(any(KseniaWebSocketManager.class), any());
    }

    @Test
    public void testLocalDisconnectDoesNotDeliverFailureCallback() {
        WebSocketClient client = mock(WebSocketClient.class);
        KseniaWebSocketListener listener = mock(KseniaWebSocketListener.class);
        KseniaWebSocketManager manager = new KseniaWebSocketManager(client, listener, "kseniasecurity:panel:test");
        connect(manager, client);

        manager.disconnect();
        manager.onWebSocketError(new IllegalStateException("Client stopped"));
        manager.onWebSocketClose(1000, "normal closure");

        verifyNoInteractions(listener);
    }

    @Test
    public void testLateConnectAfterLocalDisconnectIsClosedWithoutNotification() {
        WebSocketClient client = mock(WebSocketClient.class);
        KseniaWebSocketListener listener = mock(KseniaWebSocketListener.class);
        KseniaWebSocketManager manager = new KseniaWebSocketManager(client, listener, "kseniasecurity:panel:test");
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);
        connect(manager, client);

        manager.disconnect();
        manager.onWebSocketConnect(session);

        verify(session).close();
        verifyNoInteractions(listener);
    }

    @Test
    public void testPingDoesNotSendAnAdditionalPong() {
        WebSocketClient client = mock(WebSocketClient.class);
        KseniaWebSocketListener listener = mock(KseniaWebSocketListener.class);
        KseniaWebSocketManager manager = new KseniaWebSocketManager(client, listener, "kseniasecurity:panel:test");
        Session session = mock(Session.class);
        connect(manager, client);
        manager.onWebSocketConnect(session);
        reset(listener);

        manager.onWebSocketPing(ByteBuffer.wrap(new byte[] { 1, 2 }));

        verify(session, never()).getRemote();
        verifyNoInteractions(listener);
    }

    @Test
    public void testReportsExactFrameOnlyAfterSuccessfulWrite() {
        WebSocketClient client = mock(WebSocketClient.class);
        KseniaWebSocketListener listener = mock(KseniaWebSocketListener.class);
        KseniaWebSocketManager manager = new KseniaWebSocketManager(client, listener, "kseniasecurity:panel:test");
        Session session = connectSession(manager, client, listener);
        String message = "encoded protocol frame";

        manager.sendMessage(message);

        ArgumentCaptor<WriteCallback> callback = ArgumentCaptor.forClass(WriteCallback.class);
        verify(session.getRemote()).sendString(eq(message), callback.capture());
        verifyNoInteractions(listener);

        callback.getValue().writeSuccess();

        verify(listener).messageSent(same(manager), same(message));
        verify(listener, never()).connectionError(any(), any());
    }

    @Test
    public void testAsynchronousWriteFailureEndsConnectionWithoutReportingASentFrame() {
        WebSocketClient client = mock(WebSocketClient.class);
        KseniaWebSocketListener listener = mock(KseniaWebSocketListener.class);
        KseniaWebSocketManager manager = new KseniaWebSocketManager(client, listener, "kseniasecurity:panel:test");
        Session session = connectSession(manager, client, listener);
        manager.sendMessage("frame");
        ArgumentCaptor<WriteCallback> callback = ArgumentCaptor.forClass(WriteCallback.class);
        verify(session.getRemote()).sendString(eq("frame"), callback.capture());
        IllegalStateException cause = new IllegalStateException("Write failed");

        callback.getValue().writeFailed(cause);
        manager.onWebSocketError(cause);
        manager.onWebSocketClose(1006, "abnormal closure");

        verify(listener).connectionError(same(manager), same(cause));
        verify(listener, never()).messageSent(any(), anyString());
        verify(listener, never()).connectionClosed(any(), anyInt(), any());
        assertFalse(manager.isConnected());
    }

    @Test
    public void testSynchronousWriteFailureEndsConnection() {
        WebSocketClient client = mock(WebSocketClient.class);
        KseniaWebSocketListener listener = mock(KseniaWebSocketListener.class);
        KseniaWebSocketManager manager = new KseniaWebSocketManager(client, listener, "kseniasecurity:panel:test");
        Session session = connectSession(manager, client, listener);
        IllegalStateException cause = new IllegalStateException("Write rejected");
        RemoteEndpoint remote = session.getRemote();
        doThrow(cause).when(remote).sendString(anyString(), any(WriteCallback.class));

        manager.sendMessage("frame");

        verify(listener).connectionError(same(manager), same(cause));
        verify(listener, never()).messageSent(any(), anyString());
        assertFalse(manager.isConnected());
    }

    @Test
    public void testClosedSessionCannotReportSuccessfulWrite() {
        WebSocketClient client = mock(WebSocketClient.class);
        KseniaWebSocketListener listener = mock(KseniaWebSocketListener.class);
        KseniaWebSocketManager manager = new KseniaWebSocketManager(client, listener, "kseniasecurity:panel:test");
        Session session = connectSession(manager, client, listener);
        when(session.isOpen()).thenReturn(false);

        manager.sendMessage("frame");

        verify(session.getRemote(), never()).sendString(anyString(), any(WriteCallback.class));
        verify(listener, never()).messageSent(any(), anyString());
        verify(listener).connectionError(same(manager), any(IllegalStateException.class));
        assertFalse(manager.isConnected());
    }

    @Test
    public void testLateWriteFailureCannotEndNewSession() {
        WebSocketClient client = mock(WebSocketClient.class);
        KseniaWebSocketListener listener = mock(KseniaWebSocketListener.class);
        KseniaWebSocketManager manager = new KseniaWebSocketManager(client, listener, "kseniasecurity:panel:test");
        Session previousSession = connectSession(manager, client, listener);
        manager.sendMessage("old frame");
        ArgumentCaptor<WriteCallback> callback = ArgumentCaptor.forClass(WriteCallback.class);
        verify(previousSession.getRemote()).sendString(eq("old frame"), callback.capture());

        manager.disconnect();
        connectSession(manager, client, listener);
        callback.getValue().writeFailed(new IllegalStateException("Old write failed"));

        verifyNoInteractions(listener);
        assertTrue(manager.isConnected());
    }

    private static Session connectSession(KseniaWebSocketManager manager, WebSocketClient client,
            KseniaWebSocketListener listener) {
        connect(manager, client);
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getRemote()).thenReturn(mock(RemoteEndpoint.class));
        manager.onWebSocketConnect(session);
        reset(listener);
        return session;
    }

    private static void connect(KseniaWebSocketManager manager, WebSocketClient client) {
        when(client.isRunning()).thenReturn(true);
        manager.connect(URI.create("ws://panel.example"));
    }
}
