/*
 * Copyright 2015-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.onosproject.provider.bgp.route.impl;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onosproject.bgp.controller.BgpController;
import org.onosproject.bgp.controller.BgpId;
import org.onosproject.bgp.controller.BgpPeer.OperationType;
import org.onosproject.bgp.controller.BgpRouteListener;
import org.onosproject.bgpio.protocol.BgpEvpnNlri;
import org.onosproject.bgpio.protocol.BgpUpdateMsg;
import org.onosproject.bgpio.protocol.evpn.BgpEvpnNlriVer4;
import org.onosproject.bgpio.protocol.evpn.BgpMacIpAdvNlriVer4;
import org.onosproject.bgpio.protocol.evpn.RouteType;
import org.onosproject.bgpio.types.BgpExtendedCommunity;
import org.onosproject.bgpio.types.BgpValueType;
import org.onosproject.bgpio.types.EthernetSegmentidentifier;
import org.onosproject.bgpio.types.MpReachNlri;
import org.onosproject.bgpio.types.MpUnReachNlri;
import org.onosproject.bgpio.types.MplsLabel;
import org.onosproject.bgpio.types.NlriDetailsType;
import org.onosproject.bgpio.types.RouteDistinguisher;
import org.onosproject.bgpio.types.RouteTarget;
import org.onosproject.core.CoreService;
import org.onosproject.incubator.net.evpnrouting.EvpnRoute;
import org.onosproject.incubator.net.evpnrouting.EvpnRoute.Source;
import org.onosproject.incubator.net.evpnrouting.EvpnRouteAdminService;
import org.onosproject.incubator.provider.BgpEvpnRouteProvider;
import org.onosproject.incubator.provider.BgpEvpnRouteProviderRegistry;
import org.onosproject.incubator.provider.BgpEvpnRouteProviderService;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.provider.AbstractProvider;
import org.onosproject.net.provider.ProviderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider which uses an BGP controller to update/delete route.
 */
