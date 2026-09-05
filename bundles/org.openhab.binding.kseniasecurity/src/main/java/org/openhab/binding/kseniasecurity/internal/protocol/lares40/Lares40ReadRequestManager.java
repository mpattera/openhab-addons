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

import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Serializes Lares {@code READ} requests and correlates their responses by command identifier.
 *
 * <p>
 * The protocol allows command identifiers, but the binding intentionally keeps a single read in flight. This avoids
 * associating a response with stale state after a reconnect, and is sufficient for diagnostics, metadata retrieval,
 * and discovery.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public final class Lares40ReadRequestManager {

    private static final int FIRST_COMMAND_ID = 1;
    private static final int LAST_COMMAND_ID = 65534;

    private int nextCommandId = FIRST_COMMAND_ID;
    private @Nullable Lares40PendingReadRequest pendingRequest;

    /**
     * Starts a read request if no other read is in flight.
     *
     * @param operation the binding operation initiating the read
     * @param request the read to send
     * @return the correlated request, or empty if another read is pending
     */
    public synchronized Optional<Lares40PendingReadRequest> begin(Lares40ReadOperation operation,
            Lares40ReadRequest request) {
        if (pendingRequest != null) {
            return Optional.empty();
        }

        Lares40PendingReadRequest createdRequest = new Lares40PendingReadRequest(nextCommandId(), operation, request);
        pendingRequest = createdRequest;
        return Optional.of(createdRequest);
    }

    /**
     * Returns the request matching a response and removes it from the pending slot.
     *
     * @param commandId the identifier from {@code READ_RES}
     * @return the matching request, or empty when it is unexpected
     */
    public synchronized Optional<Lares40PendingReadRequest> take(int commandId) {
        @Nullable
        Lares40PendingReadRequest request = pendingRequest;
        if (request == null || request.commandId() != commandId) {
            return Optional.empty();
        }

        pendingRequest = null;
        return Optional.of(request);
    }

    /**
     * Returns and removes the sole read in flight without correlating it by identifier.
     *
     * <p>
     * This is only for the legacy {@code GENERIC} error response form, which the protocol documentation shows with
     * command identifier {@code 0}. Callers must ensure that no other tracked request can be associated with that
     * response.
     *
     * @return the current read, if any
     */
    public synchronized Optional<Lares40PendingReadRequest> takePendingRequest() {
        @Nullable
        Lares40PendingReadRequest request = pendingRequest;
        pendingRequest = null;
        return Optional.ofNullable(request);
    }

    /**
     * Cancels a request only if it is still the one in flight.
     *
     * @param expectedRequest the request to cancel
     * @return {@code true} when the request was pending and has been cancelled
     */
    public synchronized boolean cancel(Lares40PendingReadRequest expectedRequest) {
        if (!expectedRequest.equals(pendingRequest)) {
            return false;
        }

        pendingRequest = null;
        return true;
    }

    /** Clears the pending request, for example when a panel session ends. */
    public synchronized void clear() {
        pendingRequest = null;
    }

    /**
     * Returns the current request without removing it.
     *
     * @return the current request, if any
     */
    public synchronized Optional<Lares40PendingReadRequest> getPendingRequest() {
        return Optional.ofNullable(pendingRequest);
    }

    /**
     * Allocates an identifier for the final logout without reusing the identifier of an in-flight read.
     * The caller must stop submitting reads before using this identifier for another command.
     *
     * @return the next available wire identifier, excluding the startup identifier 65535
     */
    public synchronized int nextCommandId() {
        int commandId = nextCommandId;
        nextCommandId = commandId == LAST_COMMAND_ID ? FIRST_COMMAND_ID : commandId + 1;
        Lares40PendingReadRequest request = pendingRequest;
        if (request != null && request.commandId() == commandId) {
            return nextCommandId();
        }
        return commandId;
    }
}
