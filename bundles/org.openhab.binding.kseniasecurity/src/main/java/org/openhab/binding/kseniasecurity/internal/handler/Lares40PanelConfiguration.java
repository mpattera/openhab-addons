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

import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Mutable bridge configuration populated by openHAB.
 *
 * <p>
 * Nullable raw values are converted into {@link Lares40PanelSettings} before the handler starts any network work.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public class Lares40PanelConfiguration {
    public @Nullable String host;
    public @Nullable Integer port;
    public @Nullable Boolean useSecureConnection;
    public @Nullable Boolean trustSelfSignedCertificate;
    public @Nullable Integer reconnectInterval;
    public @Nullable Integer responseTimeout;
    public @Nullable String loginType;
    public @Nullable String loginCode;

    /**
     * Validates raw configuration and returns safe immutable settings.
     *
     * @return validated settings, or {@code null} if a required value is absent or invalid
     */
    public @Nullable Lares40PanelSettings toSettings() {
        @Nullable
        String configuredHost = host;
        @Nullable
        Integer configuredPort = port;
        @Nullable
        Boolean configuredUseSecureConnection = useSecureConnection;
        @Nullable
        Boolean configuredTrustSelfSignedCertificate = trustSelfSignedCertificate;
        @Nullable
        Integer configuredReconnectInterval = reconnectInterval;
        @Nullable
        Integer configuredResponseTimeout = responseTimeout;
        @Nullable
        String configuredLoginType = loginType;
        @Nullable
        String configuredLoginCode = loginCode;
        if (configuredHost == null || configuredPort == null || configuredUseSecureConnection == null
                || configuredTrustSelfSignedCertificate == null || configuredReconnectInterval == null
                || configuredResponseTimeout == null || configuredLoginType == null || configuredLoginCode == null) {
            return null;
        }

        String normalizedHost = configuredHost.strip();
        KseniaLoginType normalizedLoginType;
        try {
            normalizedLoginType = KseniaLoginType.valueOf(configuredLoginType.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        int configuredPortValue = configuredPort.intValue();
        int configuredReconnectIntervalValue = configuredReconnectInterval.intValue();
        int configuredResponseTimeoutValue = configuredResponseTimeout.intValue();
        if (normalizedHost.isEmpty() || configuredPortValue < 1 || configuredPortValue > 65535
                || configuredReconnectIntervalValue < 1 || configuredResponseTimeoutValue < 1
                || configuredLoginCode.isBlank()) {
            return null;
        }

        return new Lares40PanelSettings(normalizedHost, configuredPortValue,
                configuredUseSecureConnection.booleanValue(), configuredTrustSelfSignedCertificate.booleanValue(),
                configuredReconnectIntervalValue, configuredResponseTimeoutValue, normalizedLoginType,
                configuredLoginCode);
    }
}
