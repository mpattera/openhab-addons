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
 * Current real-time condition of an alarm zone, independent of whether the panel has generated an alarm event.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public enum ZoneCondition {
    REST,
    ALARM,
    FAULT,
    TAMPER,
    ERROR,
    UNKNOWN
}
