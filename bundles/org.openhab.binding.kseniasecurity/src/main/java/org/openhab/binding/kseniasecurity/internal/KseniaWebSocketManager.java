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

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.WebSocketPingPongListener;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the lifecycle of a single panel WebSocket connection.
 *
 * <p>
 * The class deliberately exposes only connection events and text messages. Protocol decoding remains in the bridge
 * handler, and complete wire messages are never written to the log because they can contain security-sensitive data.
 * Each connection attempt reports at most one unexpected terminal event; a locally requested disconnect reports none.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public class KseniaWebSocketManager implements WebSocketListener, WebSocketPingPongListener {

    private static final String WEBSOCKET_PROTOCOL = "KS_WSOCK";
    // Jetty otherwise closes an entirely valid, quiet real-time connection after its 300 second default timeout.
    private static final long DISABLED_IDLE_TIMEOUT_MILLISECONDS = 0;

    private final Logger logger = LoggerFactory.getLogger(KseniaWebSocketManager.class);
    private final Object lifecycleLock = new Object();
    private final WebSocketClient client;
    private final KseniaWebSocketListener listener;
    private final String bridgeUid;

    private volatile @Nullable Session session;
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private long connectionAttempt;
    private boolean disconnectRequested;
    private boolean terminalCallbackDelivered;

    /**
     * Creates a WebSocket manager using a client owned by the bridge.
     *
     * @param client the WebSocket client to use
     * @param listener recipient of connection and text events
     * @param bridgeUid UID of the bridge that owns the connection
     */
    public KseniaWebSocketManager(WebSocketClient client, KseniaWebSocketListener listener, String bridgeUid) {
        this.client = Objects.requireNonNull(client);
        this.listener = Objects.requireNonNull(listener);
        this.bridgeUid = Objects.requireNonNull(bridgeUid);
        // This is a bridge-owned client, never the openHAB shared client. A panel connection is long lived and the
        // protocol does not prescribe a fixed application heartbeat. Transport failures are reported by Jetty.
        this.client.setMaxIdleTimeout(DISABLED_IDLE_TIMEOUT_MILLISECONDS);
        logger.debug("Panel {} disabled the WebSocket idle timeout for its long-lived connection", bridgeUid);
    }

    /**
     * Starts a non-blocking connection attempt.
     *
     * @param uri the panel WebSocket endpoint
     */
    public void connect(URI uri) {
        long attempt;
        @Nullable
        Exception connectionFailure = null;
        synchronized (lifecycleLock) {
            if (connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.CONNECTING
                    || connectionState == ConnectionState.CLOSING) {
                logger.debug("Panel {} already has a WebSocket connection attempt in progress", bridgeUid);
                return;
            }

            attempt = ++connectionAttempt;
            disconnectRequested = false;
            terminalCallbackDelivered = false;
            connectionState = ConnectionState.CONNECTING;
            try {
                if (!client.isRunning()) {
                    client.start();
                }

                ClientUpgradeRequest request = new ClientUpgradeRequest();
                request.setSubProtocols(WEBSOCKET_PROTOCOL);
                client.connect(this, uri, request);
                logger.debug("Panel {} started WebSocket connection attempt {}", bridgeUid, attempt);
            } catch (Exception e) {
                connectionFailure = e;
            }
        }
        if (connectionFailure != null) {
            handleUnexpectedError(connectionFailure);
        }
    }

    /** Stops the connection and the underlying client. */
    public void disconnect() {
        @Nullable
        Session currentSession;
        long closingAttempt;
        synchronized (lifecycleLock) {
            disconnectRequested = true;
            terminalCallbackDelivered = true;
            connectionState = ConnectionState.CLOSING;
            closingAttempt = connectionAttempt;
            currentSession = session;
            session = null;
        }
        if (currentSession != null && currentSession.isOpen()) {
            currentSession.close();
        }

        try {
            if (client.isRunning()) {
                client.stop();
            }
        } catch (Exception e) {
            logger.debug("Panel {} could not stop its WebSocket client", bridgeUid, e);
        } finally {
            synchronized (lifecycleLock) {
                if (connectionAttempt == closingAttempt && connectionState == ConnectionState.CLOSING) {
                    connectionState = ConnectionState.DISCONNECTED;
                }
            }
        }
    }

    /**
     * Sends one already encoded protocol message if the connection is open.
     *
     * @param message the encoded protocol message
     */
    public void sendMessage(String message) {
        @Nullable
        Session currentSession = session;
        if (currentSession == null || !currentSession.isOpen()) {
            logger.debug("Panel {} cannot send a WebSocket message because the session is not open", bridgeUid);
            handleUnexpectedError(new IllegalStateException("WebSocket session is not open"), currentSession);
            return;
        }

        try {
            currentSession.getRemote().sendString(message, new WriteCallback() {
                @Override
                public void writeSuccess() {
                    logger.trace("Panel {} WebSocket TX text frame: {} characters", bridgeUid, message.length());
                    listener.messageSent(KseniaWebSocketManager.this, message);
                }

                @Override
                public void writeFailed(@Nullable Throwable cause) {
                    handleUnexpectedError(cause, currentSession);
                }
            });
        } catch (Exception e) {
            handleUnexpectedError(e, currentSession);
        }
    }

    @Override
    public void onWebSocketConnect(@Nullable Session connectedSession) {
        if (connectedSession == null) {
            handleUnexpectedError(new IllegalStateException("Jetty connected without a WebSocket session"));
            return;
        }

        long attempt;
        boolean accepted;
        synchronized (lifecycleLock) {
            accepted = connectionState == ConnectionState.CONNECTING && !disconnectRequested
                    && !terminalCallbackDelivered;
            attempt = connectionAttempt;
            if (accepted) {
                session = connectedSession;
                connectionState = ConnectionState.CONNECTED;
            }
        }
        if (!accepted) {
            logger.debug("Panel {} ignored a late WebSocket connection for attempt {}", bridgeUid, attempt);
            connectedSession.close();
            return;
        }

        logger.debug("Panel {} established WebSocket session for attempt {}", bridgeUid, attempt);
        listener.connectionEstablished(this);
    }

    @Override
    public void onWebSocketClose(int statusCode, @Nullable String reason) {
        long attempt = getConnectionAttempt();
        if (completeUnexpectedTermination(null)) {
            logger.debug("Panel {} WebSocket session for attempt {} closed unexpectedly: statusCode={}, reason={}",
                    bridgeUid, attempt, statusCode, reason);
            listener.connectionClosed(this, statusCode, reason);
        } else {
            logger.trace("Panel {} ignored a duplicate or locally requested WebSocket close for attempt {}", bridgeUid,
                    attempt);
        }
    }

    @Override
    public void onWebSocketError(@Nullable Throwable cause) {
        handleUnexpectedError(cause);
    }

    @Override
    public void onWebSocketBinary(byte @Nullable [] payload, int offset, int len) {
        logger.trace("Panel {} ignored an unsupported binary WebSocket frame containing {} bytes", bridgeUid, len);
    }

    @Override
    public void onWebSocketText(@Nullable String message) {
        if (message == null) {
            logger.debug("Panel {} ignored an empty WebSocket text frame", bridgeUid);
            return;
        }
        if (!isActiveConnection()) {
            logger.debug("Panel {} ignored a WebSocket text frame after its session had ended", bridgeUid);
            return;
        }

        logger.trace("Panel {} WebSocket RX text frame: {} characters", bridgeUid, message.length());
        listener.messageReceived(this, message);
    }

    @Override
    public void onWebSocketPing(@Nullable ByteBuffer payload) {
        // Jetty automatically sends the matching PONG after invoking this listener. Sending one here would produce a
        // duplicate PONG for every panel PING.
        logger.trace("Panel {} received a WebSocket PING containing {} bytes; Jetty will send the PONG", bridgeUid,
                payload == null ? 0 : payload.remaining());
    }

    @Override
    public void onWebSocketPong(@Nullable ByteBuffer payload) {
        logger.trace("Panel {} received a WebSocket PONG containing {} bytes", bridgeUid,
                payload == null ? 0 : payload.remaining());
    }

    /**
     * Indicates whether the WebSocket handshake has completed.
     *
     * @return {@code true} if connected
     */
    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED;
    }

    private void handleUnexpectedError(@Nullable Throwable cause) {
        handleUnexpectedError(cause, null);
    }

    private void handleUnexpectedError(@Nullable Throwable cause, @Nullable Session expectedSession) {
        long attempt = getConnectionAttempt();
        if (completeUnexpectedTermination(expectedSession)) {
            if (cause == null) {
                logger.debug("Panel {} WebSocket session for attempt {} reported an unspecified error", bridgeUid,
                        attempt);
            } else {
                logger.debug("Panel {} WebSocket session for attempt {} reported {}: {}", bridgeUid, attempt,
                        cause.getClass().getName(), cause.getMessage(), cause);
            }
            listener.connectionError(this, cause);
        } else {
            logger.trace("Panel {} ignored a duplicate or locally requested WebSocket error for attempt {}", bridgeUid,
                    attempt);
        }
    }

    private boolean completeUnexpectedTermination(@Nullable Session expectedSession) {
        synchronized (lifecycleLock) {
            // An asynchronous write failure from a previous session must not terminate a newer connection.
            if (expectedSession != null && !Objects.equals(expectedSession, session)) {
                return false;
            }
            if (disconnectRequested || terminalCallbackDelivered || (connectionState != ConnectionState.CONNECTING
                    && connectionState != ConnectionState.CONNECTED)) {
                return false;
            }
            terminalCallbackDelivered = true;
            session = null;
            connectionState = ConnectionState.DISCONNECTED;
            return true;
        }
    }

    private long getConnectionAttempt() {
        synchronized (lifecycleLock) {
            return connectionAttempt;
        }
    }

    private boolean isActiveConnection() {
        synchronized (lifecycleLock) {
            return !disconnectRequested && !terminalCallbackDelivered && connectionState == ConnectionState.CONNECTED;
        }
    }

    private enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        CLOSING
    }
}
