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

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Validated, immutable connection settings for a Lares 4.0 panel.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public record Lares40PanelSettings(String host, int port, boolean useSecureConnection,
        boolean trustSelfSignedCertificate, int reconnectInterval, int responseTimeout, KseniaLoginType loginType,
        String pin) {

    private static final String WEBSOCKET_PATH = "/KseniaWsock";

    /**
     * Builds the panel WebSocket endpoint URI.
     *
     * @return the WebSocket URI
     * @throws URISyntaxException if the configured host cannot be used in a URI
     */
    public URI getWebSocketUri() throws URISyntaxException {
        return new URI(useSecureConnection ? "wss" : "ws", null, host, port, WEBSOCKET_PATH, null, null);
    }

    /**
     * Determines whether a panel-specific trust manager must be created before opening the WebSocket.
     *
     * @return {@code true} when the connection is secure and self-signed certificate trust is enabled
     */
    public boolean requiresSelfSignedCertificateTrust() {
        return useSecureConnection && trustSelfSignedCertificate;
    }
}
