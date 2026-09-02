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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
public class Lares40PanelConfiguration {
    public @Nullable String host;
    public int port;
    public boolean useSSL;
    public int reconnectInterval;
    public @Nullable String loginType;
    public @Nullable String pin;

    public boolean isValid() {
        return host != null && port > 0 && loginType != null && pin != null;
    }
}
