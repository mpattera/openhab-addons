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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Receives connection lifecycle events and validated text messages from a panel WebSocket.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public interface KseniaWebSocketListener {

    /**
     * Called when a WebSocket session has been established.
     *
     * @param manager the manager that owns the established session
     */
    void connectionEstablished(KseniaWebSocketManager manager);

    /**
     * Called when a WebSocket session was closed unexpectedly.
     *
     * @param manager the manager that owned the closed session
     * @param statusCode the WebSocket close status code
     * @param reason the optional close reason supplied by the peer
     */
    void connectionClosed(KseniaWebSocketManager manager, int statusCode, @Nullable String reason);

    /**
     * Called when the WebSocket could not be opened or reported an error.
     *
     * @param manager the manager that owned the failed session
     * @param cause the optional error reported by Jetty
     */
    void connectionError(KseniaWebSocketManager manager, @Nullable Throwable cause);

    /**
     * Receives a non-empty WebSocket text frame.
     *
     * @param manager the manager that received the message
     * @param message the raw protocol frame
     */
    void messageReceived(KseniaWebSocketManager manager, String message);

    /**
     * Called when Jetty completes writing a text message. This is not a panel acknowledgement.
     *
     * @param manager the manager that sent the message
     * @param message the exact encoded protocol frame passed to the transport
     */
    void messageSent(KseniaWebSocketManager manager, String message);
}
