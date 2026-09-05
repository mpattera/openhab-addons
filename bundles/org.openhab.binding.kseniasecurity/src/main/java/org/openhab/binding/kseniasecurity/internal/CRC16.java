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
package org.openhab.binding.kseniasecurity.internal;

import java.nio.charset.StandardCharsets;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Calculates the CRC-16 values used by the Lares 4.0 protocol.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public final class CRC16 {

    private static final int POLY_CCITT = 0x1021;

    private CRC16() {
    }

    /**
     * Calculates a CRC using the protocol's CCITT polynomial.
     *
     * @param data the text to checksum
     * @return the formatted CRC value
     */
    public static String calculate(String data) {
        return calculate(data, POLY_CCITT);
    }

    /**
     * Calculates a CRC for text using a supplied polynomial.
     *
     * @param data the text to checksum
     * @param polynomial the CRC polynomial
     * @return the formatted CRC value
     */
    public static String calculate(String data, int polynomial) {
        return String.format("0x%04X", calculate(data.getBytes(StandardCharsets.UTF_8), polynomial));
    }

    /**
     * Calculates a CRC for bytes using a supplied polynomial.
     *
     * @param data the bytes to checksum
     * @param polynomial the CRC polynomial
     * @return the CRC value
     */
    public static short calculate(byte[] data, int polynomial) {
        int crc = 0xFFFF;

        for (byte b : data) {
            for (int i = 0x80; i != 0; i >>= 1) {
                boolean flagCRC = (crc & 0x8000) != 0;
                crc <<= 1;
                if ((b & i) != 0) {
                    crc++;
                }
                if (flagCRC) {
                    crc ^= polynomial;
                }
            }
        }

        return (short) (crc & 0xFFFF);
    }
}
