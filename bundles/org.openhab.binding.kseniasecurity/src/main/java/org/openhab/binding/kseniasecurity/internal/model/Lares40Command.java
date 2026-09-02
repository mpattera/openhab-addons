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
package org.openhab.binding.kseniasecurity.internal.model;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

@NonNullByDefault
public class Lares40Command {

    public static final String COMMAND_LOGIN = "LOGIN";
    public static final String COMMAND_LOGINRES = "LOGIN_RES";
    public static final String COMMAND_LOGOUT = "LOGOUT";
    public static final String COMMAND_LOGOUTRES = "LOGOUT_RES";
    public static final String COMMAND_TYPE_USER = "USER";
    public static final String COMMAND_TYPE_IPSUPERV = "IP_SUPERV";
    public static final String COMMAND_PAYLOAD_PIN = "PIN";
    public static final String COMMAND_PAYLOAD_IDLOGIN = "ID_LOGIN";
    public static final String COMMAND_PAYLOAD_DESCRIPTION = "DESCRIPTION";
    public static final String COMMAND_PAYLOAD_RESULT = "RESULT";
    public static final String COMMAND_PAYLOAD_RESULTOK = "OK";
    public static final String COMMAND_PAYLOAD_RESULTDETAIL = "RESULT_DETAIL";
    public static final String COMMAND_PAYLOAD_SYSTEMLANGUAGE = "SYSTEM_LANG";
    public static final String COMMAND_PAYLOAD_SESSIONSTATE = "SESSION_STATE";
    public static final String COMMAND_PAYLOAD_FREEZESTATE = "FREEZE_STATE";
    public static final String COMMAND_PAYLOAD_VERSION = "VER_LITE";
    public static final String COMMAND_REALTIME = "REALTIME";
    public static final String COMMAND_REALTIMERES = "REALTIME_RES";
    public static final String COMMAND_TYPE_REGISTER = "REGISTER";
    public static final String COMMAND_PAYLOAD_TYPES = "TYPES";
    public static final String COMMAND_TYPE_REGISTERACK = "REGISTER_ACK";
    public static final String COMMAND_TYPE_CHANGES = "CHANGES";
    public static final String COMMAND_PAYLOAD_STATUSPARTITIONS = "STATUS_PARTITIONS";
    public static final String COMMAND_PAYLOAD_STATUSPARTITIONSID = "ID";
    public static final String COMMAND_PAYLOAD_STATUSZONES = "STATUS_ZONES";
    public static final String COMMAND_PAYLOAD_STATUSZONESID = "ID";

    public static final String KEY_SENDER = "SENDER";
    public static final String KEY_RECEIVER = "RECEIVER";
    public static final String KEY_CMD = "CMD";
    public static final String KEY_ID = "ID";
    public static final String KEY_PAYLOAD_TYPE = "PAYLOAD_TYPE";
    public static final String KEY_PAYLOAD = "PAYLOAD";
    public static final String KEY_TIMESTAMP = "TIMESTAMP";
    public static final String KEY_CRC16 = "CRC_16";

    @SerializedName(KEY_SENDER)
    public @Nullable String sender;
    @SerializedName(KEY_RECEIVER)
    public @Nullable String receiver;
    @SerializedName(KEY_CMD)
    public @Nullable String cmd;
    @SerializedName(KEY_ID)
    public @Nullable String id;
    @SerializedName(KEY_PAYLOAD_TYPE)
    public @Nullable String payloadType;
    @SerializedName(KEY_PAYLOAD)
    public @Nullable JsonElement payload;
    @SerializedName(KEY_TIMESTAMP)
    public @Nullable String timestamp;
    @SerializedName(KEY_CRC16)
    public @Nullable String crc16;

    public Lares40Command() {
    }
}
