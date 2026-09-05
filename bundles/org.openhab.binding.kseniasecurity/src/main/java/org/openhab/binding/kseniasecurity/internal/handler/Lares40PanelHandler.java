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

import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.kseniasecurity.internal.KseniaBindingConstants;
import org.openhab.binding.kseniasecurity.internal.KseniaTlsTrustManagerProvider;
import org.openhab.binding.kseniasecurity.internal.KseniaWebSocketListener;
import org.openhab.binding.kseniasecurity.internal.KseniaWebSocketManager;
import org.openhab.binding.kseniasecurity.internal.protocol.lares40.Lares40Command;
import org.openhab.binding.kseniasecurity.internal.protocol.lares40.Lares40CommandCodec;
import org.openhab.binding.kseniasecurity.internal.protocol.lares40.Lares40EntityTraceFormatter;
import org.openhab.binding.kseniasecurity.internal.protocol.lares40.Lares40InventoryDecoder;
import org.openhab.binding.kseniasecurity.internal.protocol.lares40.Lares40PendingReadRequest;
import org.openhab.binding.kseniasecurity.internal.protocol.lares40.Lares40ProtocolConstants;
import org.openhab.binding.kseniasecurity.internal.protocol.lares40.Lares40ReadDiagnostics;
import org.openhab.binding.kseniasecurity.internal.protocol.lares40.Lares40ReadOperation;
import org.openhab.binding.kseniasecurity.internal.protocol.lares40.Lares40ReadRequest;
import org.openhab.binding.kseniasecurity.internal.protocol.lares40.Lares40ReadRequestManager;
import org.openhab.binding.kseniasecurity.internal.protocol.lares40.Lares40RealtimeChangesDecoder;
import org.openhab.binding.kseniasecurity.internal.state.KseniaPanelInventory;
import org.openhab.binding.kseniasecurity.internal.state.KseniaPanelStateDispatcher;
import org.openhab.binding.kseniasecurity.internal.state.PanelInventoryUpdates;
import org.openhab.binding.kseniasecurity.internal.state.PanelStateUpdates;
import org.openhab.binding.kseniasecurity.internal.state.PartitionStateUpdate;
import org.openhab.binding.kseniasecurity.internal.state.ZoneStateUpdate;
import org.openhab.core.io.net.http.TlsTrustManagerProvider;
import org.openhab.core.io.net.http.WebSocketFactory;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.util.ThingWebClientUtil;
import org.openhab.core.types.Command;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Handles one Lares 4.0 panel bridge.
 *
 * <p>
 * This is the sole owner of the panel transport and protocol adapter. Child Thing handlers receive only typed,
 * protocol-independent state snapshots from the dispatcher.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public class Lares40PanelHandler extends BaseBridgeHandler
        implements KseniaWebSocketListener, KseniaPanelSubscriptions {

    private static final int PANEL_COMMAND_ID = 65535;

    private final Logger logger = LoggerFactory.getLogger(Lares40PanelHandler.class);
    private final Object connectionLock = new Object();
    // Lifecycle, transport callbacks, commands and timers enter this lock before accessing session state.
    // Protocol-only helpers assume that this lock is already held.
    private final Object sessionLock = new Object();
    private final WebSocketFactory webSocketFactory;
    private final BundleContext bundleContext;
    private final String senderGuid = UUID.randomUUID().toString();
    private final Lares40CommandCodec commandCodec = new Lares40CommandCodec();
    private final Lares40InventoryDecoder inventoryDecoder = new Lares40InventoryDecoder();
    private final Lares40ReadRequestManager readRequestManager;
    private final Lares40RealtimeChangesDecoder realtimeChangesDecoder = new Lares40RealtimeChangesDecoder();
    private final KseniaPanelInventory panelInventory;
    private final KseniaPanelStateDispatcher stateDispatcher = new KseniaPanelStateDispatcher();
    private final Deque<QueuedRead> queuedReadRequests = new ArrayDeque<>();
    private final List<PanelStateUpdates> pendingRealtimeUpdates = new ArrayList<>();
    private final Map<KseniaWebSocketManager, PendingLogout> closingSessions = new HashMap<>();

    private volatile boolean reconnectEnabled;
    private volatile @Nullable Lares40PanelSettings settings;
    private volatile @Nullable KseniaWebSocketManager webSocketManager;
    private @Nullable ScheduledFuture<?> retryFuture;
    private @Nullable ScheduledFuture<?> readResponseTimeout;
    private @Nullable ScheduledFuture<?> startupResponseTimeout;
    private @Nullable PendingStartupRequest pendingStartupRequest;
    private long startupRequestSequence;
    private @Nullable ServiceRegistration<?> tlsTrustManagerRegistration;

    private String loginId = "";
    private SessionPhase sessionPhase = SessionPhase.CLOSED;

    /**
     * Creates the bridge handler.
     *
     * @param bridge the panel bridge
     * @param webSocketFactory the openHAB WebSocket client factory
     * @param bundleContext the OSGi context used to register endpoint-specific TLS trust
     */
    public Lares40PanelHandler(Bridge bridge, WebSocketFactory webSocketFactory, BundleContext bundleContext) {
        this(bridge, webSocketFactory, bundleContext, new Lares40ReadRequestManager(), new KseniaPanelInventory());
    }

    Lares40PanelHandler(Bridge bridge, WebSocketFactory webSocketFactory, BundleContext bundleContext,
            Lares40ReadRequestManager readRequestManager) {
        this(bridge, webSocketFactory, bundleContext, readRequestManager, new KseniaPanelInventory());
    }

    Lares40PanelHandler(Bridge bridge, WebSocketFactory webSocketFactory, BundleContext bundleContext,
            Lares40ReadRequestManager readRequestManager, KseniaPanelInventory panelInventory) {
        super(bridge);
        this.webSocketFactory = webSocketFactory;
        this.bundleContext = bundleContext;
        this.readRequestManager = readRequestManager;
        this.panelInventory = panelInventory;
    }

    @Override
    public void initialize() {
        synchronized (sessionLock) {
            logger.debug("Panel {} initializing handler", getThing().getUID());
            reconnectEnabled = false;
            stopTransport(false);
            invalidateRealtimeSession();

            reconnectEnabled = true;
            updateStatus(ThingStatus.UNKNOWN);
            scheduler.execute(this::initializeConnection);
        }
    }

    @Override
    public void dispose() {
        synchronized (sessionLock) {
            reconnectEnabled = false;
            stopTransport(true);
            // A bridge configuration update reinitializes this handler without disposing its children.
            // Children own their subscriptions; only session data is discarded here.
            invalidateRealtimeSession();
        }
        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (KseniaBindingConstants.CHANNEL_PANEL_DIAGNOSTIC_READ.equals(channelUID.getId())) {
            handleDiagnosticReadCommand(command);
            return;
        }
        logger.debug("Panel {} ignored a command for read-only channel {}", getThing().getUID(), channelUID);
    }

    @Override
    public void connectionEstablished(KseniaWebSocketManager manager) {
        synchronized (sessionLock) {
            if (!isCurrentWebSocketManager(manager)) {
                logger.debug("Panel {} ignored a WebSocket connection event from an inactive transport",
                        getThing().getUID());
                return;
            }

            logger.debug("Panel {} WebSocket connection established; starting panel login", getThing().getUID());
            invalidateRealtimeSession();
            loginId = "";
            sendLoginCommand();
        }
    }

    @Override
    public void connectionClosed(KseniaWebSocketManager manager, int statusCode, @Nullable String reason) {
        synchronized (sessionLock) {
            if (closingSessions.containsKey(manager)) {
                finishLogout(manager, "WebSocket closed during logout");
                return;
            }
            if (!isCurrentWebSocketManager(manager)) {
                logger.debug("Panel {} ignored a WebSocket close event from an inactive transport",
                        getThing().getUID());
                return;
            }

            handleUnexpectedConnectionClosed(statusCode, reason);
        }
    }

    void handleUnexpectedConnectionClosed(int statusCode, @Nullable String reason) {
        logger.debug("Panel {} WebSocket connection closed: statusCode={}, reason={}", getThing().getUID(), statusCode,
                reason);
        handleConnectionFailure("Panel WebSocket connection closed (status " + statusCode + ")");
    }

    @Override
    public void connectionError(KseniaWebSocketManager manager, @Nullable Throwable cause) {
        synchronized (sessionLock) {
            if (closingSessions.containsKey(manager)) {
                logger.debug("Panel {} WebSocket failed during logout", getThing().getUID(), cause);
                finishLogout(manager, "WebSocket error during logout");
                return;
            }
            if (!isCurrentWebSocketManager(manager)) {
                logger.debug("Panel {} ignored a WebSocket error event from an inactive transport",
                        getThing().getUID());
                return;
            }

            if (cause == null) {
                logger.debug("Panel {} WebSocket connection error without a reported cause", getThing().getUID());
            } else {
                logger.debug("Panel {} WebSocket connection error: {}: {}", getThing().getUID(),
                        cause.getClass().getName(), cause.getMessage());
            }
            handleConnectionFailure("Panel WebSocket connection error");
        }
    }

    @Override
    public void messageReceived(KseniaWebSocketManager manager, String message) {
        synchronized (sessionLock) {
            PendingLogout logout = closingSessions.get(manager);
            if (logout != null) {
                commandCodec.decode(message).ifPresent(command -> {
                    logPanelCommand("received", command);
                    handleClosingResponse(manager, logout, command);
                });
                return;
            }
            if (!isCurrentWebSocketManager(manager)) {
                logger.debug("Panel {} ignored a message from an inactive WebSocket transport", getThing().getUID());
                return;
            }

            handlePanelMessage(message);
        }
    }

    @Override
    public void messageSent(KseniaWebSocketManager manager, String message) {
        // A successful write is still worth logging if its completion races with retirement of the session.
        // This callback is diagnostic only: it must never update protocol state or Thing status.
        if (logger.isTraceEnabled()) {
            commandCodec.decode(message).ifPresent(command -> logPanelCommand("sent", command));
        }
    }

    /** Handles a validated text message from the active panel transport. */
    void handlePanelMessage(String message) {
        Optional<Lares40Command> optionalCommand = commandCodec.decode(message);
        if (optionalCommand.isEmpty()) {
            logger.debug("Panel {} ignored an invalid protocol message", getThing().getUID());
            return;
        }

        Lares40Command command = optionalCommand.get();
        @Nullable
        String commandName = command.command;
        if (commandName == null) {
            logger.debug("Panel {} ignored a protocol message without a command name", getThing().getUID());
            return;
        }
        logPanelCommand("received", command);

        // Serialize protocol responses with read timeouts and session invalidation. Transport sends only enqueue
        // asynchronous WebSocket writes, so the next startup phase can be started within this same critical section.
        synchronized (sessionLock) {
            switch (commandName) {
                case Lares40ProtocolConstants.COMMAND_LOGIN_RESPONSE:
                    handleLoginResponse(command);
                    break;
                case Lares40ProtocolConstants.COMMAND_LOGOUT_RESPONSE:
                    logger.debug("Panel {} ignored LOGOUT_RES without a pending logout", getThing().getUID());
                    break;
                case Lares40ProtocolConstants.COMMAND_GENERIC:
                    handleGenericResponse(command);
                    break;
                case Lares40ProtocolConstants.COMMAND_READ_RESPONSE:
                    handleReadResponse(command);
                    break;
                case Lares40ProtocolConstants.COMMAND_REALTIME_RESPONSE:
                    handleRealtimeResponse(command);
                    break;
                case Lares40ProtocolConstants.COMMAND_REALTIME:
                    handleRealtimeNotification(command);
                    break;
                default:
                    logger.debug("Panel {} ignored unsupported protocol command {}", getThing().getUID(), commandName);
                    break;
            }
        }
    }

    /**
     * Logs one actual transmitted or received protocol frame, never a separately reconstructed request.
     *
     * <p>
     * Entity values are useful while comparing newer panel firmware against the published SDK. The fields to redact
     * are explicitly defined by {@link Lares40ReadDiagnostics} and currently include only the PIN.
     *
     * @param direction whether the frame was sent or received
     * @param command decoded wire frame
     */
    private void logPanelCommand(String direction, Lares40Command command) {
        if (!logger.isTraceEnabled()) {
            return;
        }

        String entityFields = Lares40ProtocolConstants.COMMAND_REALTIME.equals(command.command)
                && Lares40ProtocolConstants.PAYLOAD_TYPE_CHANGES.equals(command.payloadType)
                        ? Lares40EntityTraceFormatter.formatChanges(command.payload, senderGuid)
                        : Lares40EntityTraceFormatter.formatPayload(command.payload);
        logger.trace("Panel {} {} command={}, id={}, payloadType={}, payload={}, entityFields={}", getThing().getUID(),
                direction, command.command, command.id, command.payloadType,
                Lares40ReadDiagnostics.summarize(command.payload), entityFields);
    }

    @Override
    public void addZoneHandler(int zoneId, KseniaZoneHandler handler) {
        logger.debug("Panel {} registering zone Thing {} for zone {}", getThing().getUID(), handler.getThing().getUID(),
                zoneId);
        panelInventory.addZoneListener(zoneId, handler);
        stateDispatcher.addZoneListener(zoneId, handler);
    }

    @Override
    public void removeZoneHandler(int zoneId, KseniaZoneHandler handler) {
        logger.debug("Panel {} unregistering zone Thing {} for zone {}", getThing().getUID(),
                handler.getThing().getUID(), zoneId);
        panelInventory.removeZoneListener(zoneId, handler);
        stateDispatcher.removeZoneListener(zoneId, handler);
    }

    @Override
    public void addPartitionHandler(int partitionId, KseniaPartitionHandler handler) {
        logger.debug("Panel {} registering partition Thing {} for partition {}", getThing().getUID(),
                handler.getThing().getUID(), partitionId);
        panelInventory.addPartitionListener(partitionId, handler);
        stateDispatcher.addPartitionListener(partitionId, handler);
    }

    @Override
    public void removePartitionHandler(int partitionId, KseniaPartitionHandler handler) {
        logger.debug("Panel {} unregistering partition Thing {} for partition {}", getThing().getUID(),
                handler.getThing().getUID(), partitionId);
        panelInventory.removePartitionListener(partitionId, handler);
        stateDispatcher.removePartitionListener(partitionId, handler);
    }

    private void initializeConnection() {
        if (!reconnectEnabled) {
            return;
        }

        Lares40PanelConfiguration configuration = getConfigAs(Lares40PanelConfiguration.class);
        @Nullable
        Lares40PanelSettings panelSettings = configuration.toSettings();
        if (panelSettings == null) {
            reconnectEnabled = false;
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Configuration is invalid");
            return;
        }
        settings = panelSettings;

        if (panelSettings.requiresSelfSignedCertificateTrust() && !registerSelfSignedCertificateTrust(panelSettings)) {
            scheduleRetry(this::initializeConnection);
            return;
        }

        if (!reconnectEnabled) {
            return;
        }

        createWebSocketTransport();
    }

    /**
     * Creates and starts the bridge-owned transport for the current panel configuration.
     *
     * <p>
     * A failed transport is never reused: the next retry receives a distinct callback source, so a late Jetty event
     * from the previous session cannot alter the state of the new one.
     */
    private void createWebSocketTransport() {
        if (!reconnectEnabled) {
            return;
        }

        try {
            String consumerName = ThingWebClientUtil.buildWebClientConsumerName(thing.getUID(), null);
            WebSocketClient webSocketClient = webSocketFactory.createWebSocketClient(consumerName);
            webSocketManager = new KseniaWebSocketManager(webSocketClient, this, getThing().getUID().toString());
            connect();
        } catch (RuntimeException e) {
            logger.debug("Panel {} could not create its WebSocket client", getThing().getUID(), e);
            unregisterTlsTrustManager();
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "WebSocket client creation failed");
            scheduleRetry(this::initializeConnection);
        }
    }

    private boolean registerSelfSignedCertificateTrust(Lares40PanelSettings panelSettings) {
        try {
            KseniaTlsTrustManagerProvider trustManagerProvider = new KseniaTlsTrustManagerProvider(panelSettings.host(),
                    panelSettings.port());
            ServiceRegistration<TlsTrustManagerProvider> registration = bundleContext
                    .registerService(TlsTrustManagerProvider.class, trustManagerProvider, null);
            synchronized (connectionLock) {
                if (!reconnectEnabled) {
                    registration.unregister();
                    return false;
                }
                tlsTrustManagerRegistration = registration;
            }
            return true;
        } catch (MalformedURLException e) {
            reconnectEnabled = false;
            logger.debug("Panel {} could not create its secure endpoint", getThing().getUID(), e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "The configured panel host cannot be used for a secure connection");
            return false;
        } catch (CertificateException e) {
            logger.debug("Panel {} could not establish trust for its self-signed certificate", getThing().getUID(), e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    describeSelfSignedCertificateTrustFailure(e));
            return false;
        } catch (IllegalStateException e) {
            logger.debug("Panel {} could not register trust for its self-signed certificate", getThing().getUID(), e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Could not initialize the secure connection to the panel");
            return false;
        }
    }

    /**
     * Converts a certificate bootstrap failure into a user-facing connection description.
     *
     * <p>
     * {@link org.openhab.core.io.net.http.PEMTrustManager} wraps failures while contacting the endpoint in a
     * {@link CertificateException}, so the causal chain must be inspected before reporting a certificate problem.
     *
     * @param failure the certificate bootstrap failure
     * @return a concise, user-facing description of the actual failure category
     */
    static String describeSelfSignedCertificateTrustFailure(Throwable failure) {
        if (hasCause(failure, UnknownHostException.class)) {
            return "Panel host cannot be resolved";
        }
        if (hasCause(failure, SocketTimeoutException.class)) {
            return "Connection to the panel timed out";
        }
        if (hasCause(failure, ConnectException.class) || hasCause(failure, NoRouteToHostException.class)) {
            return "Panel is unreachable";
        }
        if (hasCause(failure, SSLException.class)) {
            return "Secure connection to the panel failed";
        }
        return "Could not retrieve the panel certificate";
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> causeType) {
        @Nullable
        Throwable current = failure;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void connect() {
        @Nullable
        Lares40PanelSettings panelSettings = settings;
        @Nullable
        KseniaWebSocketManager manager = webSocketManager;
        if (!reconnectEnabled || panelSettings == null || manager == null) {
            return;
        }

        try {
            manager.connect(panelSettings.getWebSocketUri());
        } catch (URISyntaxException e) {
            reconnectEnabled = false;
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "The configured panel host cannot be used in a WebSocket URI");
        }
    }

    private void handleConnectionFailure(String description) {
        logger.debug("Panel {}: {}; ending the current panel session", getThing().getUID(), description);
        synchronized (sessionLock) {
            if (reconnectEnabled) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, description);
            }
            invalidateRealtimeSession();
        }
        scheduleRetry(this::restartWebSocketTransport);
    }

    /** Replaces a failed transport while retaining the already validated panel configuration and TLS trust. */
    private void restartWebSocketTransport() {
        if (!reconnectEnabled) {
            return;
        }

        disconnectWebSocketTransport();
        createWebSocketTransport();
    }

    private void scheduleRetry(Runnable retryOperation) {
        @Nullable
        Lares40PanelSettings panelSettings = settings;
        if (!reconnectEnabled || panelSettings == null) {
            return;
        }

        synchronized (connectionLock) {
            @Nullable
            ScheduledFuture<?> existingRetry = retryFuture;
            if (existingRetry != null && !existingRetry.isDone()) {
                return;
            }
            retryFuture = scheduleProtocolTask(() -> {
                synchronized (connectionLock) {
                    retryFuture = null;
                }
                if (reconnectEnabled) {
                    retryOperation.run();
                }
            }, panelSettings.reconnectInterval());
            logger.debug("Panel {} scheduled a connection retry in {} seconds", getThing().getUID(),
                    panelSettings.reconnectInterval());
        }
    }

    /** Called with sessionLock held by initialize() or dispose(). */
    private void stopTransport(boolean graceful) {
        cancelRetry();
        KseniaWebSocketManager manager = webSocketManager;
        Lares40PanelSettings panelSettings = settings;
        boolean authenticated = sessionPhase != SessionPhase.CLOSED && !loginId.isBlank();
        boolean allowLegacyGeneric = pendingStartupRequest == null && readRequestManager.getPendingRequest().isEmpty();
        int logoutId = graceful && authenticated && manager != null ? readRequestManager.nextCommandId() : 0;
        String previousLoginId = loginId;
        webSocketManager = null;
        closeReadSessionLocked();
        pendingRealtimeUpdates.clear();
        settings = null;
        loginId = "";
        if (manager != null) {
            if (graceful && authenticated && panelSettings != null && manager.isConnected()) {
                beginLogout(manager, logoutId, previousLoginId, panelSettings, allowLegacyGeneric);
            } else {
                executeTransportCleanup(manager::disconnect);
            }
        }
        unregisterTlsTrustManager();
    }

    private void beginLogout(KseniaWebSocketManager manager, int commandId, String previousLoginId,
            Lares40PanelSettings panelSettings, boolean allowLegacyGeneric) {
        String payloadType = loginPayloadType(panelSettings.loginType());
        Optional<String> message = commandCodec.encode(senderGuid, "", Lares40ProtocolConstants.COMMAND_LOGOUT,
                commandId, payloadType, Map.of(Lares40ProtocolConstants.PAYLOAD_LOGIN_ID, previousLoginId));
        if (message.isEmpty()) {
            executeTransportCleanup(manager::disconnect);
            return;
        }
        PendingLogout logout = new PendingLogout(commandId, payloadType, previousLoginId, allowLegacyGeneric);
        closingSessions.put(manager, logout);
        logout.timeout = scheduleProtocolTask(() -> finishLogout(manager, "LOGOUT response timed out"),
                panelSettings.responseTimeout());
        logger.debug("Panel {} sending LOGOUT before closing its WebSocket session", getThing().getUID());
        manager.sendMessage(message.get());
    }

    private void handleClosingResponse(KseniaWebSocketManager manager, PendingLogout logout, Lares40Command command) {
        boolean matchingId = Integer.toString(logout.commandId).equals(command.id);
        boolean logoutResponse = matchingId && Lares40ProtocolConstants.COMMAND_LOGOUT_RESPONSE.equals(command.command)
                && logout.payloadType.equals(command.payloadType);
        boolean genericResponse = Lares40ProtocolConstants.COMMAND_GENERIC.equals(command.command)
                && (matchingId || (logout.allowLegacyGeneric && isLegacyGenericResponse(command)));
        if (!logoutResponse && !genericResponse) {
            return;
        }
        JsonObject payload = getPayloadObject(command);
        String responseLoginId = payload == null ? ""
                : valueOrEmpty(getText(payload, Lares40ProtocolConstants.PAYLOAD_LOGIN_ID));
        if (logoutResponse && !responseLoginId.isBlank() && !logout.loginId.equals(responseLoginId)) {
            logger.debug("Panel {} ignored LOGOUT_RES for another login", getThing().getUID());
            return;
        }
        String result = payload == null ? "" : valueOrEmpty(getText(payload, Lares40ProtocolConstants.PAYLOAD_RESULT));
        String detail = payload == null ? ""
                : valueOrEmpty(getText(payload, Lares40ProtocolConstants.PAYLOAD_RESULT_DETAIL));
        logger.debug("Panel {} logout response: command={}, id={}, result={}, detail={}", getThing().getUID(),
                command.command, command.id, result, detail);
        finishLogout(manager, "Logout exchange completed");
    }

    private void finishLogout(KseniaWebSocketManager manager, String reason) {
        synchronized (sessionLock) {
            PendingLogout logout = closingSessions.remove(manager);
            if (logout == null) {
                return;
            }
            ScheduledFuture<?> timeout = logout.timeout;
            if (timeout != null) {
                timeout.cancel(false);
            }
        }
        logger.debug("Panel {} closing retired WebSocket session: {}", getThing().getUID(), reason);
        // Jetty must not be stopped on its own callback thread, and dispose() must remain non-blocking.
        executeTransportCleanup(manager::disconnect);
    }

    /** Runs transport shutdown outside both the framework disposal call and Jetty callbacks. */
    void executeTransportCleanup(Runnable task) {
        scheduler.execute(task);
    }

    private static String loginPayloadType(KseniaLoginType loginType) {
        return switch (loginType) {
            case USER -> Lares40ProtocolConstants.PAYLOAD_TYPE_USER;
            case SUPERVISOR -> Lares40ProtocolConstants.PAYLOAD_TYPE_SUPERVISOR;
        };
    }

    /** Detaches and stops the current transport before a lifecycle operation can create a replacement. */
    private void disconnectWebSocketTransport() {
        @Nullable
        KseniaWebSocketManager manager = webSocketManager;
        // Detach first. A callback delivered while Jetty stops the old client must be ignored by the source guard.
        webSocketManager = null;
        if (manager != null) {
            manager.disconnect();
        }
    }

    private void cancelRetry() {
        @Nullable
        ScheduledFuture<?> retry;
        synchronized (connectionLock) {
            retry = retryFuture;
            retryFuture = null;
        }
        if (retry != null) {
            retry.cancel(false);
        }
    }

    private void unregisterTlsTrustManager() {
        @Nullable
        ServiceRegistration<?> registration;
        synchronized (connectionLock) {
            registration = tlsTrustManagerRegistration;
            tlsTrustManagerRegistration = null;
        }
        if (registration != null) {
            try {
                registration.unregister();
            } catch (IllegalStateException e) {
                logger.trace("Panel {} TLS trust manager was already unregistered", getThing().getUID(), e);
            }
        }
    }

    private void sendLoginCommand() {
        @Nullable
        Lares40PanelSettings panelSettings = settings;
        if (panelSettings == null) {
            return;
        }

        String payloadType = loginPayloadType(panelSettings.loginType());
        sessionPhase = SessionPhase.LOGIN;
        beginStartupRequest(Lares40ProtocolConstants.COMMAND_LOGIN, Lares40ProtocolConstants.COMMAND_LOGIN_RESPONSE,
                payloadType);
        logger.debug("Panel {} sending LOGIN using {} (payloadType={})", getThing().getUID(), panelSettings.loginType(),
                payloadType);
        Map<String, String> payload = Map.of(Lares40ProtocolConstants.PAYLOAD_PIN, panelSettings.pin());
        sendEncodedCommand(commandCodec.encode(senderGuid, "", Lares40ProtocolConstants.COMMAND_LOGIN, PANEL_COMMAND_ID,
                payloadType, payload));
    }

    private void sendRegisterCommand() {
        sessionPhase = SessionPhase.REALTIME_REGISTRATION;
        pendingRealtimeUpdates.clear();
        beginStartupRequest(Lares40ProtocolConstants.PAYLOAD_TYPE_REGISTER,
                Lares40ProtocolConstants.COMMAND_REALTIME_RESPONSE,
                Lares40ProtocolConstants.PAYLOAD_TYPE_REGISTER_ACKNOWLEDGEMENT);

        JsonObject payload = new JsonObject();
        payload.addProperty(Lares40ProtocolConstants.PAYLOAD_LOGIN_ID, loginId);
        JsonArray types = new JsonArray();
        types.add(Lares40ProtocolConstants.PAYLOAD_STATUS_ZONES);
        types.add(Lares40ProtocolConstants.PAYLOAD_STATUS_PARTITIONS);
        payload.add(Lares40ProtocolConstants.PAYLOAD_TYPES, types);

        logger.debug("Panel {} sending REALTIME REGISTER for types {}", getThing().getUID(), types);
        sendEncodedCommand(commandCodec.encode(senderGuid, "", Lares40ProtocolConstants.COMMAND_REALTIME,
                PANEL_COMMAND_ID, Lares40ProtocolConstants.PAYLOAD_TYPE_REGISTER, payload));
    }

    private void handleDiagnosticReadCommand(Command command) {
        if (!(command instanceof StringType stringCommand)) {
            logger.debug("Panel {} ignored a diagnostic READ command with unsupported type {}", getThing().getUID(),
                    command.getClass().getName());
            return;
        }

        Optional<Lares40ReadRequest> request = Lares40ReadRequest.forDiagnosticCommand(stringCommand.toString());
        if (request.isEmpty()) {
            logger.debug("Panel {} ignored a malformed diagnostic READ; expected PAYLOAD_TYPE [rangeStart rangeEnd]",
                    getThing().getUID());
            return;
        }
        queueRead(Lares40ReadOperation.DIAGNOSTIC, request.get());
    }

    /** Reads a fresh inventory after login, before starting the real-time registration. */
    private void beginReadSession() {
        sessionPhase = SessionPhase.INITIAL_INVENTORY;
        queuedReadRequests.addFirst(
                new QueuedRead(Lares40ReadOperation.INITIAL_INVENTORY, Lares40ReadRequest.initialInventory()));
        logger.debug("Panel {} starting initial inventory READ before REALTIME REGISTER", getThing().getUID());
        dispatchNextRead();
    }

    private void queueRead(Lares40ReadOperation operation, Lares40ReadRequest request) {
        synchronized (sessionLock) {
            if (sessionPhase == SessionPhase.CLOSED || sessionPhase == SessionPhase.LOGIN) {
                logger.debug("Panel {} cannot queue {} READ {} because its login is not active", getThing().getUID(),
                        operation.logName(), request.payloadType());
                return;
            }
            queuedReadRequests.addLast(new QueuedRead(operation, request));
            logger.debug("Panel {} queued {} READ {}", getThing().getUID(), operation.logName(), request.payloadType());
            dispatchNextRead();
        }
    }

    private void dispatchNextRead() {
        if ((sessionPhase != SessionPhase.INITIAL_INVENTORY && sessionPhase != SessionPhase.ACTIVE)
                || readRequestManager.getPendingRequest().isPresent() || queuedReadRequests.isEmpty()) {
            return;
        }

        // During startup only the inventory read may run. Diagnostics wait until REGISTER_ACK, so a legacy
        // GENERIC error cannot refer to both a READ and a REGISTER command in flight.
        if (sessionPhase == SessionPhase.INITIAL_INVENTORY && Objects.requireNonNull(queuedReadRequests.peekFirst())
                .operation() != Lares40ReadOperation.INITIAL_INVENTORY) {
            return;
        }

        QueuedRead queuedRequest = Objects.requireNonNull(queuedReadRequests.pollFirst());
        String activeLoginId = loginId;
        if (activeLoginId.isBlank()) {
            logger.debug("Panel {} cannot send {} READ {} because its login is no longer active", getThing().getUID(),
                    queuedRequest.operation().logName(), queuedRequest.request().payloadType());
            return;
        }

        Optional<Lares40PendingReadRequest> newPendingRequest = readRequestManager.begin(queuedRequest.operation(),
                queuedRequest.request());
        if (newPendingRequest.isEmpty()) {
            queuedReadRequests.addFirst(queuedRequest);
            return;
        }
        Lares40PendingReadRequest pendingRequest = newPendingRequest.get();

        JsonObject payload = pendingRequest.request().createPayload(activeLoginId);
        Optional<String> command = commandCodec.encode(senderGuid, "", Lares40ProtocolConstants.COMMAND_READ,
                pendingRequest.commandId(), pendingRequest.request().payloadType(), payload);
        if (command.isEmpty()) {
            readRequestManager.cancel(pendingRequest);
            logger.debug("Panel {} could not encode {} READ {}", getThing().getUID(),
                    pendingRequest.operation().logName(), pendingRequest.request().payloadType());
            completeRead(pendingRequest);
            return;
        }

        readResponseTimeout = scheduleProtocolTask(() -> handleReadTimeout(pendingRequest),
                Objects.requireNonNull(settings).responseTimeout());
        logger.debug("Panel {} sending {} READ {} with request ID {}", getThing().getUID(),
                pendingRequest.operation().logName(), pendingRequest.request().payloadType(),
                pendingRequest.commandId());
        sendMessage(command.get());
    }

    private void sendEncodedCommand(Optional<String> encodedCommand) {
        if (encodedCommand.isPresent()) {
            sendMessage(encodedCommand.get());
        } else {
            handleConnectionFailure("Could not encode the panel startup command");
        }
    }

    /** Schedules a protocol timeout or reconnect using the handler-owned scheduler. */
    ScheduledFuture<?> scheduleProtocolTask(Runnable task, int delaySeconds) {
        return scheduler.schedule(task, delaySeconds, TimeUnit.SECONDS);
    }

    private void beginStartupRequest(String name, String responseCommand, String responsePayloadType) {
        clearStartupRequest();
        PendingStartupRequest request = new PendingStartupRequest(++startupRequestSequence, name, responseCommand,
                responsePayloadType);
        pendingStartupRequest = request;
        startupResponseTimeout = scheduleProtocolTask(() -> {
            synchronized (sessionLock) {
                // The local sequence distinguishes attempts that reuse the same command and wire ID after reconnect.
                if (request.equals(pendingStartupRequest)) {
                    handleConnectionFailure("Panel " + request.name() + " response timed out");
                }
            }
        }, Objects.requireNonNull(settings).responseTimeout());
    }

    private boolean isExpectedStartupResponse(Lares40Command command) {
        @Nullable
        PendingStartupRequest request = pendingStartupRequest;
        return request != null && Integer.toString(PANEL_COMMAND_ID).equals(command.id)
                && request.responseCommand().equals(command.command)
                && request.responsePayloadType().equals(command.payloadType);
    }

    private void clearStartupRequest() {
        pendingStartupRequest = null;
        @Nullable
        ScheduledFuture<?> timeout = startupResponseTimeout;
        startupResponseTimeout = null;
        if (timeout != null) {
            timeout.cancel(false);
        }
    }

    void sendMessage(String message) {
        @Nullable
        KseniaWebSocketManager manager = webSocketManager;
        if (manager != null) {
            manager.sendMessage(message);
        }
    }

    /**
     * Verifies that a transport callback still belongs to this active bridge connection.
     *
     * <p>
     * Jetty can deliver a late callback while a bridge is being reconfigured or disposed. The handler owns exactly
     * one active manager; messages and lifecycle events from an older manager must not affect its current state.
     *
     * @param manager the callback source
     * @return {@code true} when the manager is still active for this bridge
     */
    private boolean isCurrentWebSocketManager(KseniaWebSocketManager manager) {
        return reconnectEnabled && Objects.equals(manager, webSocketManager);
    }

    private void handleLoginResponse(Lares40Command command) {
        if (sessionPhase != SessionPhase.LOGIN || !isExpectedStartupResponse(command)) {
            logger.debug("Panel {} ignored unexpected LOGIN_RES: id={}, payloadType={}, phase={}", getThing().getUID(),
                    command.id, command.payloadType, sessionPhase);
            return;
        }
        @Nullable
        JsonObject payload = getPayloadObject(command);
        if (payload == null) {
            handleConnectionFailure("Panel LOGIN_RES did not contain an object payload");
            return;
        }

        clearStartupRequest();
        @Nullable
        String result = getText(payload, Lares40ProtocolConstants.PAYLOAD_RESULT);
        if (result == null || result.isBlank()) {
            handleConnectionFailure("Panel LOGIN_RES did not contain a result");
            return;
        }
        if (!Lares40ProtocolConstants.PAYLOAD_RESULT_OK.equalsIgnoreCase(result)) {
            logger.debug("Panel {} LOGIN_RES was rejected: result={}", getThing().getUID(), result);
            reconnectEnabled = false;
            cancelRetry();
            invalidateRealtimeSession();
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Panel login was rejected");
            return;
        }

        loginId = valueOrEmpty(getText(payload, Lares40ProtocolConstants.PAYLOAD_LOGIN_ID));
        if (loginId.isBlank()) {
            handleConnectionFailure("Panel login response did not contain a login identifier");
            return;
        }
        logger.debug("Panel {} LOGIN_RES accepted using {}; starting initial inventory READ", getThing().getUID(),
                command.payloadType);

        updatePanelState(payload, Lares40ProtocolConstants.PAYLOAD_SYSTEM_LANGUAGE,
                KseniaBindingConstants.CHANNEL_PANEL_SYSTEM_LANG);
        updatePanelState(payload, Lares40ProtocolConstants.PAYLOAD_SESSION_STATE,
                KseniaBindingConstants.CHANNEL_PANEL_SESSION_STATE);
        updatePanelState(payload, Lares40ProtocolConstants.PAYLOAD_FREEZE_STATE,
                KseniaBindingConstants.CHANNEL_PANEL_FREEZE_STATE);

        @Nullable
        JsonObject version = getObject(payload, Lares40ProtocolConstants.PAYLOAD_VERSION);
        if (version != null) {
            updatePanelState(version, Lares40ProtocolConstants.PAYLOAD_VERSION_FW,
                    KseniaBindingConstants.CHANNEL_PANEL_FW_VERSION);
            updatePanelState(version, Lares40ProtocolConstants.PAYLOAD_VERSION_WS,
                    KseniaBindingConstants.CHANNEL_PANEL_WS_VERSION);
        }

        beginReadSession();
    }

    private void handleReadResponse(Lares40Command command) {
        @Nullable
        Integer commandId = getReadCommandId(command);
        if (commandId == null) {
            logger.debug("Panel {} ignored READ_RES without a valid command ID", getThing().getUID());
            return;
        }

        Optional<Lares40PendingReadRequest> matchingRequest = takePendingReadRequest(commandId.intValue());
        if (matchingRequest.isEmpty()) {
            logger.debug("Panel {} ignored unexpected READ_RES with command ID {}", getThing().getUID(), commandId);
            return;
        }
        Lares40PendingReadRequest pendingRequest = matchingRequest.get();

        @Nullable
        String responsePayloadType = command.payloadType;
        if (!pendingRequest.request().payloadType().equals(responsePayloadType)) {
            logger.debug("Panel {} {} READ expected response type {} but received {}", getThing().getUID(),
                    pendingRequest.operation().logName(), pendingRequest.request().payloadType(), responsePayloadType);
        }

        switch (pendingRequest.operation()) {
            case DIAGNOSTIC:
                logDiagnosticReadResponse(pendingRequest, responsePayloadType);
                break;
            case INITIAL_INVENTORY:
                handleInitialInventoryResponse(pendingRequest, command, responsePayloadType);
                break;
        }
        completeRead(pendingRequest);
    }

    /** Advances startup after any terminal inventory result, including errors, without blocking state monitoring. */
    private void completeRead(Lares40PendingReadRequest request) {
        if (request.operation() == Lares40ReadOperation.INITIAL_INVENTORY
                && sessionPhase == SessionPhase.INITIAL_INVENTORY) {
            logger.debug("Panel {} initial inventory READ completed; inventoryAvailable={}; starting REALTIME REGISTER",
                    getThing().getUID(), panelInventory.isComplete());
            sendRegisterCommand();
        } else {
            dispatchNextRead();
        }
    }

    private void logDiagnosticReadResponse(Lares40PendingReadRequest pendingRequest,
            @Nullable String responsePayloadType) {
        logger.debug("Panel {} received {} diagnostic READ response: requestId={}, responseType={}, range=[{}, {}]",
                getThing().getUID(), pendingRequest.request().payloadType(), pendingRequest.commandId(),
                responsePayloadType, pendingRequest.request().rangeStart(), pendingRequest.request().rangeEnd());
    }

    private void handleInitialInventoryResponse(Lares40PendingReadRequest pendingRequest, Lares40Command command,
            @Nullable String responsePayloadType) {
        if (!pendingRequest.request().payloadType().equals(responsePayloadType)) {
            logger.debug("Panel {} ignored initial inventory READ_RES with unexpected payload type {}",
                    getThing().getUID(), responsePayloadType);
            return;
        }

        @Nullable
        JsonObject payload = getPayloadObject(command);
        if (payload == null) {
            logger.debug("Panel {} ignored initial inventory READ_RES without an object payload", getThing().getUID());
            return;
        }
        String result = valueOrEmpty(getText(payload, Lares40ProtocolConstants.PAYLOAD_RESULT));
        if (!Lares40ProtocolConstants.PAYLOAD_RESULT_OK.equalsIgnoreCase(result)) {
            logger.debug("Panel {} initial inventory READ was not accepted: result={}, detail={}", getThing().getUID(),
                    result, valueOrEmpty(getText(payload, Lares40ProtocolConstants.PAYLOAD_RESULT_DETAIL)));
            return;
        }

        PanelInventoryUpdates updates = inventoryDecoder.decode(command.payload);
        if (!updates.zonesIncluded() || !updates.partitionsIncluded()) {
            logger.debug("Panel {} initial inventory READ_RES did not contain both zones and partitions",
                    getThing().getUID());
            return;
        }

        panelInventory.apply(updates);
        logger.debug("Panel {} applied initial inventory: {} zones and {} partitions", getThing().getUID(),
                updates.zones().size(), updates.partitions().size());
    }

    /**
     * Handles a protocol-level error response.
     *
     * <p>
     * The protocol documentation shows the legacy form with command identifier {@code 0}; newer firmware may instead
     * echo the request identifier, which is handled first. LOGIN and REGISTER errors are matched to the pending startup
     * request before considering READ requests. These operations never overlap, so the legacy form can be associated
     * with the sole request in flight. Concurrent commands will require a different policy for uncorrelated errors.
     *
     * @param command the decoded generic response
     */
    private void handleGenericResponse(Lares40Command command) {
        @Nullable
        PendingStartupRequest startupRequest = pendingStartupRequest;
        if (startupRequest != null
                && (Integer.toString(PANEL_COMMAND_ID).equals(command.id) || isLegacyGenericResponse(command))) {
            @Nullable
            JsonObject payload = getPayloadObject(command);
            String result = payload == null ? "<missing>"
                    : valueOrEmpty(getText(payload, Lares40ProtocolConstants.PAYLOAD_RESULT));
            String detail = payload == null ? ""
                    : valueOrEmpty(getText(payload, Lares40ProtocolConstants.PAYLOAD_RESULT_DETAIL));
            logger.debug("Panel {} {} was rejected by GENERIC: id={}, result={}, detail={}", getThing().getUID(),
                    startupRequest.name(), command.id, result, detail);
            handleConnectionFailure("Panel " + startupRequest.name() + " failed"
                    + (detail.isBlank() ? " (GENERIC error)" : " (" + detail + ")"));
            return;
        }
        @Nullable
        Integer commandId = getReadCommandId(command);
        if (commandId != null) {
            Optional<Lares40PendingReadRequest> matchingRequest = takePendingReadRequest(commandId.intValue());
            if (matchingRequest.isPresent()) {
                completeReadWithError(matchingRequest.get(), command);
            } else {
                logger.debug("Panel {} received GENERIC with unexpected command ID {}", getThing().getUID(), commandId);
            }
            return;
        }

        if (!isLegacyGenericResponse(command)) {
            logger.debug("Panel {} received GENERIC without a matching pending request: id={}, phase={}",
                    getThing().getUID(), command.id, sessionPhase);
            return;
        }

        Optional<Lares40PendingReadRequest> pendingRequest = takePendingReadRequestForLegacyGenericResponse();
        if (pendingRequest.isEmpty()) {
            logger.debug("Panel {} received legacy GENERIC without a pending request", getThing().getUID());
            return;
        }

        completeReadWithError(pendingRequest.get(), command);
    }

    private void completeReadWithError(Lares40PendingReadRequest pendingRequest, Lares40Command command) {
        @Nullable
        JsonObject payload = getPayloadObject(command);
        String result = payload == null ? "<missing>"
                : valueOrEmpty(getText(payload, Lares40ProtocolConstants.PAYLOAD_RESULT));
        String detail = payload == null ? "<missing>"
                : valueOrEmpty(getText(payload, Lares40ProtocolConstants.PAYLOAD_RESULT_DETAIL));
        logger.debug("Panel {} {} READ {} was rejected: responseId={}, responseType={}, result={}, detail={}",
                getThing().getUID(), pendingRequest.operation().logName(), pendingRequest.request().payloadType(),
                command.id, command.payloadType, result, detail);
        completeRead(pendingRequest);
    }

    private void handleRealtimeResponse(Lares40Command command) {
        @Nullable
        String payloadType = command.payloadType;
        if (!Lares40ProtocolConstants.PAYLOAD_TYPE_REGISTER_ACKNOWLEDGEMENT.equals(payloadType)) {
            logger.debug("Panel {} ignored unsupported REALTIME_RES payload type {}", getThing().getUID(), payloadType);
            return;
        }

        if (sessionPhase != SessionPhase.REALTIME_REGISTRATION || !isExpectedStartupResponse(command)) {
            logger.debug("Panel {} ignored unexpected REALTIME_RES REGISTER_ACK in phase {}", getThing().getUID(),
                    sessionPhase);
            return;
        }

        @Nullable
        JsonObject payload = getPayloadObject(command);
        if (payload == null) {
            handleConnectionFailure("Panel REGISTER_ACK did not contain an object payload");
            return;
        }

        clearStartupRequest();
        String result = valueOrEmpty(getText(payload, Lares40ProtocolConstants.PAYLOAD_RESULT));
        if (!Lares40ProtocolConstants.PAYLOAD_RESULT_OK.equalsIgnoreCase(result)) {
            logger.debug("Panel {} REALTIME REGISTER was rejected: result={}, detail={}", getThing().getUID(), result,
                    valueOrEmpty(getText(payload, Lares40ProtocolConstants.PAYLOAD_RESULT_DETAIL)));
            handleConnectionFailure("Panel real-time status registration was rejected");
            return;
        }

        PanelStateUpdates initialUpdates = realtimeChangesDecoder.decodeRegisterAcknowledgement(command);
        applyStateUpdates(initialUpdates);
        for (PanelStateUpdates pendingUpdates : pendingRealtimeUpdates) {
            applyStateUpdates(pendingUpdates);
        }
        pendingRealtimeUpdates.clear();
        sessionPhase = SessionPhase.ACTIVE;
        logger.debug(
                "Panel {} submitted REALTIME REGISTER_ACK state to child Things: {} zone and {} partition " + "updates",
                getThing().getUID(), initialUpdates.zoneUpdates().size(), initialUpdates.partitionUpdates().size());
        updateStatus(ThingStatus.ONLINE);
        logger.info("Panel {} is online; real-time status registration is active", getThing().getUID());
        dispatchNextRead();
    }

    private void handleRealtimeNotification(Lares40Command command) {
        @Nullable
        String payloadType = command.payloadType;
        if (!Lares40ProtocolConstants.PAYLOAD_TYPE_CHANGES.equals(payloadType)) {
            logger.debug("Panel {} ignored unsupported REALTIME notification payload type {}", getThing().getUID(),
                    payloadType);
            return;
        }

        PanelStateUpdates updates = realtimeChangesDecoder.decodeChanges(command, senderGuid);
        if (updates.isEmpty()) {
            logger.debug("Panel {} received REALTIME CHANGES without usable state updates", getThing().getUID());
            return;
        }

        if (sessionPhase == SessionPhase.REALTIME_REGISTRATION) {
            pendingRealtimeUpdates.add(updates);
            logger.debug(
                    "Panel {} queued REALTIME CHANGES received before REGISTER_ACK: {} zone and {} partition updates",
                    getThing().getUID(), updates.zoneUpdates().size(), updates.partitionUpdates().size());
            return;
        }
        if (sessionPhase != SessionPhase.ACTIVE) {
            logger.debug("Panel {} ignored REALTIME CHANGES outside an active registration", getThing().getUID());
            return;
        }
        applyStateUpdates(updates);
        logger.debug("Panel {} applied REALTIME CHANGES: {} zone and {} partition updates", getThing().getUID(),
                updates.zoneUpdates().size(), updates.partitionUpdates().size());
    }

    private void invalidateRealtimeSession() {
        boolean wasActive = sessionPhase != SessionPhase.CLOSED;
        pendingRealtimeUpdates.clear();
        closeReadSessionLocked();
        stateDispatcher.clearSnapshots();
        panelInventory.clearSnapshots();
        if (wasActive) {
            logger.debug("Panel {} session ended; cached state and inventory were invalidated", getThing().getUID());
        }
    }

    void handleReadTimeout(Lares40PendingReadRequest request) {
        synchronized (sessionLock) {
            if (clearPendingReadRequest(request)) {
                logger.debug("Panel {} {} READ {} with command ID {} timed out", getThing().getUID(),
                        request.operation().logName(), request.request().payloadType(), request.commandId());
                completeRead(request);
            }
        }
    }

    private Optional<Lares40PendingReadRequest> takePendingReadRequest(int commandId) {
        Optional<Lares40PendingReadRequest> request = readRequestManager.take(commandId);
        if (request.isPresent()) {
            cancelReadResponseTimeout();
        }
        return request;
    }

    private Optional<Lares40PendingReadRequest> takePendingReadRequestForLegacyGenericResponse() {
        Optional<Lares40PendingReadRequest> request = readRequestManager.takePendingRequest();
        if (request.isPresent()) {
            cancelReadResponseTimeout();
        }
        return request;
    }

    private void closeReadSessionLocked() {
        sessionPhase = SessionPhase.CLOSED;
        clearStartupRequest();
        queuedReadRequests.clear();
        readRequestManager.clear();
        cancelReadResponseTimeout();
    }

    private boolean clearPendingReadRequest(Lares40PendingReadRequest expectedRequest) {
        if (!readRequestManager.cancel(expectedRequest)) {
            return false;
        }
        cancelReadResponseTimeout();
        return true;
    }

    private void cancelReadResponseTimeout() {
        @Nullable
        ScheduledFuture<?> timeout = readResponseTimeout;
        readResponseTimeout = null;
        if (timeout != null) {
            timeout.cancel(false);
        }
    }

    private void applyStateUpdates(PanelStateUpdates updates) {
        for (PartitionStateUpdate update : updates.partitionUpdates()) {
            stateDispatcher.updatePartition(update);
        }
        for (ZoneStateUpdate update : updates.zoneUpdates()) {
            stateDispatcher.updateZone(update);
        }
    }

    private void updatePanelState(JsonObject payload, String payloadKey, String channelId) {
        @Nullable
        String value = getText(payload, payloadKey);
        if (value != null) {
            updateState(channelId, new StringType(value));
        }
    }

    private static @Nullable JsonObject getPayloadObject(Lares40Command command) {
        @Nullable
        JsonElement payload = command.payload;
        return payload != null && payload.isJsonObject() ? payload.getAsJsonObject() : null;
    }

    private static @Nullable JsonObject getObject(JsonObject object, String key) {
        @Nullable
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static @Nullable String getText(JsonObject object, String key) {
        @Nullable
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }

        try {
            return element.getAsString();
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

    private static String valueOrEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static @Nullable Integer getReadCommandId(Lares40Command command) {
        @Nullable
        String commandId = command.id;
        if (commandId == null) {
            return null;
        }

        try {
            int value = Integer.parseInt(commandId);
            return value > 0 && value < PANEL_COMMAND_ID ? Integer.valueOf(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isLegacyGenericResponse(Lares40Command command) {
        return "0".equals(command.id);
    }

    private record QueuedRead(Lares40ReadOperation operation, Lares40ReadRequest request) {
    }

    /** A retired session may finish its logout, but can no longer update Things or trigger retries. */
    private static final class PendingLogout {
        private final int commandId;
        private final String payloadType;
        private final String loginId;
        private final boolean allowLegacyGeneric;
        private @Nullable ScheduledFuture<?> timeout;

        private PendingLogout(int commandId, String payloadType, String loginId, boolean allowLegacyGeneric) {
            this.commandId = commandId;
            this.payloadType = payloadType;
            this.loginId = loginId;
            this.allowLegacyGeneric = allowLegacyGeneric;
        }
    }

    private record PendingStartupRequest(long sequence, String name, String responseCommand,
            String responsePayloadType) {
    }

    private enum SessionPhase {
        CLOSED,
        LOGIN,
        INITIAL_INVENTORY,
        REALTIME_REGISTRATION,
        ACTIVE
    }
}
