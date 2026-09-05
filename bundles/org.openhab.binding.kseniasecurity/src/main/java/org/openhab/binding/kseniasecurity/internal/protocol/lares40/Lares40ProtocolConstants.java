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
 * Wire-level names and values defined by the Lares 4.0 WebSocket protocol.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public final class Lares40ProtocolConstants {

    public static final String COMMAND_GENERIC = "GENERIC";
    public static final String COMMAND_LOGIN = "LOGIN";
    public static final String COMMAND_LOGIN_RESPONSE = "LOGIN_RES";
    public static final String COMMAND_LOGOUT = "LOGOUT";
    public static final String COMMAND_LOGOUT_RESPONSE = "LOGOUT_RES";
    public static final String COMMAND_READ = "READ";
    public static final String COMMAND_READ_RESPONSE = "READ_RES";
    public static final String COMMAND_REALTIME = "REALTIME";
    public static final String COMMAND_REALTIME_RESPONSE = "REALTIME_RES";

    public static final String PAYLOAD_TYPE_ERROR = "ERROR";
    public static final String PAYLOAD_TYPE_ALL = "ALL";
    public static final String PAYLOAD_TYPE_USER_ALL = "USR_ALL";
    public static final String PAYLOAD_TYPE_CONFIGURATION_ALL = "CFG_ALL";
    public static final String PAYLOAD_TYPE_STATUS_ALL = "STATUS_ALL";
    public static final String PAYLOAD_TYPE_MULTIPLE_TYPES = "MULTI_TYPES";
    public static final String PAYLOAD_TYPE_PARTITIONS = "PARTITIONS";
    public static final String PAYLOAD_TYPE_ZONES = "ZONES";
    public static final String PAYLOAD_TYPE_USER = "USER";
    public static final String PAYLOAD_TYPE_SUPERVISOR = "IP_SUPERV";
    public static final String PAYLOAD_TYPE_REGISTER = "REGISTER";
    public static final String PAYLOAD_TYPE_REGISTER_ACKNOWLEDGEMENT = "REGISTER_ACK";
    public static final String PAYLOAD_TYPE_CHANGES = "CHANGES";

    public static final String PAYLOAD_PIN = "PIN";
    public static final String PAYLOAD_LOGIN_ID = "ID_LOGIN";
    public static final String PAYLOAD_ITEMS_RANGE = "ID_ITEMS_RANGE";
    public static final String PAYLOAD_ITEMS_RANGE_ALL = "ALL";
    public static final String PAYLOAD_DESCRIPTION = "DES";
    public static final String PAYLOAD_RESULT = "RESULT";
    public static final String PAYLOAD_RESULT_OK = "OK";
    public static final String PAYLOAD_RESULT_DETAIL = "RESULT_DETAIL";
    public static final String PAYLOAD_SYSTEM_LANGUAGE = "SYSTEM_LANG";
    public static final String PAYLOAD_SESSION_STATE = "SESSION_STATE";
    public static final String PAYLOAD_FREEZE_STATE = "FREEZE_STATE";
    public static final String PAYLOAD_VERSION = "VER_LITE";
    public static final String PAYLOAD_VERSION_FW = "FW";
    public static final String PAYLOAD_VERSION_WS = "WS";
    public static final String PAYLOAD_TYPES = "TYPES";
    public static final String PAYLOAD_STATUS_PARTITIONS = "STATUS_PARTITIONS";
    public static final String PAYLOAD_STATUS_ZONES = "STATUS_ZONES";
    public static final String PAYLOAD_ENTRY_ID = "ID";

    public static final String FIELD_SENDER = "SENDER";
    public static final String FIELD_RECEIVER = "RECEIVER";
    public static final String FIELD_COMMAND = "CMD";
    public static final String FIELD_ID = "ID";
    public static final String FIELD_PAYLOAD_TYPE = "PAYLOAD_TYPE";
    public static final String FIELD_PAYLOAD = "PAYLOAD";
    public static final String FIELD_TIMESTAMP = "TIMESTAMP";
    public static final String FIELD_CRC16 = "CRC_16";

    private Lares40ProtocolConstants() {
    }
}