@Component(immediate = true)
public class BgpRouteProvider extends AbstractProvider
        implements BgpEvpnRouteProvider {

    /**
     * Creates an instance of BGP route provider.
     */
    public BgpRouteProvider() {
        super(new ProviderId("route",
                             "org.onosproject.provider.bgp.route.impl"));
    }

    private static final Logger log = LoggerFactory
            .getLogger(BgpRouteProvider.class);

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected BgpEvpnRouteProviderRegistry providerRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected BgpController controller;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected EvpnRouteAdminService routeAdminService;

    private final InternalBgpRouteListener bgpRouteListener = new InternalBgpRouteListener();
    private BgpEvpnRouteProviderService providerService;

    @Activate
    public void activate() {
        providerService = providerRegistry.register(this);
        controller.addRouteListener(bgpRouteListener);
        log.info("Bgp Route Provider activate");
    }

    @Deactivate
    public void deactivate() {
        controller.removeRouteListener(bgpRouteListener);
        providerRegistry.unregister(this);
        providerService = null;
        log.info("Bgp Route Provider deactivate");
    }

    private void sendUpdateMessage(OperationType operationType, String rdString,
                                   String rtString, Ip4Address nextHop,
                                   MacAddress macAddress, InetAddress ipAddress, int labelInt) {

        List<BgpEvpnNlri> eVpnComponents = new ArrayList<BgpEvpnNlri>();
        RouteDistinguisher rd = stringToRD(rdString);
        EthernetSegmentidentifier esi = new EthernetSegmentidentifier(new byte[10]);
        int ethernetTagID = 0;
        MplsLabel mplsLabel1 = intToLabel(labelInt);
        MplsLabel mplsLabel2 = null;

        List<BgpValueType> extCom = new ArrayList<BgpValueType>();
        if (operationType == OperationType.UPDATE && rtString != null) {
            RouteTarget rTarget = stringToRT(rtString);
            extCom.add(rTarget);
        }
        BgpMacIpAdvNlriVer4 routeTypeSpec = new BgpMacIpAdvNlriVer4(rd, esi,
                                                                    ethernetTagID,
                                                                    macAddress,
                                                                    ipAddress,
                                                                    mplsLabel1,
                                                                    mplsLabel2);
        BgpEvpnNlri nlri = new BgpEvpnNlriVer4(RouteType.MAC_IP_ADVERTISEMENT
                .getType(), routeTypeSpec);
        eVpnComponents.add(nlri);

        controller.getPeers().forEach(peer -> {
            log.info("Send route update eVpnComponents {} to peer {}",
                     eVpnComponents, peer);
            peer.updateEvpn(operationType, nextHop, extCom, eVpnComponents);
        });

    }

    private static RouteDistinguisher stringToRD(String rdString) {
        if (rdString.contains(":")) {
            if ((rdString.indexOf(":") != 0)
                    && (rdString.indexOf(":") != rdString.length() - 1)) {
                String[] tem = rdString.split(":");
                short as = (short) Integer.parseInt(tem[0]);
                int assignednum = Integer.parseInt(tem[1]);
                long rd = ((long) assignednum & 0xFFFFFFFFL)
                        | (((long) as << 32) & 0xFFFFFFFF00000000L);
                return new RouteDistinguisher(rd);
            }
        }
        return null;

    }

    private static String rdToString(RouteDistinguisher rd) {
        long rdLong = rd.getRouteDistinguisher();
        int as = (int) ((rdLong & 0xFFFFFFFF00000000L) >> 32);
        int assignednum = (int) (rdLong & 0xFFFFFFFFL);
        String result = as + ":" + assignednum;
        return result;
    }

    private static RouteTarget stringToRT(String rdString) {
        if (rdString.contains(":")) {
            if ((rdString.indexOf(":") != 0)
                    && (rdString.indexOf(":") != rdString.length() - 1)) {
                String[] tem = rdString.split(":");
                short as = Short.parseShort(tem[0]);
                int assignednum = Integer.parseInt(tem[1]);

                byte[] rt = new byte[] {(byte) ((as >> 8) & 0xFF),
                                        (byte) (as & 0xFF),
                                        (byte) ((assignednum >> 24) & 0xFF),
                                        (byte) ((assignednum >> 16) & 0xFF),
                                        (byte) ((assignednum >> 8) & 0xFF),
                                        (byte) (assignednum & 0xFF) };
                short type = 0x02;
                return new RouteTarget(type, rt);
            }
        }
        return null;

    }

    private static String rtToString(RouteTarget rt) {
        byte[] b = rt.getRouteTarget();

        int assignednum = b[5] & 0xFF | (b[4] & 0xFF) << 8 | (b[3] & 0xFF) << 16
                | (b[2] & 0xFF) << 24;
        short as = (short) (b[1] & 0xFF | (b[0] & 0xFF) << 8);
        String result = as + ":" + assignednum;
        return result;
    }

    private static MplsLabel intToLabel(int labelInt) {
        byte[] label = new byte[] {(byte) ((labelInt >> 16) & 0xFF),
                                   (byte) ((labelInt >> 8) & 0xFF),
                                   (byte) (labelInt & 0xFF) };

        return new MplsLabel(label);
    }

    private static int labelToInt(MplsLabel label) {
        byte[] b = label.getMplsLabel();
        return b[2] & 0xFF | (b[1] & 0xFF) << 8 | (b[0] & 0xFF) << 16;

    }

    private class InternalBgpRouteListener implements BgpRouteListener {

        @Override
        public void processRoute(BgpId bgpId, BgpUpdateMsg updateMsg) {
            List<BgpValueType> pathAttr = updateMsg.bgpPathAttributes()
                    .pathAttributes();
            Iterator<BgpValueType> iterator = pathAttr.iterator();
            RouteTarget rt = null;
            List<BgpEvpnNlri> evpnReachNlri =  new LinkedList<>();
            List<BgpEvpnNlri> evpnUnreachNlri =  new LinkedList<>();

            Ip4Address ipNextHop = null;
            while (iterator.hasNext()) {
                BgpValueType attr = iterator.next();
                if (attr instanceof MpReachNlri) {
                    MpReachNlri mpReachNlri = (MpReachNlri) attr;
                    ipNextHop = mpReachNlri.nexthop4();
                    if (mpReachNlri
                            .getNlriDetailsType() == NlriDetailsType.EVPN) {
                        evpnReachNlri.addAll(mpReachNlri.bgpEvpnNlri());
                    }

                }
                if (attr instanceof MpUnReachNlri) {
                    MpUnReachNlri mpUnReachNlri = (MpUnReachNlri) attr;
                    if (mpUnReachNlri
                            .getNlriDetailsType() == NlriDetailsType.EVPN) {
                        evpnUnreachNlri.addAll(mpUnReachNlri.bgpEvpnNlri());
                    }
                }

                if (attr instanceof BgpExtendedCommunity) {
                    BgpExtendedCommunity extCom = (BgpExtendedCommunity) attr;
                    Iterator<BgpValueType> extIte = extCom.fsActionTlv()
                            .iterator();
                    while (extIte.hasNext()) {
                        BgpValueType extAttr = extIte.next();
                        if (extAttr instanceof RouteTarget) {
                            rt = (RouteTarget) extAttr;
                            break;
                        }
                    }
                }
            }

            if ((rt != null) && (!evpnReachNlri.isEmpty())) {
                for (BgpEvpnNlri nlri : evpnReachNlri) {
                    if (nlri.getRouteType() == RouteType.MAC_IP_ADVERTISEMENT) {
                        BgpMacIpAdvNlriVer4 macIpAdvNlri = (BgpMacIpAdvNlriVer4) nlri
                                .getRouteTypeSpec();
                        MacAddress macAddress = macIpAdvNlri.getMacAddress();
                        Ip4Address ipAddress = Ip4Address.valueOf(macIpAdvNlri.getIpAddress());
                        RouteDistinguisher rd = macIpAdvNlri
                                .getRouteDistinguisher();
                        MplsLabel label = macIpAdvNlri.getMplsLable1();
                        log.info("Route Provider received bgp packet {} to route system.",
                                 macIpAdvNlri.toString());
                        // Add route to route system
                        Source source = Source.REMOTE;
                        EvpnRoute evpnRoute = new EvpnRoute(source, macAddress,
                                                            ipAddress,
                                                            ipNextHop,
                                                            rdToString(rd),
                                                            rtToString(rt),
                                                            labelToInt(label));
                        routeAdminService.updateEvpnRoute(Collections
                                .singleton(evpnRoute));
                    }
                }
            }

            if (!evpnUnreachNlri.isEmpty()) {
                for (BgpEvpnNlri nlri : evpnUnreachNlri) {
                    if (nlri.getRouteType() == RouteType.MAC_IP_ADVERTISEMENT) {
                        BgpMacIpAdvNlriVer4 macIpAdvNlri = (BgpMacIpAdvNlriVer4) nlri
                                .getRouteTypeSpec();
                        MacAddress macAddress = macIpAdvNlri.getMacAddress();
                        Ip4Address ipAddress = Ip4Address.valueOf(macIpAdvNlri.getIpAddress());
                        RouteDistinguisher rd = macIpAdvNlri
                                .getRouteDistinguisher();
                        MplsLabel label = macIpAdvNlri.getMplsLable1();
                        log.info("Route Provider received bgp packet {} and remove from route system.",
                                 macIpAdvNlri.toString());
                        // Delete route from route system
                        Source source = Source.REMOTE;
                        // For mpUnreachNlri, nexthop and rt is null
                        EvpnRoute evpnRoute = new EvpnRoute(source, macAddress,
                                                            ipAddress,
                                                            null,
                                                            rdToString(rd),
                                                            null,
                                                            labelToInt(label));
                        routeAdminService.withdrawEvpnRoute(Collections
                                .singleton(evpnRoute));
                    }
                }
            }
        }
    }

    @Override
    public void sendEvpnRoute(EvpnRoute.OperationType type, EvpnRoute evpnRoute) {
        OperationType operationType = null;
        switch (type) {
        case UPDATE:
            operationType = OperationType.UPDATE;
            break;
        case REMOVE:
            operationType = OperationType.DELETE;
            break;
        default:
            break;
        }
        String rdString = evpnRoute.routeDistinguisher()
                .getRouteDistinguisher();
        MacAddress macAddress = evpnRoute.prefixMac();
        InetAddress inetAddress = evpnRoute.prefixIp().toInetAddress();
        Ip4Address nextHop = evpnRoute.nextHop();
        String rtString = evpnRoute.routeTarget().getRouteTarget();
        int labelInt = evpnRoute.label().getLabel();
        sendUpdateMessage(operationType, rdString, rtString, nextHop,
                          macAddress, inetAddress, labelInt);
    }
}
