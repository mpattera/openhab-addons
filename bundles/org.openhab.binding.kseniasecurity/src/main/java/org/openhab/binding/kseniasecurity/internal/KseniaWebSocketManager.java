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

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.WebSocketPingPongListener;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.core.io.net.http.WebSocketFactory;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.util.ThingWebClientUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link KseniaWebSocketManager} is responsible for managing a WebSocket connection to a specific URI. Consumer
 * must register a {@link KseniaWebSocketListener} in order to receive events from the WebSocket end point. A PING-PONG
 * mechanism is implemented to keep the connection alive.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public class KseniaWebSocketManager implements WebSocketListener, WebSocketPingPongListener {

    private static final String WEBSOCKET_PROTOCOL = "KS_WSOCK";

    private final Logger logger = LoggerFactory.getLogger(KseniaWebSocketManager.class);

    private final WebSocketClient client;
    private final KseniaWebSocketListener listener;
    private @Nullable Session session;

    private @Nullable ConnectionState connectionState;

    public KseniaWebSocketManager(WebSocketClient webSocketClient, KseniaWebSocketListener webSocketListener) {
        this.client = webSocketClient;
        this.listener = webSocketListener;
    }

    public KseniaWebSocketManager(ThingUID thingUID, WebSocketFactory webSocketFactory,
            KseniaWebSocketListener webSocketListener) {
        this.listener = webSocketListener;

        String consumerName = ThingWebClientUtil.buildWebClientConsumerName(thingUID, null);
        this.client = webSocketFactory.createWebSocketClient(consumerName);
    }

    public void connect(URI uri) {
        if (connectionState == ConnectionState.CONNECTED) {
            logger.debug("Already connected");
            return;
        } else if (connectionState == ConnectionState.CONNECTING) {
            logger.debug("Already connecting");
            return;
        } else if (connectionState == ConnectionState.CLOSING) {
            logger.debug("Already closing");
            return;
        }

        Future<Session> futureConnect = null;
        try {
            logger.info("Connecting to {}", uri.toString());
            connectionState = ConnectionState.CONNECTING;

            if (!client.isRunning()) {
                client.start();
            }
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            request.setSubProtocols(WEBSOCKET_PROTOCOL);
            futureConnect = client.connect(this, uri, request);
            futureConnect.get();
        } catch (Exception e) {
            logger.error("Error while connecting to {} {}", uri.toString(), e.getMessage());

            if (futureConnect != null) {
                futureConnect.cancel(true);
            }
        }
    }

    public void disconnect() {
        try {
            if (client.isRunning()) {
                client.stop();
            }
        } catch (Exception e) {
            logger.warn("Error while disconnecting {}", e.getMessage());
        }
    }

    public void sendMessage(String message) {
        if (session != null && session.isOpen()) {
            try {
                logger.debug("Sending message {}", message);

                session.getRemote().sendStringByFuture(message);
            } catch (Exception e) {
                logger.error("Error while sending message {}", e.getMessage());
            }
        } else {
            logger.debug("Cannot send message (session not open)");
        }
    }

    @Override
    public void onWebSocketConnect(@Nullable Session session) {
        logger.info("Connected to {} (session {})", session.getRemoteAddress().toString(), session.hashCode());
        connectionState = ConnectionState.CONNECTED;

        this.session = session;
        listener.connectionEstablished();
    }

    @Override
    public void onWebSocketClose(int statusCode, @Nullable String reason) {
        logger.info("Connection to {} (session {}) closed {}", session.getRemoteAddress().toString(),
                session.hashCode(), reason);
        connectionState = ConnectionState.CLOSED;

        listener.connectionClosed();
    }

    @Override
    public void onWebSocketError(@Nullable Throwable cause) {
        logger.error("Connection error {}", cause.getMessage());
        connectionState = ConnectionState.ERROR;

        listener.connectionError();
    }

    @Override
    public void onWebSocketBinary(byte @Nullable [] payload, int offset, int len) {
    }

    @Override
    public void onWebSocketText(@Nullable String message) {
        logger.debug("Received message {}", message);

        listener.messageReceived(message);
    }

    @Override
    public void onWebSocketPing(@Nullable ByteBuffer payload) {
        logger.debug("Received PING");
        if (session != null && session.isOpen()) {
            try {
                logger.debug("Sending PONG");

                session.getRemote().sendPong(payload);
            } catch (IOException e) {
                logger.error("Error while sending PONG {}", e.getMessage());
            }
        } else {
            logger.debug("Cannot send PONG (session not open)");
        }
    }

    @Override
    public void onWebSocketPong(@Nullable ByteBuffer payload) {
        logger.debug("Received PONG");
    }

    private enum ConnectionState {
        CONNECTING,
        CONNECTED,
        CLOSING,
        CLOSED,
        ERROR
    }

    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED;
    }
}
