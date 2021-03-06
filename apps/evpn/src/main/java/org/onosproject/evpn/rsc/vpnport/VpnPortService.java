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

package org.onosproject.evpn.rsc.vpnport;

import java.util.Collection;

import org.onosproject.evpn.rsc.EtcdResponse;
import org.onosproject.evpn.rsc.VpnPort;
import org.onosproject.evpn.rsc.VpnPortId;


/**
 * Service for interacting with the inventory of VPN port.
 */
public interface VpnPortService {
    /**
     * Returns if the vpnPort is existed.
     *
     * @param vpnPortId vpnPort identifier
     * @return true or false if one with the given identifier is not existed.
     */
    boolean exists(VpnPortId vpnPortId);

    /**
     * Returns the vpnPort with the identifier.
     *
     * @param vpnPortId vpnPort ID
     * @return VpnPort or null if one with the given ID is not know.
     */
    VpnPort getPort(VpnPortId vpnPortId);

    /**
     * Returns the collection of the currently known vpnPort.
     * @return collection of VpnPort.
     */
    Collection<VpnPort> getPorts();

    /**
     * Creates vpnPorts by vpnPorts.
     *
     * @param vpnPorts the iterable collection of vpnPorts
     * @return true if all given identifiers created successfully.
     */
    boolean createPorts(Iterable<VpnPort> vpnPorts);

    /**
     * Updates vpnPorts by vpnPorts.
     *
     * @param vpnPorts the iterable  collection of vpnPorts
     * @return true if all given identifiers updated successfully.
     */
    boolean updatePorts(Iterable<VpnPort> vpnPorts);

    /**
     * Deletes vpnPortIds by vpnPortIds.
     *
     * @param vpnPortIds the iterable collection of vpnPort identifiers
     * @return true or false if one with the given identifier to delete is
     *         successfully.
     */
    boolean removePorts(Iterable<VpnPortId> vpnPortIds);

    /**
     * process Etcd response.
     *
     * @param response Etcd response
     */
    void processEtcdResponse(EtcdResponse response);

    /**
     * Adds the specified listener to Vpn Port manager.
     *
     * @param listener Vpn Port listener
     */
    void addListener(VpnPortListener listener);

    /**
     * Removes the specified listener to Vpn Port manager.
     *
     * @param listener Vpn Port listener
     */
    void removeListener(VpnPortListener listener);
}
