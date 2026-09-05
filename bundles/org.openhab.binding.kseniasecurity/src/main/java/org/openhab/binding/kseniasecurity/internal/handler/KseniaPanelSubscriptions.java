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

/**
 * Child subscriptions provided by a panel bridge, independently of its protocol and transport.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
interface KseniaPanelSubscriptions {

    /** Registers a zone handler and replays the data available in the current panel session. */
    void addZoneHandler(int zoneId, KseniaZoneHandler handler);

    /** Removes a zone handler from both state and metadata delivery. */
    void removeZoneHandler(int zoneId, KseniaZoneHandler handler);

    /** Registers a partition handler and replays the data available in the current panel session. */
    void addPartitionHandler(int partitionId, KseniaPartitionHandler handler);

    /** Removes a partition handler from both state and metadata delivery. */
    void removePartitionHandler(int partitionId, KseniaPartitionHandler handler);
}
