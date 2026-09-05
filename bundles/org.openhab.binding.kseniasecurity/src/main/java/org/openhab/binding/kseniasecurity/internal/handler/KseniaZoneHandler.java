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
import org.openhab.binding.kseniasecurity.internal.state.ZoneBypassMode;
import org.openhab.binding.kseniasecurity.internal.state.ZoneCondition;
import org.openhab.binding.kseniasecurity.internal.state.ZoneCycleState;
import org.openhab.binding.kseniasecurity.internal.state.ZoneFaultState;
import org.openhab.binding.kseniasecurity.internal.state.ZoneMetadataField;
import org.openhab.binding.kseniasecurity.internal.state.ZoneMetadataListener;
import org.openhab.binding.kseniasecurity.internal.state.ZoneMetadataSnapshot;
import org.openhab.binding.kseniasecurity.internal.state.ZoneSnapshot;
import org.openhab.binding.kseniasecurity.internal.state.ZoneStateListener;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
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
 * Maps state snapshots for a zone to openHAB channels.
 *
 * @author Michele Pattera - Initial contribution
 */
@NonNullByDefault
public class KseniaZoneHandler extends BaseThingHandler implements ZoneMetadataListener, ZoneStateListener {

    private final Logger logger = LoggerFactory.getLogger(KseniaZoneHandler.class);
    private final Object snapshotLock = new Object();

    private int zoneId;
    private boolean disposed = true;
    private @Nullable KseniaPanelSubscriptions panelHandler;
    private @Nullable ZoneSnapshot stateSnapshot;
    private @Nullable ZoneMetadataSnapshot metadataSnapshot;

