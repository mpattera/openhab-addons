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

import static org.junit.jupiter.api.Assertions.*;

import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Lares40PanelConfiguration}.
 *
 * @author Michele Pattera - Initial contribution
 */
public class Lares40PanelConfigurationTest {

    @Test
    public void testRejectsMissingOrInvalidRequiredValues() {
        Lares40PanelConfiguration configuration = new Lares40PanelConfiguration();

        assertNull(configuration.toSettings());

        configuration.host = "lares.local";
        configuration.loginType = "SUPERVISOR";
        configuration.loginCode = "123456";

        assertNull(configuration.toSettings());

        configuration.port = 0;
        configuration.useSecureConnection = true;
        configuration.trustSelfSignedCertificate = true;
        configuration.reconnectInterval = 60;
        configuration.responseTimeout = 90;

        assertNull(configuration.toSettings());
    }

    @Test
    public void testProducesNormalizedImmutableSettings() throws URISyntaxException {
        Lares40PanelConfiguration configuration = new Lares40PanelConfiguration();
        configuration.host = " lares.local ";
        configuration.port = 8443;
        configuration.useSecureConnection = true;
        configuration.trustSelfSignedCertificate = true;
        configuration.reconnectInterval = 60;
        configuration.responseTimeout = 90;
        configuration.loginType = " SUPERVISOR ";
        configuration.loginCode = "123456";

        Lares40PanelSettings settings = configuration.toSettings();
        if (settings == null) {
            throw new AssertionError("Expected valid panel settings");
        }

        assertEquals("lares.local", settings.host());
        assertEquals(90, settings.responseTimeout());
        assertEquals(KseniaLoginType.SUPERVISOR, settings.loginType());
        assertTrue(settings.trustSelfSignedCertificate());
        assertEquals("wss://lares.local:8443/KseniaWsock", settings.getWebSocketUri().toString());
    }

    @Test
    public void testUsesUnsecuredUriWhenConfigured() throws URISyntaxException {
        Lares40PanelConfiguration configuration = new Lares40PanelConfiguration();
        configuration.host = "lares.local";
        configuration.port = 8080;
        configuration.useSecureConnection = false;
        configuration.trustSelfSignedCertificate = false;
        configuration.reconnectInterval = 60;
        configuration.responseTimeout = 90;
        configuration.loginType = "SUPERVISOR";
        configuration.loginCode = "123456";

        Lares40PanelSettings settings = configuration.toSettings();
        if (settings == null) {
            throw new AssertionError("Expected valid panel settings");
        }

        assertEquals("ws://lares.local:8080/KseniaWsock", settings.getWebSocketUri().toString());
    }

    @Test
    public void testRequiresSelfSignedCertificateTrustOnlyForSecureConnections() {
        assertTrue(
                new Lares40PanelSettings("lares.local", 443, true, true, 60, 90, KseniaLoginType.SUPERVISOR, "123456")
                        .requiresSelfSignedCertificateTrust());
        assertFalse(
                new Lares40PanelSettings("lares.local", 443, true, false, 60, 90, KseniaLoginType.SUPERVISOR, "123456")
                        .requiresSelfSignedCertificateTrust());
        assertFalse(
                new Lares40PanelSettings("lares.local", 80, false, true, 60, 90, KseniaLoginType.SUPERVISOR, "123456")
                        .requiresSelfSignedCertificateTrust());
        assertFalse(
                new Lares40PanelSettings("lares.local", 80, false, false, 60, 90, KseniaLoginType.SUPERVISOR, "123456")
                        .requiresSelfSignedCertificateTrust());
    }

    @Test
    public void testAcceptsOnlyBindingLoginRoles() {
        Lares40PanelConfiguration configuration = new Lares40PanelConfiguration();
        configuration.host = "lares.local";
        configuration.port = 443;
        configuration.useSecureConnection = true;
        configuration.trustSelfSignedCertificate = true;
        configuration.reconnectInterval = 60;
        configuration.responseTimeout = 90;
        configuration.loginCode = "123456";
        configuration.loginType = " user ";
        Lares40PanelSettings settings = configuration.toSettings();
        if (settings == null) {
            throw new AssertionError("Expected the USER role to be accepted");
        }
        assertEquals(KseniaLoginType.USER, settings.loginType());

        configuration.responseTimeout = null;
        assertNull(configuration.toSettings());
        configuration.responseTimeout = 0;
        assertNull(configuration.toSettings());
        configuration.responseTimeout = -1;
        assertNull(configuration.toSettings());
        configuration.responseTimeout = 90;

        configuration.loginType = "UNKNOWN_ROLE";
        assertNull(configuration.toSettings());
        configuration.loginType = "";
        assertNull(configuration.toSettings());
    }
}
