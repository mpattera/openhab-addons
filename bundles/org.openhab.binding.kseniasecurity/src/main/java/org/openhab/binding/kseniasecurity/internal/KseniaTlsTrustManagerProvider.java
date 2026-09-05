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

import java.net.MalformedURLException;
import java.security.cert.CertificateException;

import javax.net.ssl.X509ExtendedTrustManager;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.io.net.http.PEMTrustManager;
import org.openhab.core.io.net.http.TlsTrustManagerProvider;

/**
 * Provides trust for the self-signed certificate of one Ksenia panel endpoint.
 *
 * <p>
 * The certificate is retrieved once from the configured endpoint and used to build a dedicated trust manager. The
 * provider deliberately has no trust-all fallback: if the certificate cannot be obtained, the bridge remains offline
 * instead of weakening TLS validation for the whole runtime.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public class KseniaTlsTrustManagerProvider implements TlsTrustManagerProvider {

    private final String hostName;
    private final X509ExtendedTrustManager trustManager;

    /**
     * Creates a provider for one panel endpoint.
     *
     * @param host the configured hostname or IP address
     * @param port the configured HTTPS/WSS port
     * @throws CertificateException if the panel certificate endpoint cannot be contacted or its certificate cannot be
     *             read
     * @throws MalformedURLException if the endpoint cannot be represented as an HTTPS URL
     */
    public KseniaTlsTrustManagerProvider(String host, int port) throws CertificateException, MalformedURLException {
        hostName = formatHostName(host, port);
        trustManager = PEMTrustManager.getInstanceFromServer("https://" + hostName);
    }

    @Override
    public String getHostName() {
        return hostName;
    }

    @Override
    public X509ExtendedTrustManager getTrustManager() {
        return trustManager;
    }

    private static String formatHostName(String host, int port) {
        return host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]:" + port : host + ":" + port;
    }
}
