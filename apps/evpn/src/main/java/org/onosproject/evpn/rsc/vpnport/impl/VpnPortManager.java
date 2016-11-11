/*
 * Copyright 2016-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.evpn.rsc.vpnport.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.util.KryoNamespace;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.evpn.rsc.DefaultVpnPort;
import org.onosproject.evpn.rsc.EtcdResponse;
import org.onosproject.evpn.rsc.VpnInstanceId;
import org.onosproject.evpn.rsc.VpnPort;
import org.onosproject.evpn.rsc.VpnPortId;
import org.onosproject.evpn.rsc.vpnport.VpnPortEvent;
import org.onosproject.evpn.rsc.vpnport.VpnPortListener;
import org.onosproject.evpn.rsc.vpnport.VpnPortService;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.service.WallClockTimestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

/**
 * Provides implementation of the VpnPort APIs.
 */
@Component(immediate = true)
@Service
public class VpnPortManager implements VpnPortService {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Set<VpnPortListener> listeners = Sets
            .newCopyOnWriteArraySet();
    private static final String VPNPORT = "evpn-vpn-port-store";
    private static final String EVPN_APP = "org.onosproject.evpn";
    private static final String VPNPORT_ID_NOT_NULL = "VpnPort ID cannot be null";
    private static final String VPNPORT_NOT_NULL = "VpnPort cannot be null";
    private static final String JSON_NOT_NULL = "JsonNode can not be null";
    private static final String RESPONSE_NOT_NULL = "JsonNode can not be null";
    private static final String LISTENER_NOT_NULL = "Listener cannot be null";
    private static final String EVENT_NOT_NULL = "event cannot be null";

    protected EventuallyConsistentMap<VpnPortId, VpnPort> vpnPortStore;
    protected ApplicationId appId;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Activate
    public void activate() {
        appId = coreService.registerApplication(EVPN_APP);
        KryoNamespace.Builder serializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API).register(VpnPort.class)
                .register(VpnPortId.class);
        vpnPortStore = storageService
                .<VpnPortId, VpnPort>eventuallyConsistentMapBuilder()
                .withName(VPNPORT).withSerializer(serializer)
                .withTimestampProvider((k, v) -> new WallClockTimestamp())
                .build();
        log.info("Evpn Vpn Port Started");
    }

    @Deactivate
    public void deactivate() {
        vpnPortStore.destroy();
        log.info("Evpn Vpn Port Stop");
    }

    @Override
    public boolean exists(VpnPortId vpnPortId) {
        checkNotNull(vpnPortId, VPNPORT_ID_NOT_NULL);
        return vpnPortStore.containsKey(vpnPortId);
    }

    @Override
    public VpnPort getPort(VpnPortId vpnPortId) {
        checkNotNull(vpnPortId, VPNPORT_ID_NOT_NULL);
        return vpnPortStore.get(vpnPortId);
    }

    @Override
    public Collection<VpnPort> getPorts() {
        return Collections.unmodifiableCollection(vpnPortStore.values());
    }

    @Override
    public boolean createPorts(Iterable<VpnPort> vpnPorts) {
        checkNotNull(vpnPorts, VPNPORT_NOT_NULL);
        for (VpnPort vpnPort : vpnPorts) {
            log.debug("vpnPortId is  {} ", vpnPort.id().toString());
            vpnPortStore.put(vpnPort.id(), vpnPort);
            if (!vpnPortStore.containsKey(vpnPort.id())) {
                log.debug("The vpnPort is created failed whose identifier is {} ",
                          vpnPort.id().toString());
                return false;
            }
            notifyListeners(new VpnPortEvent(VpnPortEvent.Type.VPNPORT_SET, vpnPort));
        }
        return true;
    }

    @Override
    public boolean updatePorts(Iterable<VpnPort> vpnPorts) {
        checkNotNull(vpnPorts, VPNPORT_NOT_NULL);
        for (VpnPort vpnPort : vpnPorts) {
            if (!vpnPortStore.containsKey(vpnPort.id())) {
                log.debug("The vpnPort is not exist whose identifier is {}",
                          vpnPort.id().toString());
                return false;
            }
            vpnPortStore.put(vpnPort.id(), vpnPort);
            if (!vpnPort.equals(vpnPortStore.get(vpnPort.id()))) {
                log.debug("The vpnPort is updated failed whose  identifier is {}",
                          vpnPort.id().toString());
                return false;
            }
            notifyListeners(new VpnPortEvent(VpnPortEvent.Type.VPNPORT_UPDATE, vpnPort));
        }
        return true;
    }

    @Override
    public boolean removePorts(Iterable<VpnPortId> vpnPortIds) {
        checkNotNull(vpnPortIds, VPNPORT_NOT_NULL);
        for (VpnPortId vpnPortid : vpnPortIds) {
            VpnPort vpnPort = vpnPortStore.get(vpnPortid);
            vpnPortStore.remove(vpnPortid);
            if (vpnPortStore.containsKey(vpnPortid)) {
                log.debug("The vpnPort is removed failed whose identifier is {}",
                          vpnPortid.toString());
                return false;
            }
            notifyListeners(new VpnPortEvent(VpnPortEvent.Type.VPNPORT_DELETE, vpnPort));
        }
        return true;
    }

    @Override
    public void processEtcdResponse(EtcdResponse response) {
        checkNotNull(response, RESPONSE_NOT_NULL);
        if (response.action.equals("delete")) {
            String[] list = response.key.split("/");
            VpnPortId vpnPortId = VpnPortId.vpnPortId(list[list.length - 1]);
            Set<VpnPortId> vpnPortIds = Sets.newHashSet(vpnPortId);
            removePorts(vpnPortIds);
        } else {
            Collection<VpnPort> vpnPorts = changeJsonToSub(response.value);
            createPorts(vpnPorts);
        }
    }

    /**
     * Returns a collection of vpnPort from subnetNodes.
     *
     * @param vpnPortNodes the vpnPort json node
     * @return
     */
    private Collection<VpnPort> changeJsonToSub(JsonNode vpnPortNodes) {
        checkNotNull(vpnPortNodes, JSON_NOT_NULL);
        Map<VpnPortId, VpnPort> vpnPortMap = new HashMap<>();
        VpnPortId id = VpnPortId.vpnPortId(vpnPortNodes.get("id").asText());
        VpnInstanceId vpnInstanceId = VpnInstanceId
                .vpnInstanceId(vpnPortNodes.get("vpn_instance").asText());
        VpnPort vpnPort = new DefaultVpnPort(id, vpnInstanceId);
        vpnPortMap.put(id, vpnPort);

        return Collections.unmodifiableCollection(vpnPortMap.values());
    }

    @Override
    public void addListener(VpnPortListener listener) {
        checkNotNull(listener, LISTENER_NOT_NULL);
        listeners.add(listener);
    }

    @Override
    public void removeListener(VpnPortListener listener) {
        checkNotNull(listener, LISTENER_NOT_NULL);
        listeners.add(listener);
    }

    /**
     * Notifies specify event to all listeners.
     *
     * @param event Vpn Port event
     */
    private void notifyListeners(VpnPortEvent event) {
        checkNotNull(event, EVENT_NOT_NULL);
        listeners.forEach(listener -> listener.event(event));
    }
}
