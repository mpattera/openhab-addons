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

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.kseniasecurity.internal.KseniaWebSocketListener;
import org.openhab.binding.kseniasecurity.internal.KseniaWebSocketManager;
import org.openhab.binding.kseniasecurity.internal.KseniaBindingConstants;
import org.openhab.binding.kseniasecurity.internal.model.Lares40Command;
import org.openhab.binding.kseniasecurity.internal.model.Lares40CommandUtils;
import org.openhab.core.io.net.http.WebSocketFactory;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.util.ThingWebClientUtil;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The {@link Lares40PanelHandler} is responsible for handling commands, which are sent
 * to one of the channels.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public class Lares40PanelHandler extends BaseBridgeHandler implements KseniaWebSocketListener {

    private final Logger logger = LoggerFactory.getLogger(Lares40PanelHandler.class);

    private @Nullable Lares40PanelConfiguration config;

    private final WebSocketFactory webSocketFactory;
    private @Nullable KseniaWebSocketManager webSocketManager;

    private @Nullable ScheduledFuture<?> retryFuture;
    private final String senderGuid = UUID.randomUUID().toString();
    private String loginType = "";
    private String loginId = "";

    private final Map<Integer, KseniaPartitionHandler> partitionHandlers = new ConcurrentHashMap<>();
    private final Map<Integer, KseniaZoneHandler> zoneHandlers = new ConcurrentHashMap<>();

    private boolean enableReconnection = true;

    public Lares40PanelHandler(Bridge thing, WebSocketFactory webSocketFactory) {
        super(thing);
        this.webSocketFactory = webSocketFactory;
    }

    @Override
    public void initialize() {
        logger.debug("Initializing the handler for {}", getThing().getUID());
        updateStatus(ThingStatus.UNKNOWN);

        scheduler.execute(this::initializeAsync);
    }

    private void initializeAsync() {
        config = getConfigAs(Lares40PanelConfiguration.class);
        if (!config.isValid()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid configuration");
            return;
        }

        try {
            String consumerName = ThingWebClientUtil.buildWebClientConsumerName(thing.getUID(), null);
            SslContextFactory sslContextFactory = new SslContextFactory.Client(true);
            WebSocketClient webSocketClient = config.useSSL
                    ? webSocketFactory.createWebSocketClient(consumerName, sslContextFactory)
                    : webSocketFactory.createWebSocketClient(consumerName);
            webSocketManager = new KseniaWebSocketManager(webSocketClient, this);
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "WebSocket creation failed");
            return;
        }

        connect(0);
    }

    private void connect(int interval) {
        if (interval > 0) {
            logger.info("Scheduling connection attempt in {} seconds", interval);
            retryFuture = scheduler.schedule(() -> connect(0), interval, TimeUnit.SECONDS);
        } else {
            logger.debug("Executing connection attempt");
            URI uri = null;
            try {
                uri = new URI((config.useSSL ? "wss://" : "ws://") + config.host + ":" + config.port + "/KseniaWsock");
            } catch (Exception e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "URI creation failed");
            }

            if (uri != null) {
                enableReconnection = true;
                webSocketManager.connect(uri);
            }
        }
    }

    @Override
    public void dispose() {
        sendLogoutCommand();

        if (retryFuture != null && !retryFuture.isDone()) {
            retryFuture.cancel(true);
            retryFuture = null;
        }

        if (webSocketManager != null) {
            enableReconnection = false;
            webSocketManager.disconnect();
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // TODO Auto-generated method stub
    }

    public void registerPartition(int partitionId, KseniaPartitionHandler handler) {
        partitionHandlers.put(partitionId, handler);
    }

    public void unregisterPartition(int partitionId) {
        partitionHandlers.remove(partitionId);
    }

    public void registerZone(int zoneId, KseniaZoneHandler handler) {
        zoneHandlers.put(zoneId, handler);
    }

    public void unregisterZone(int zoneId) {
        zoneHandlers.remove(zoneId);
    }

    @Override
    public void connectionEstablished() {
        sendLoginCommand();
    }

    @Override
    public void connectionClosed() {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Connection closed");
        if (enableReconnection) {
            connect(config.reconnectInterval);
        }
    }

    @Override
    public void connectionError() {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Connection error");
        if (enableReconnection) {
            connect(config.reconnectInterval);
        }
    }

    @Override
    public void messageReceived(@Nullable String message) {
        Optional<Lares40Command> optionalCommand = Lares40CommandUtils
                .decodeCommand(Objects.requireNonNullElse(message, ""));
        if (optionalCommand.isEmpty()) {
            logger.debug("Failed to decode message {}", message);
            return;
        }

        Lares40Command command = optionalCommand.get();
        if (command.cmd == null) {
            logger.debug("Invalid command");
            return;
        }
        if (command.payloadType == null) {
            logger.debug("Invalid payload type");
            return;
        }

        switch (command.cmd) {
            case Lares40Command.COMMAND_LOGINRES:
                handleLoginResponse(command);
                break;

            case Lares40Command.COMMAND_LOGOUTRES:
                handleLogoutResponse(command);
                break;

            case Lares40Command.COMMAND_REALTIMERES:
                switch (command.payloadType) {
                    case Lares40Command.COMMAND_TYPE_REGISTERACK:
                        handleRegisterResponse(command);
                        break;

                    default:
                        logger.debug("Unhandled {} payload type {}", command.cmd, command.payloadType);
                        break;
                }
                break;

            case Lares40Command.COMMAND_REALTIME:
                switch (command.payloadType) {
                    case Lares40Command.COMMAND_TYPE_CHANGES:
                        handleRealtimeChanges(command);
                        break;

                    default:
                        logger.debug("Unhandled payload type {} for command {}", command.payloadType, command.cmd);
                        break;
                }
                break;

            default:
                logger.debug("Unhandled command {}", command.cmd);
                break;
        }
    }

    private void sendMessage(String message) {
        if (webSocketManager != null) {
            webSocketManager.sendMessage(message);
        }
    }

    private void sendLoginCommand() {
        String receiver = "";
        int id = 65535;
        String payloadType = config.loginType != null ? config.loginType : "";
        Map<String, String> payload = Map.of(Lares40Command.COMMAND_PAYLOAD_PIN,
                Objects.requireNonNullElse(config.pin, ""));
        String message = Lares40CommandUtils.buildCommand(senderGuid, receiver, Lares40Command.COMMAND_LOGIN, id,
                payloadType, payload);
        sendMessage(message);
    }

    private void handleLoginResponse(Lares40Command command) {
        String loginType = command.payloadType != null ? command.payloadType : "";

        if (command.payload == null || !command.payload.isJsonObject()) {
            logger.debug("Invalid payload");
            return;
        }
        JsonObject payload = command.payload.getAsJsonObject();

        String result = payload.has(Lares40Command.COMMAND_PAYLOAD_RESULT)
                ? payload.get(Lares40Command.COMMAND_PAYLOAD_RESULT).getAsString()
                : "";
        String detail = payload.has(Lares40Command.COMMAND_PAYLOAD_RESULTDETAIL)
                ? payload.get(Lares40Command.COMMAND_PAYLOAD_RESULTDETAIL).getAsString()
                : "";
        String loginId = payload.has(Lares40Command.COMMAND_PAYLOAD_IDLOGIN)
                ? payload.get(Lares40Command.COMMAND_PAYLOAD_IDLOGIN).getAsString()
                : "";
        String description = payload.has(Lares40Command.COMMAND_PAYLOAD_DESCRIPTION)
                ? payload.get(Lares40Command.COMMAND_PAYLOAD_DESCRIPTION).getAsString()
                : "";

        if (result.equalsIgnoreCase(Lares40Command.COMMAND_PAYLOAD_RESULTOK)) {
            logger.info("Login successful for {} [{}] \"{}\"", loginType, loginId, description);
            this.loginType = loginType;
            this.loginId = loginId;

            String sys_lang = payload.has(Lares40Command.COMMAND_PAYLOAD_SYSTEMLANGUAGE)
                    ? payload.get(Lares40Command.COMMAND_PAYLOAD_SYSTEMLANGUAGE).getAsString()
                    : "";
            updateState(KseniaBindingConstants.CHANNEL_PANEL_SYSTEM_LANG, new StringType(sys_lang));
            String sess_state = payload.has(Lares40Command.COMMAND_PAYLOAD_SESSIONSTATE)
                    ? payload.get(Lares40Command.COMMAND_PAYLOAD_SESSIONSTATE).getAsString()
                    : "";
            updateState(KseniaBindingConstants.CHANNEL_PANEL_SESSION_STATE, new StringType(sess_state));
            String freeze_state = payload.has(Lares40Command.COMMAND_PAYLOAD_FREEZESTATE)
                    ? payload.get(Lares40Command.COMMAND_PAYLOAD_FREEZESTATE).getAsString()
                    : "";
            updateState(KseniaBindingConstants.CHANNEL_PANEL_FREEZE_STATE, new StringType(freeze_state));

            JsonObject version = payload.has(Lares40Command.COMMAND_PAYLOAD_VERSION)
                    ? payload.get(Lares40Command.COMMAND_PAYLOAD_VERSION).getAsJsonObject()
                    : null;
            if (version != null) {
                if (version.has("FW")) {
                    updateState(KseniaBindingConstants.CHANNEL_PANEL_FW_VERSION,
                            new StringType(version.get("FW").getAsString()));
                }
                if (version.has("WS")) {
                    updateState(KseniaBindingConstants.CHANNEL_PANEL_WS_VERSION,
                            new StringType(version.get("WS").getAsString()));
                }
            }

            updateStatus(ThingStatus.ONLINE);

            sendRegisterCommand();
        } else {
            logger.info("Login failed: {} - {}", result, detail);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Login failed");
        }
    }

    private void sendLogoutCommand() {
        String receiver = "";
        int id = 65535;
        Map<String, String> payload = Map.of(Lares40Command.COMMAND_PAYLOAD_IDLOGIN, loginId);
        String message = Lares40CommandUtils.buildCommand(senderGuid, receiver, Lares40Command.COMMAND_LOGOUT, id,
                loginType, payload);
        sendMessage(message);
    }

    private void handleLogoutResponse(Lares40Command command) {
        String payloadType = command.payloadType != null ? command.payloadType : "";

        if (command.payload == null || !command.payload.isJsonObject()) {
            logger.debug("Invalid payload");
            return;
        }
        JsonObject payload = command.payload.getAsJsonObject();
        String result = payload.has(Lares40Command.COMMAND_PAYLOAD_RESULT)
                ? payload.get(Lares40Command.COMMAND_PAYLOAD_RESULT).getAsString()
                : "";
        String detail = payload.has(Lares40Command.COMMAND_PAYLOAD_RESULTDETAIL)
                ? payload.get(Lares40Command.COMMAND_PAYLOAD_RESULTDETAIL).getAsString()
                : "";
        String loginId = payload.has(Lares40Command.COMMAND_PAYLOAD_IDLOGIN)
                ? payload.get(Lares40Command.COMMAND_PAYLOAD_IDLOGIN).getAsString()
                : "";

        if (result.equalsIgnoreCase(Lares40Command.COMMAND_PAYLOAD_RESULTOK)) {
            logger.info("Logout successful for {} [{}]", payloadType, loginId);
            this.loginType = "";
            this.loginId = "";
            updateStatus(ThingStatus.OFFLINE);
        } else {
            logger.info("Logout failed: {} - {}", result, detail);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Logout failed");
        }
    }

    private void sendRegisterCommand() {
        String receiver = "";
        int id = 65535;
        // Map<String, String> payload = Map.of(Lares40Command.COMMAND_PAYLOAD_IDLOGIN, loginId);

        JsonObject payload = new JsonObject();
        payload.addProperty(Lares40Command.COMMAND_PAYLOAD_IDLOGIN, loginId);
        JsonArray types = new JsonArray();
        types.add("STATUS_ALL");
        payload.add(Lares40Command.COMMAND_PAYLOAD_TYPES, types);

        String message = Lares40CommandUtils.buildCommand(senderGuid, receiver, Lares40Command.COMMAND_REALTIME, id,
                Lares40Command.COMMAND_TYPE_REGISTER, payload);
        sendMessage(message);
    }

    private void handleRegisterResponse(Lares40Command command) {
    }

    private void handleRealtimeChanges(Lares40Command command) {
        if (command.payload == null || !command.payload.isJsonObject()) {
            logger.debug("Invalid payload");
            return;
        }
        JsonObject payload = command.payload.getAsJsonObject();

        String receiver = senderGuid;
        if (!payload.has(senderGuid)) {
            logger.debug("Payload does not contain expected receiver key: {}", receiver);
            return;
        }
        JsonObject receiverData = payload.getAsJsonObject(receiver);

        if (receiverData.has(Lares40Command.COMMAND_PAYLOAD_STATUSPARTITIONS)) {
            JsonArray partitions = receiverData.getAsJsonArray(Lares40Command.COMMAND_PAYLOAD_STATUSPARTITIONS);
            for (JsonElement element : partitions) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject partitionData = element.getAsJsonObject();
                if (!partitionData.has(Lares40Command.COMMAND_PAYLOAD_STATUSPARTITIONSID)) {
                    continue;
                }

                String idStr = partitionData.get(Lares40Command.COMMAND_PAYLOAD_STATUSPARTITIONSID).getAsString();
                try {
                    int partitionId = Integer.parseInt(idStr);
                    KseniaPartitionHandler handler = partitionHandlers.get(partitionId);
                    if (handler != null) {
                        handler.updateFromRealtime(partitionData);
                    } else {
                        logger.debug("No handler registered for partition {}", partitionId);
                    }
                } catch (NumberFormatException e) {
                    logger.error("Invalid {} format: {}", Lares40Command.COMMAND_PAYLOAD_STATUSPARTITIONSID, idStr);
                }
            }
        }

        if (receiverData.has(Lares40Command.COMMAND_PAYLOAD_STATUSZONES)) {
            JsonArray zones = receiverData.getAsJsonArray(Lares40Command.COMMAND_PAYLOAD_STATUSZONES);
            for (JsonElement element : zones) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject zoneData = element.getAsJsonObject();
                if (!zoneData.has(Lares40Command.COMMAND_PAYLOAD_STATUSZONESID)) {
                    continue;
                }

                String idStr = zoneData.get(Lares40Command.COMMAND_PAYLOAD_STATUSZONESID).getAsString();
                try {
                    int zoneId = Integer.parseInt(idStr);
                    KseniaZoneHandler handler = zoneHandlers.get(zoneId);
                    if (handler != null) {
                        handler.updateFromRealtime(zoneData);
                    } else {
                        logger.debug("No handler registered for zone {}", zoneId);
                    }
                } catch (NumberFormatException e) {
                    logger.error("Invalid {} format: {}", Lares40Command.COMMAND_PAYLOAD_STATUSZONESID, idStr);
                }
            }
        }
    }
}