    public KseniaZoneHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        synchronized (snapshotLock) {
            disposed = false;
            zoneId = 0;
            stateSnapshot = null;
            metadataSnapshot = null;
            // A changed entity ID must not leave channel values belonging to the previous entity.
            publishState(null, null);
            publishMetadata(null, null);
            logger.debug("Initializing the handler for {}", getThing().getUID());
            KseniaZoneConfiguration configuration = getConfigAs(KseniaZoneConfiguration.class);
            if (!configuration.isValid()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid configuration");
                return;
            }

            zoneId = configuration.id;
            logger.debug("Initializing zone {}", zoneId);
            updateAvailability();
        }
        // The framework can reinitialize this handler after any Thing edit without notifying the bridge.
        // Subscribe here, outside snapshotLock: replay and live delivery acquire the dispatcher lock first.
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof KseniaPanelSubscriptions handler) {
            panelHandler = handler;
            handler.addZoneHandler(zoneId, this);
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
            handler.removeZoneHandler(zoneId, this);
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
            ZoneSnapshot snapshot = stateSnapshot;
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
        logger.debug("Ignoring a command for read-only zone channel {}", channelUID);
    }

    @Override
    public void onZoneStateUpdated(ZoneSnapshot state) {
        synchronized (snapshotLock) {
            if (!isConfigured() || state.id() != zoneId) {
                return;
            }
            stateSnapshot = state;
            publishState(state, null);
            updateAvailability();
        }
    }

    private boolean isConfigured() {
        return !disposed && zoneId > 0;
    }

    @Override
    public void onZoneMetadataUpdated(ZoneMetadataSnapshot metadata) {
        synchronized (snapshotLock) {
            if (isConfigured() && metadata.id() == zoneId) {
                metadataSnapshot = metadata;
                publishMetadata(metadata, null);
            }
        }
    }

    @Override
    public void onZoneMetadataInvalidated() {
        synchronized (snapshotLock) {
            if (!isConfigured()) {
                return;
            }
            metadataSnapshot = null;
            publishMetadata(null, null);
        }
    }

    @Override
    public void onZoneStateInvalidated() {
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
                logger.trace("Refreshing cached zone {} channel {}", zoneId, channelUID);
            } else {
                logger.debug("No cached zone data is available to refresh channel {}", channelUID);
            }
        }
    }

    private boolean publishMetadata(@Nullable ZoneMetadataSnapshot metadata, @Nullable String requestedChannelId) {
        if (!isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_ZONE_DESCRIPTION)) {
            return false;
        }
        updateTextState(KseniaBindingConstants.CHANNEL_ZONE_DESCRIPTION,
                metadata == null ? null : metadata.get(ZoneMetadataField.DESCRIPTION));
        return true;
    }

    private boolean publishState(@Nullable ZoneSnapshot state, @Nullable String requestedChannelId) {
        @Nullable
        ZoneCondition condition = state == null ? null : state.condition();
        @Nullable
        ZoneBypassMode bypassMode = state == null ? null : state.bypassMode();
        @Nullable
        ZoneCycleState alarmCycleState = state == null ? null : state.alarmCycleState();
        @Nullable
        ZoneCycleState tamperCycleState = state == null ? null : state.tamperCycleState();
        @Nullable
        ZoneFaultState faultState = state == null ? null : state.faultState();
        boolean statePublished = false;

        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_ZONE_REALTIME_STATE)) {
            updateEnumState(KseniaBindingConstants.CHANNEL_ZONE_REALTIME_STATE, condition);
            statePublished = true;
        }
        // STA reports the present condition of the input, regardless of an alarm event generated by the panel.
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_ZONE_REST_STATUS)) {
            updateSwitchState(KseniaBindingConstants.CHANNEL_ZONE_REST_STATUS,
                    isCurrentCondition(condition, ZoneCondition.REST));
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_ZONE_ALARM_STATUS)) {
            updateSwitchState(KseniaBindingConstants.CHANNEL_ZONE_ALARM_STATUS,
                    isCurrentCondition(condition, ZoneCondition.ALARM));
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_ZONE_FAULT_STATUS)) {
            updateSwitchState(KseniaBindingConstants.CHANNEL_ZONE_FAULT_STATUS,
                    isCurrentCondition(condition, ZoneCondition.FAULT));
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_ZONE_TAMPER_STATUS)) {
            updateSwitchState(KseniaBindingConstants.CHANNEL_ZONE_TAMPER_STATUS,
                    isCurrentCondition(condition, ZoneCondition.TAMPER));
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_ZONE_ERROR_STATUS)) {
            updateSwitchState(KseniaBindingConstants.CHANNEL_ZONE_ERROR_STATUS,
                    isCurrentCondition(condition, ZoneCondition.ERROR));
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_ZONE_BYPASS_STATE)) {
            updateEnumState(KseniaBindingConstants.CHANNEL_ZONE_BYPASS_STATE, bypassMode);
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_ZONE_BYPASS_STATUS)) {
            updateSwitchState(KseniaBindingConstants.CHANNEL_ZONE_BYPASS_STATUS, isBypassed(bypassMode));
            statePublished = true;
        }
        // A and T report the alarm and tamper events decided by the panel, separately from STA.
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_ZONE_ALARM_STATE)) {
            updateEnumState(KseniaBindingConstants.CHANNEL_ZONE_ALARM_STATE, alarmCycleState);
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_ZONE_ALARM_ACTIVE)) {
            updateSwitchState(KseniaBindingConstants.CHANNEL_ZONE_ALARM_ACTIVE,
                    isCycleState(alarmCycleState, ZoneCycleState.ACTIVE));
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_ZONE_ALARM_MEMORY)) {
            updateSwitchState(KseniaBindingConstants.CHANNEL_ZONE_ALARM_MEMORY,
                    isCycleState(alarmCycleState, ZoneCycleState.MEMORY));
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_ZONE_TAMPER_STATE)) {
            updateEnumState(KseniaBindingConstants.CHANNEL_ZONE_TAMPER_STATE, tamperCycleState);
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_ZONE_TAMPER_ACTIVE)) {
            updateSwitchState(KseniaBindingConstants.CHANNEL_ZONE_TAMPER_ACTIVE,
                    isCycleState(tamperCycleState, ZoneCycleState.ACTIVE));
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_ZONE_TAMPER_MEMORY)) {
            updateSwitchState(KseniaBindingConstants.CHANNEL_ZONE_TAMPER_MEMORY,
                    isCycleState(tamperCycleState, ZoneCycleState.MEMORY));
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_ZONE_FAULT_STATE)) {
            updateEnumState(KseniaBindingConstants.CHANNEL_ZONE_FAULT_STATE, faultState);
            statePublished = true;
        }
        if (isRequestedChannel(requestedChannelId, KseniaBindingConstants.CHANNEL_ZONE_FAULT_MEMORY)) {
            updateSwitchState(KseniaBindingConstants.CHANNEL_ZONE_FAULT_MEMORY, isFaultMemory(faultState));
            statePublished = true;
        }
        return statePublished;
    }

    private static boolean isRequestedChannel(@Nullable String requestedChannelId, String channelId) {
        return requestedChannelId == null || channelId.equals(requestedChannelId);
    }

    private @Nullable Boolean isCurrentCondition(@Nullable ZoneCondition condition, ZoneCondition expectedCondition) {
        if (condition == null || condition == ZoneCondition.UNKNOWN) {
            return null;
        }
        return condition == expectedCondition;
    }

    private @Nullable Boolean isBypassed(@Nullable ZoneBypassMode bypassMode) {
        if (bypassMode == null || bypassMode == ZoneBypassMode.UNKNOWN) {
            return null;
        }
        return bypassMode != ZoneBypassMode.NOT_BYPASSED;
    }

    private @Nullable Boolean isCycleState(@Nullable ZoneCycleState cycleState, ZoneCycleState expectedState) {
        if (cycleState == null || cycleState == ZoneCycleState.UNKNOWN) {
            return null;
        }
        return cycleState == expectedState;
    }

    private @Nullable Boolean isFaultMemory(@Nullable ZoneFaultState faultState) {
        if (faultState == null || faultState == ZoneFaultState.UNKNOWN) {
            return null;
        }
        return faultState == ZoneFaultState.ACTIVE;
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
}
