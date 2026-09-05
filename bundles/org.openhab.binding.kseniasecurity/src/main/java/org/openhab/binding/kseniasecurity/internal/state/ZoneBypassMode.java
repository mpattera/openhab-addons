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
package org.openhab.binding.kseniasecurity.internal.state;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Bypass mode of an alarm zone.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public enum ZoneBypassMode {
    NOT_BYPASSED,
    AUTO_BYPASSED,
    MANUAL_BYPASS_DURING_ARMING,
    MANUAL_BYPASS,
    UNKNOWN
}
