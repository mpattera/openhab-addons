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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Lares40ReadRequestManager}.
 *
 * @author Michele Pattera - Initial contribution
 */
public class Lares40ReadRequestManagerTest {

    @Test
    public void testFinalCommandIdsSkipThePendingReadEvenAfterWrapping() {
        Lares40ReadRequestManager manager = new Lares40ReadRequestManager();
        Lares40PendingReadRequest read = manager
                .begin(Lares40ReadOperation.DIAGNOSTIC, Lares40ReadRequest.forDiagnosticCommand("ALL").orElseThrow())
                .orElseThrow();
        assertEquals(1, read.commandId());
        for (int expected = 2; expected <= 65534; expected++) {
            assertEquals(expected, manager.nextCommandId());
        }
        assertEquals(2, manager.nextCommandId());
        assertEquals(read, manager.getPendingRequest().orElseThrow());
    }

    @Test
    public void testSerializesAndCorrelatesPendingReads() {
        Lares40ReadRequestManager manager = new Lares40ReadRequestManager();
        Lares40ReadRequest request = Lares40ReadRequest.forDiagnosticCommand("ZONES").orElseThrow();

        Lares40PendingReadRequest pending = manager.begin(Lares40ReadOperation.DIAGNOSTIC, request).orElseThrow();

        assertEquals(1, pending.commandId());
        assertEquals(Lares40ReadOperation.DIAGNOSTIC, pending.operation());
        assertTrue(manager.begin(Lares40ReadOperation.DIAGNOSTIC, request).isEmpty());
        assertTrue(manager.take(2).isEmpty());
        assertEquals(pending, manager.take(1).orElseThrow());
        assertTrue(manager.getPendingRequest().isEmpty());
        assertEquals(2, manager.begin(Lares40ReadOperation.INITIAL_INVENTORY, request).orElseThrow().commandId());
    }

    @Test
    public void testCancelsAndClearsOnlyTheCurrentRead() {
        Lares40ReadRequestManager manager = new Lares40ReadRequestManager();
        Lares40ReadRequest request = Lares40ReadRequest.forDiagnosticCommand("ZONES").orElseThrow();
        Lares40PendingReadRequest pending = manager.begin(Lares40ReadOperation.DIAGNOSTIC, request).orElseThrow();

        assertFalse(manager.cancel(new Lares40PendingReadRequest(2, Lares40ReadOperation.DIAGNOSTIC, request)));
        assertTrue(manager.cancel(pending));
        assertTrue(manager.getPendingRequest().isEmpty());

        manager.begin(Lares40ReadOperation.DIAGNOSTIC, request).orElseThrow();
        manager.clear();
        assertTrue(manager.getPendingRequest().isEmpty());
    }
}
