/*
 * Copyright 2016-present Open Networking Foundation
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

package org.onosproject.l2fwd;

import com.google.common.collect.Maps;
import org.apache.felix.scr.annotations.*;
import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.*;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.onlab.packet.Ethernet.TYPE_IPV4;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Component for the LayerTwoForward application.
 */
@Component(immediate = true)
@Service
public class LayerTwoManager implements LayerTwoService {

    public static final String L2FWD_APP = "org.onosproject.l2fwd";

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    private LayerTwoPacketProcessor processor = new LayerTwoPacketProcessor();
    private ApplicationId appId;

    protected Map<DeviceId, Map<MacAddress, MacTableEntry>> macTables
        = new ConcurrentHashMap<>();

    @Activate
    protected void activate() {
        appId = coreService.registerApplication(L2FWD_APP);
        packetService.addProcessor(processor, PacketProcessor.director(3));

        /*
         * Restricts packet types to IPV4 and ARP by only requesting those types.
         */
        packetService.requestPackets(DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4).build(), PacketPriority.REACTIVE, appId, Optional.empty());
        packetService.requestPackets(DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_ARP).build(), PacketPriority.REACTIVE, appId, Optional.empty());
        log.info(" l2fwd started");
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(processor);
        log.info(" l2fwd stopped");
    }

    @Override
    public Map<MacAddress, MacTableEntry> getDeviceMacTable(DeviceId deviceId) {
        return macTables.get(deviceId);
    }

    @Override
    public boolean installFirewallRule(IpAddress srcIpAddress, IpAddress dstIpAddress, PortNumber dstPort) {
        /**
         * Blocking flows from srcIP(ANY PORT) --> dstIP(dst port)
         */
        Iterable<Device> devices = deviceService.getDevices();
        for (Device d : devices) {
            /* Insert Firewall flow rule on every devices */

            // FlowRule fr = DefaultFlowRule.builder()
            //         .withSelector(DefaultTrafficSelector.builder()
            //                 .matchEthType(TYPE_IPV4)
            //                 .matchIPProtocol(IPv4.PROTOCOL_TCP)
            //                 .matchIPSrc(firstIpAddress.toIpPrefix())
            //                 .matchIPDst(secondIpAddress.toIpPrefix())
            //                 .matchTcpDst(TpPort.tpPort((int) portNumber.toLong()))
            //                 .build())
            //         .withTreatment(DefaultTrafficTreatment.builder().drop().build())
            //         .forDevice(d.id()).withPriority(PacketPriority.CONTROL.priorityValue())
            //         .makeTemporary(30)
            //         .fromApp(appId).build();
            // log.info(" On device {} install firewall rule: {}", d.id(), fr);
            // flowRuleService.applyFlowRules(fr);
            FlowRule fr = DefaultFlowRule.builder()
                     .withSelector(DefaultTrafficSelector.builder()
                             .matchEthType(TYPE_IPV4)
                             .matchIPProtocol(IPv4.PROTOCOL_TCP)
                             .matchIPSrc(srcIpAddress.toIpPrefix())
                             .matchIPDst(dstIpAddress.toIpPrefix())
                             .matchTcpDst(TpPort.tpPort((int) dstPort.toLong()))
                             .build())
                     .withTreatment(DefaultTrafficTreatment.builder().drop().build())
                     .forDevice(d.id()).withPriority(PacketPriority.CONTROL.priorityValue())
                     .makePermanent()
                     .fromApp(appId).build();
            log.info(" On device {} install firewall rule: {}", d.id(), fr);
            flowRuleService.applyFlowRules(fr);
        }
        return true;
    }


    /**
     * Packet processor responsible for forwarding packets along their paths.
     */
    private class LayerTwoPacketProcessor implements PacketProcessor {

        /**
         * Learns the source port associated with the packet's DeviceId if it has not
         * already been learned.
         * Calls forward() to process and send the packet.
         *
         * @param pc PacketContext object containing packet info
         */
        @Override
        public void process(PacketContext pc) {
            /* Record source information(srcIP, input port) in MAC table */
            initMacTable(pc.inPacket().receivedFrom());

            /*
             * This is the call to the forward() method you will be creating. When
             * you are ready to test it, uncomment the line below, and comment out the
             * flood call above.
             */
            forward(pc);
        }

        /**
         * Flood a packet. Floods packet out of all switch ports.
         *
         * @param pc the PacketContext object passed through from activate() method
         */
        public void flood(PacketContext pc) {
            pc.treatmentBuilder().setOutput(PortNumber.FLOOD);
            pc.send();
        }

        /**
         * Forward a packet.
         * 
         * Ensures packet is of required type. Obtain the PortNumber associated with the
         * inPackets DeviceId.
         * If this port has previously been learned (in initMacTable method) build a
         * flow using the packet's
         * out port, treatment, destination, and other properties. Send the flow to the
         * learned out port.
         * Otherwise, flood packet to all ports if out port is not learned.
         *
         * @param pc the PacketContext object passed through from activate() method
         */
        public void forward(PacketContext pc) {

            /*
             * Ensures the type of packet being processed is only of type IPV4 (not LLDP or BDDP). 
             * If it is not, return and do nothing with the packet. forward() can only process IPV4 packets.
             */
            Short type = pc.inPacket().parsed().getEtherType();
            if (type != Ethernet.TYPE_IPV4) {
                return;
            }

            /*
             * [MAIN PROCESSING]
             * 
             * Learn the destination, source, and output port of the packet using a ConnectPoint and the associated MAC table. 
             * If there is a known port associated with the packet's destination MAC Address, the output port will not be null.
             */
            ConnectPoint cp = pc.inPacket().receivedFrom();
            Map<MacAddress, MacTableEntry> macTable = macTables.get(cp.deviceId());
            MacAddress srcMac = pc.inPacket().parsed().getSourceMAC();
            MacAddress dstMac = pc.inPacket().parsed().getDestinationMAC();

            /* Insert a MAC table entry with time duration for input IP address */
            MacTableEntry macTableEntry = new MacTableEntry(cp.port(), Duration.ofSeconds(60));
            macTable.put(srcMac, macTableEntry);

            PortNumber outPort = null;
            if (!macTable.containsKey(dstMac)) {
                flood(pc);
            } else {
                outPort = macTable.get(dstMac).getPortNumber();
                pc.treatmentBuilder().setOutput(outPort);
                FlowRule fr = DefaultFlowRule.builder()
                           .withSelector(DefaultTrafficSelector.builder().matchEthDst(dstMac).build())
                           .withTreatment(DefaultTrafficTreatment.builder().setOutput(outPort).build())
                           .forDevice(cp.deviceId()).withPriority(PacketPriority.REACTIVE.priorityValue())
                           .makeTemporary(60)
                           .fromApp(appId).build();
                flowRuleService.applyFlowRules(fr);
                pc.send();
            }

            /** 
             * Lookup for destination host in MAC table
             * 
             * If lookup succeeded, get the port via which we should forward this packet.
             *      Set pc's out port to the packet's learned output port.
             *      Construct a FlowRule using a source, destination, treatment and other properties. 
             *      Insert the FlowRule to the designated output port.
             * Otherwise, we haven't learnt the output port yet. We need to flood this packet to all the ports.
             */

            /**
             * * HINT: install FlowRule using the following method(more detailed API usage can be found in ONOS website)
             * FlowRule fr = DefaultFlowRule.builder()
             *              .withSelector(DefaultTrafficSelector.builder().matchEthDst(IP_ADDR).build())
             *              .withTreatment(DefaultTrafficTreatment.builder().setOutput(PORT_NUM).build())
             *              .forDevice(cp.deviceId()).withPriority(PacketPriority.REACTIVE.priorityValue())
             *              .fromApp(appId).build();
             */
             
        }

        /**
         * puts the ConnectPoint's device Id into the map macTables if it has not
         * previously been added.
         *
         * @param cp ConnectPoint containing the required DeviceId for the map
         */
        private void initMacTable(ConnectPoint cp) {
            macTables.putIfAbsent(cp.deviceId(), Maps.newConcurrentMap());
        }

    }
}
