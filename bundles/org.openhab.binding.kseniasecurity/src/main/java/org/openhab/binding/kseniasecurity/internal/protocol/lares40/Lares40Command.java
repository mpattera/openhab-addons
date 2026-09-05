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
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

/**
 * Mutable representation of a Lares 4.0 wire command used solely by Gson at the protocol boundary.
 *
 * <p>
 * Every field is nullable because a remote peer can omit any JSON member. Callers must validate a decoded command
 * before using it.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public class Lares40Command {

    @SerializedName(Lares40ProtocolConstants.FIELD_SENDER)
    public @Nullable String sender;
    @SerializedName(Lares40ProtocolConstants.FIELD_RECEIVER)
    public @Nullable String receiver;
    @SerializedName(Lares40ProtocolConstants.FIELD_COMMAND)
    public @Nullable String command;
    @SerializedName(Lares40ProtocolConstants.FIELD_ID)
    public @Nullable String id;
    @SerializedName(Lares40ProtocolConstants.FIELD_PAYLOAD_TYPE)
    public @Nullable String payloadType;
    @SerializedName(Lares40ProtocolConstants.FIELD_PAYLOAD)
    public @Nullable JsonElement payload;
    @SerializedName(Lares40ProtocolConstants.FIELD_TIMESTAMP)
    public @Nullable String timestamp;
    @SerializedName(Lares40ProtocolConstants.FIELD_CRC16)
    public @Nullable String crc16;

    /** Creates an empty command for Gson deserialization. */
    public Lares40Command() {
    }
}
