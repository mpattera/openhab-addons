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

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.kseniasecurity.internal.handler.KseniaPartitionHandler;
import org.openhab.binding.kseniasecurity.internal.handler.KseniaZoneHandler;
import org.openhab.binding.kseniasecurity.internal.handler.Lares40PanelHandler;
import org.openhab.core.io.net.http.WebSocketFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link KseniaHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Michele Pattera - Initial contribution
 */
@Component(service = ThingHandlerFactory.class, configurationPid = KseniaBindingConstants.CONFIGURATION_PID)
@NonNullByDefault
public class KseniaHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(KseniaBindingConstants.THING_TYPE_PANEL,
            KseniaBindingConstants.THING_TYPE_PARTITION, KseniaBindingConstants.THING_TYPE_ZONE);

    private final WebSocketFactory webSocketFactory;
    private final BundleContext bundleContext;

    @Activate
    public KseniaHandlerFactory(@Reference WebSocketFactory webSocketFactory, BundleContext bundleContext) {
        this.webSocketFactory = webSocketFactory;
        this.bundleContext = bundleContext;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();
        if (thingTypeUID.equals(KseniaBindingConstants.THING_TYPE_PANEL) && thing instanceof Bridge bridge) {
            return new Lares40PanelHandler(bridge, webSocketFactory, bundleContext);
        }
        if (thingTypeUID.equals(KseniaBindingConstants.THING_TYPE_PARTITION)) {
            return new KseniaPartitionHandler(thing);
        }
        if (thingTypeUID.equals(KseniaBindingConstants.THING_TYPE_ZONE)) {
            return new KseniaZoneHandler(thing);
        }
        return null;
    }
}
