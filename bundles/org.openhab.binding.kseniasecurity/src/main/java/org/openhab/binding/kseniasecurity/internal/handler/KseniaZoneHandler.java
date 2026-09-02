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
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.kseniasecurity.internal.KseniaBindingConstants;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

/**
 * The {@link KseniaZoneHandler} is responsible for handling commands, which are sent
 * to one of the channels.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public class KseniaZoneHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(KseniaZoneHandler.class);

    private @Nullable KseniaZoneConfiguration config;

    public KseniaZoneHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing the handler for {}", getThing().getUID());
        updateStatus(ThingStatus.UNKNOWN);

        scheduler.execute(this::initializeAsync);
    }

    private void initializeAsync() {
        config = getConfigAs(KseniaZoneConfiguration.class);
        if (!config.isValid()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid configuration");
            return;
        }

        logger.debug("Initializing zone {}", config.id);
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof Lares40PanelHandler panelHandler) {
            panelHandler.registerZone(config.id, this);
            updateStatus(ThingStatus.OFFLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
        }
    }

    @Override
    public void dispose() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof Lares40PanelHandler panelHandler) {
            panelHandler.unregisterZone(config.id);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Zone is read-only in this implementation
        logger.debug("Ignoring command {} for read-only channel {}", command, channelUID);
    }

    public void updateFromRealtime(JsonObject json) {
        if (json == null || !json.isJsonObject()) {
            logger.debug("Invalid payload for zone {}", config.id);
            return;
        }

        String id = json.has("ID") ? json.get("ID").getAsString() : null;
        if (id == null || !id.equals(String.valueOf(config.id))) {
            return; // not for this zone
        }

        if (json.has("DES")) {
            updateState(KseniaBindingConstants.CHANNEL_ZONE_DESCRIPTION,
                    new StringType(json.get("DES").getAsString()));
        }
        if (json.has("STA")) {
            updateState(KseniaBindingConstants.CHANNEL_ZONE_REALTIME_STATE,
                    new StringType(json.get("STA").getAsString()));
        }
        if (json.has("BYP")) {
            updateState(KseniaBindingConstants.CHANNEL_ZONE_BYPASS_STATE,
                    new StringType(json.get("BYP").getAsString()));
        }
        if (json.has("T")) {
            updateState(KseniaBindingConstants.CHANNEL_ZONE_TAMPER_STATE, new StringType(json.get("T").getAsString()));
        }
        if (json.has("A")) {
            updateState(KseniaBindingConstants.CHANNEL_ZONE_ALARM_STATE, new StringType(json.get("A").getAsString()));
        }
        if (json.has("FM")) {
            updateState(KseniaBindingConstants.CHANNEL_ZONE_FAULT_MEMORY,
                    new StringType(json.get("FM").getAsString()));
        }

        if (getThing().getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.ONLINE);
        }
    }
}
