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
package org.openhab.binding.kseniasecurity.internal.protocol.lares40;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The binding-level purpose of a serialized Lares {@code READ} operation.
 *
 * <p>
 * The protocol itself does not carry this distinction. It lets the panel handler route a correlated response to the
 * appropriate, protocol-independent consumer.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public enum Lares40ReadOperation {
    /** A read explicitly requested through the advanced diagnostic channel. */
    DIAGNOSTIC("diagnostic"),
    /** The one-time static inventory of zones and partitions. */
    INITIAL_INVENTORY("initial inventory");

    private final String logName;

    Lares40ReadOperation(String logName) {
        this.logName = logName;
    }

    /**
     * Returns a concise, non-protocol name suitable for a log message.
     *
     * @return the operation name
     */
    public String logName() {
        return logName;
    }
}
