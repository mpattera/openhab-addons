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
import org.openhab.binding.kseniasecurity.internal.state.PartitionArmingState;
import org.openhab.binding.kseniasecurity.internal.state.PartitionEventState;
import org.openhab.binding.kseniasecurity.internal.state.PartitionMetadataField;
import org.openhab.binding.kseniasecurity.internal.state.PartitionMetadataListener;
import org.openhab.binding.kseniasecurity.internal.state.PartitionMetadataSnapshot;
import org.openhab.binding.kseniasecurity.internal.state.PartitionSnapshot;
import org.openhab.binding.kseniasecurity.internal.state.PartitionStateListener;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps state snapshots for a partition to openHAB channels.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public class KseniaPartitionHandler extends BaseThingHandler
        implements PartitionMetadataListener, PartitionStateListener {
    private final Logger logger = LoggerFactory.getLogger(KseniaPartitionHandler.class);
    private final Object snapshotLock = new Object();

    private int partitionId;
    private boolean disposed = true;
    private @Nullable KseniaPanelSubscriptions panelHandler;
    private @Nullable PartitionSnapshot stateSnapshot;
    private @Nullable PartitionMetadataSnapshot metadataSnapshot;

    public KseniaPartitionHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        synchronized (snapshotLock) {
            disposed = false;
            partitionId = 0;
            stateSnapshot = null;
            metadataSnapshot = null;
            // A changed entity ID must not leave channel values belonging to the previous entity.
            publishState(null, null);
            publishMetadata(null, null);
            logger.debug("Initializing the handler for {}", getThing().getUID());
            KseniaPartitionConfiguration configuration = getConfigAs(KseniaPartitionConfiguration.class);
            if (!configuration.isValid()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid configuration");
                return;
            }

            partitionId = configuration.id;
            logger.debug("Initializing partition {}", partitionId);
            updateAvailability();
        }
        // The framework can reinitialize this handler after any Thing edit without notifying the bridge.
        // Subscribe here, outside snapshotLock: replay and live delivery acquire the dispatcher lock first.
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof KseniaPanelSubscriptions handler) {
            panelHandler = handler;
            handler.addPartitionHandler(partitionId, this);
        }
    }

    @Override
    public void dispose() {
        synchronized (snapshotLock) {
            disposed = true;
            stateSnapshot = null;
            metadataSnapshot = null;
        }
        KseniaPanelSubscriptions handler = panelHandler;
        panelHandler = null;
        if (handler != null) {
            handler.removePartitionHandler(partitionId, this);
        }
        super.dispose();
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        synchronized (snapshotLock) {
            // Framework notifications are asynchronous: use the current bridge, not a possibly stale event.
            updateAvailability();
        }
    }

    /** Called with snapshotLock held; only a fresh entity snapshot can make this Thing online. */
    private void updateAvailability() {
        if (!isConfigured()) {
            return;
        }
        Bridge bridge = getBridge();
        if (bridge == null || bridge.getStatus() == ThingStatus.OFFLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        } else {
            PartitionSnapshot snapshot = stateSnapshot;
            updateStatus(bridge.getStatus() == ThingStatus.ONLINE && snapshot != null && !snapshot.values().isEmpty()
                    ? ThingStatus.ONLINE
                    : ThingStatus.UNKNOWN);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (RefreshType.REFRESH.equals(command)) {
            refreshChannel(channelUID);
            return;
        }
        logger.debug("Ignoring a command for read-only partition channel {}", channelUID);
    }

    @Override
    public void onPartitionStateUpdated(PartitionSnapshot state) {
        synchronized (snapshotLock) {
            if (!isConfigured() || state.id() != partitionId) {
                return;
            }
            stateSnapshot = state;
            publishState(state, null);
            updateAvailability();
        }
    }

    private boolean isConfigured() {
        return !disposed && partitionId > 0;
    }

    @Override
    public void onPartitionMetadataUpdated(PartitionMetadataSnapshot metadata) {
        synchronized (snapshotLock) {
            if (isConfigured() && metadata.id() == partitionId) {
                metadataSnapshot = metadata;
                publishMetadata(metadata, null);
            }
        }
    }

    @Override
    public void onPartitionMetadataInvalidated() {
        synchronized (snapshotLock) {
            if (!isConfigured()) {
                return;
            }
            metadataSnapshot = null;
            publishMetadata(null, null);
        }
    }

    @Override
    public void onPartitionStateInvalidated() {
        synchronized (snapshotLock) {
            if (!isConfigured()) {
                return;
            }
            stateSnapshot = null;
            publishState(null, null);
            updateAvailability();
        }
    }

    private void refreshChannel(ChannelUID channelUID) {
        String channelId = channelUID.getId();
        synchronized (snapshotLock) {
            if (!isConfigured()) {
                return;
            }
            boolean stateRefreshed = stateSnapshot != null && publishState(stateSnapshot, channelId);
            boolean metadataRefreshed = metadataSnapshot != null && publishMetadata(metadataSnapshot, channelId);
            if (stateRefreshed || metadataRefreshed) {
                logger.trace("Refreshing cached partition {} channel {}", partitionId, channelUID);
            } else {
                logger.debug("No cached partition data is available to refresh channel {}", channelUID);
            }
        }
    }

    private boolean publishMetadata(@Nullable PartitionMetadataSnapshot metadata, @Nullable String requestedChannelId) {
        if (!isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_PARTITION_DESCRIPTION)) {
            return false;
        }
        updateTextState(KseniaBindingConstants.CHANNEL_PARTITION_DESCRIPTION,
                metadata == null ? null : metadata.get(PartitionMetadataField.DESCRIPTION));
        return true;
    }

    private boolean publishState(@Nullable PartitionSnapshot state, @Nullable String requestedChannelId) {
        @Nullable
        PartitionArmingState armingState = state == null ? null : state.armingState();
        @Nullable
        PartitionEventState alarmState = state == null ? null : state.alarmState();
        @Nullable
        PartitionEventState tamperState = state == null ? null : state.tamperState();
        @Nullable
        Integer delayRemainingSeconds = state == null ? null : state.delayRemainingSeconds();
        boolean statePublished = false;

        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_PARTITION_REALTIME_STATE)) {
            updateEnumState(KseniaBindingConstants.CHANNEL_PARTITION_REALTIME_STATE, armingState);
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_PARTITION_ARM_STATUS)) {
            updateSwitchState(KseniaBindingConstants.CHANNEL_PARTITION_ARM_STATUS, isArmedOrArming(armingState));
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_PARTITION_ENTRY_DELAY_STATUS)) {
            updateSwitchState(KseniaBindingConstants.CHANNEL_PARTITION_ENTRY_DELAY_STATUS,
                    isArmingState(armingState, PartitionArmingState.ENTRY_DELAY));
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_PARTITION_EXIT_DELAY_STATUS)) {
            updateSwitchState(KseniaBindingConstants.CHANNEL_PARTITION_EXIT_DELAY_STATUS,
                    isArmingState(armingState, PartitionArmingState.EXIT_DELAY));
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_PARTITION_DELAY_REMAINING)) {
            updateDelayState(isDelayActive(armingState) ? delayRemainingSeconds : null);
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_PARTITION_ALARM_STATE)) {
            updateEnumState(KseniaBindingConstants.CHANNEL_PARTITION_ALARM_STATE, alarmState);
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_PARTITION_ALARM_ACTIVE)) {
            updateSwitchState(KseniaBindingConstants.CHANNEL_PARTITION_ALARM_ACTIVE,
                    isEventState(alarmState, PartitionEventState.ACTIVE));
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_PARTITION_ALARM_MEMORY)) {
            updateSwitchState(KseniaBindingConstants.CHANNEL_PARTITION_ALARM_MEMORY,
                    isEventState(alarmState, PartitionEventState.MEMORY));
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_PARTITION_TAMPER_STATE)) {
            updateEnumState(KseniaBindingConstants.CHANNEL_PARTITION_TAMPER_STATE, tamperState);
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_PARTITION_TAMPER_ACTIVE)) {
            updateSwitchState(KseniaBindingConstants.CHANNEL_PARTITION_TAMPER_ACTIVE,
                    isEventState(tamperState, PartitionEventState.ACTIVE));
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_PARTITION_TAMPER_MEMORY)) {
            updateSwitchState(KseniaBindingConstants.CHANNEL_PARTITION_TAMPER_MEMORY,
                    isEventState(tamperState, PartitionEventState.MEMORY));
            statePublished = true;
        }
        return statePublished;
    }

    private static boolean isRequestedChannel(@Nullable String requestedChannelId, String channelId) {
        return requestedChannelId == null || channelId.equals(requestedChannelId);
    }

    private @Nullable Boolean isArmedOrArming(@Nullable PartitionArmingState armingState) {
        if (armingState == null || armingState == PartitionArmingState.UNKNOWN) {
            return null;
        }
        return armingState != PartitionArmingState.DISARMED;
    }

    private @Nullable Boolean isArmingState(@Nullable PartitionArmingState armingState,
            PartitionArmingState expectedState) {
        if (armingState == null || armingState == PartitionArmingState.UNKNOWN) {
            return null;
        }
        return armingState == expectedState;
    }

    private boolean isDelayActive(@Nullable PartitionArmingState armingState) {
        return armingState == PartitionArmingState.ENTRY_DELAY || armingState == PartitionArmingState.EXIT_DELAY;
    }

    private @Nullable Boolean isEventState(@Nullable PartitionEventState eventState,
            PartitionEventState expectedState) {
        if (eventState == null || eventState == PartitionEventState.UNKNOWN) {
            return null;
        }
        return eventState == expectedState;
    }

    private void updateTextState(String channelId, @Nullable String value) {
        updateState(channelId, value == null ? UnDefType.UNDEF : new StringType(value));
    }

    private void updateEnumState(String channelId, @Nullable Enum<?> value) {
        updateTextState(channelId, value == null ? null : value.name());
    }

    private void updateSwitchState(String channelId, @Nullable Boolean value) {
        updateState(channelId, value == null ? UnDefType.UNDEF : value ? OnOffType.ON : OnOffType.OFF);
    }

    private void updateDelayState(@Nullable Integer seconds) {
        updateState(KseniaBindingConstants.CHANNEL_PARTITION_DELAY_REMAINING,
                seconds == null ? UnDefType.UNDEF : new QuantityType<>(seconds, Units.SECOND));
    }
}
