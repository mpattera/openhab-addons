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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Verifies the public channel contract declared by the Thing type descriptions.
 *
 * @author Michele Pattera - Initial contribution
 */
public class ThingTypesContractTest {

    @Test
    public void testDeclaredChannelsMatchBindingConstants() throws Exception {
        Document document = loadThingTypes();

        assertEquals(Set.of(KseniaBindingConstants.CHANNEL_PANEL_SYSTEM_LANG,
                KseniaBindingConstants.CHANNEL_PANEL_SESSION_STATE, KseniaBindingConstants.CHANNEL_PANEL_FREEZE_STATE,
                KseniaBindingConstants.CHANNEL_PANEL_FW_VERSION, KseniaBindingConstants.CHANNEL_PANEL_WS_VERSION,
                KseniaBindingConstants.CHANNEL_PANEL_DIAGNOSTIC_READ), getChannelIds(document, "bridge-type", "panel"));

        assertEquals(
                Set.of(KseniaBindingConstants.CHANNEL_PARTITION_DESCRIPTION,
                        KseniaBindingConstants.CHANNEL_PARTITION_REALTIME_STATE,
                        KseniaBindingConstants.CHANNEL_PARTITION_ARM_STATUS,
                        KseniaBindingConstants.CHANNEL_PARTITION_ENTRY_DELAY_STATUS,
                        KseniaBindingConstants.CHANNEL_PARTITION_EXIT_DELAY_STATUS,
                        KseniaBindingConstants.CHANNEL_PARTITION_DELAY_REMAINING,
                        KseniaBindingConstants.CHANNEL_PARTITION_ALARM_STATE,
                        KseniaBindingConstants.CHANNEL_PARTITION_ALARM_ACTIVE,
                        KseniaBindingConstants.CHANNEL_PARTITION_ALARM_MEMORY,
                        KseniaBindingConstants.CHANNEL_PARTITION_TAMPER_STATE,
                        KseniaBindingConstants.CHANNEL_PARTITION_TAMPER_ACTIVE,
                        KseniaBindingConstants.CHANNEL_PARTITION_TAMPER_MEMORY),
                getChannelIds(document, "thing-type", "partition"));

        assertEquals(Set.of(KseniaBindingConstants.CHANNEL_ZONE_DESCRIPTION,
                KseniaBindingConstants.CHANNEL_ZONE_REALTIME_STATE, KseniaBindingConstants.CHANNEL_ZONE_REST_STATUS,
                KseniaBindingConstants.CHANNEL_ZONE_ALARM_STATUS, KseniaBindingConstants.CHANNEL_ZONE_FAULT_STATUS,
                KseniaBindingConstants.CHANNEL_ZONE_TAMPER_STATUS, KseniaBindingConstants.CHANNEL_ZONE_ERROR_STATUS,
                KseniaBindingConstants.CHANNEL_ZONE_BYPASS_STATE, KseniaBindingConstants.CHANNEL_ZONE_BYPASS_STATUS,
                KseniaBindingConstants.CHANNEL_ZONE_ALARM_STATE, KseniaBindingConstants.CHANNEL_ZONE_ALARM_ACTIVE,
                KseniaBindingConstants.CHANNEL_ZONE_ALARM_MEMORY, KseniaBindingConstants.CHANNEL_ZONE_TAMPER_STATE,
                KseniaBindingConstants.CHANNEL_ZONE_TAMPER_ACTIVE, KseniaBindingConstants.CHANNEL_ZONE_TAMPER_MEMORY,
                KseniaBindingConstants.CHANNEL_ZONE_FAULT_STATE, KseniaBindingConstants.CHANNEL_ZONE_FAULT_MEMORY),
                getChannelIds(document, "thing-type", "zone"));
    }

    private Document loadThingTypes() throws Exception {
        InputStream stream = Objects.requireNonNull(getClass().getResourceAsStream("/OH-INF/thing/thing-types.xml"));
        try (stream) {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
        }
    }

    @Test
    public void testDiagnosticReadHasNoPredefinedCommandOptions() throws Exception {
        Element channel = getElementById(loadThingTypes().getElementsByTagName("channel-type"), "diagnosticRead");
        assertEquals(0, channel.getElementsByTagName("option").getLength());
    }

    @Test
    public void testResponseTimeoutHasAnOptionalAdvancedDefault() throws Exception {
        Element panel = getElementById(loadThingTypes().getElementsByTagName("bridge-type"), "panel");
        NodeList parameters = panel.getElementsByTagName("parameter");
        for (int index = 0; index < parameters.getLength(); index++) {
            Element parameter = (Element) parameters.item(index);
            if ("responseTimeout".equals(parameter.getAttribute("name"))) {
                assertEquals("false", parameter.getAttribute("required"));
                assertEquals("1", parameter.getAttribute("min"));
                assertEquals("60", parameter.getElementsByTagName("default").item(0).getTextContent());
                assertEquals("true", parameter.getElementsByTagName("advanced").item(0).getTextContent());
                return;
            }
        }
        throw new AssertionError("Missing responseTimeout parameter");
    }

    private Set<String> getChannelIds(Document document, String typeElementName, String typeId) {
        Element type = getElementById(document.getElementsByTagName(typeElementName), typeId);
        NodeList channelNodes = type.getElementsByTagName("channel");
        Set<String> channelIds = new HashSet<>();
        for (int index = 0; index < channelNodes.getLength(); index++) {
            Node channelNode = channelNodes.item(index);
            String channelId = ((Element) channelNode).getAttribute("id");
            assertTrue(channelIds.add(channelId), () -> "Duplicate channel ID in " + typeId + ": " + channelId);
        }
        return channelIds;
    }

    private Element getElementById(NodeList nodes, String expectedId) {
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            if (expectedId.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        throw new AssertionError("Missing Thing type " + expectedId);
    }
}
