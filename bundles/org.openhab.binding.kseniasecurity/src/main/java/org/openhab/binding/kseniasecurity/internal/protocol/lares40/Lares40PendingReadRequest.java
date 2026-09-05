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
 * A sent {@code READ} command waiting for its correlated response.
 *
 * @param commandId the protocol command identifier assigned by the binding
 * @param operation the binding operation that initiated the read
 * @param request the requested data set
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public record Lares40PendingReadRequest(int commandId, Lares40ReadOperation operation, Lares40ReadRequest request) {
}
