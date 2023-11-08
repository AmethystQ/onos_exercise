/*
 * Copyright 2014 Open Networking Foundation
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
package org.onosproject.ifwd;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.HostToHostIntent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.IntentState;
import org.onosproject.net.intent.Key;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;

import java.util.EnumSet;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * WORK-IN-PROGRESS: Sample reactive forwarding application using intent framework.
 */
@Component(immediate = true)
public class IntentReactiveForwarding {

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    private ReactivePacketProcessor processor = new ReactivePacketProcessor();
    private ApplicationId appId;

    private static final int DROP_RULE_TIMEOUT = 300;

    private static final EnumSet<IntentState> WITHDRAWN_STATES = EnumSet.of(IntentState.WITHDRAWN,
                                                                            IntentState.WITHDRAWING,
                                                                            IntentState.WITHDRAW_REQ);

    @Activate
    public void activate() {
        /* Register Intent Forward application in ONOS project */
        appId = coreService.registerApplication("org.onosproject.ifwd");

        /* Add specified packet processor to Packet Service */
        packetService.addProcessor(processor, PacketProcessor.director(2));

        /* Set up packet filter for IP packets */
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);

        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        /* Remove packet processor from packet service */
        packetService.removeProcessor(processor);
        processor = null;
        log.info("Stopped");
    }

    
    /**
     * Packet processor responsible for forwarding packets along their paths.
     */
    private class ReactivePacketProcessor implements PacketProcessor {
        /**
         * Reactive packet forwaring.
         * 
         * If the destination of incoming packet is known by the device, simply forward this packet via the output port.
         * Otherwise, flood this packet to neighbors.
         *
         * @param pc PacketContext object containing packet info
         */
        @Override
        public void process(PacketContext context) {
            /* Skip processing if the packet has already been handled! */
            if (context.isHandled()) {
                return;
            }

            InboundPacket pkt;
            Ethernet ethPkt;

            /* Fetch incoming packet */
            pkt = context.inPacket();

            /** 
             * [STEP 1] Extract Ethernet header
             * more specifically, we need the source and destination IP address
             */
            ethPkt = pkt.parsed();
            HostId srcId = HostId.hostId(ethPkt.getSourceMAC());
            HostId dstId = HostId.hostId(ethPkt.getDestinationMAC());

            /** 
             * [STEP 2] Do we know where the destination host is and which host should we hand this packet to?
             * If not, flood this packet and bail.
             * Otherwise, just forward it to the next hop and processing is done.
             * * HINT: use setUpConnectivity() to install flow rule
             */
            Host destination = hostService.getHost(dstId);
            if (destination != null) {
                setUpConnectivity(context,srcId,dstId);
                forwardPacketToDst(context,destination);
            } else {
                flood(context);
            }
        }
    }

    /**
     * Send out the packet via the specified port.
     * 
     * @param pc PacketContext object containing packet info
     * @param portNumber the specified port through which this packet will be send out
     */
    private void packetOut(PacketContext context, PortNumber portNumber) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }

    /**
     * Flood the incoming packet to neighbors.
     * 
     * @param pc PacketContext object containing packet info
     */
    private void flood(PacketContext context) {
        if (topologyService.isBroadcastPoint(topologyService.currentTopology(),
                                             context.inPacket().receivedFrom())) {
            packetOut(context, PortNumber.FLOOD);
        } else {
            context.block();
        }
    }

    /**
     * Forward the incoming packet to specified destination.
     * 
     * @param pc PacketContext object containing packet info
     * @param dst the next hop of this packet
     */
    private void forwardPacketToDst(PacketContext context, Host dst) {
        /* Build and send out packet to destination host */
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
            .setOutput(dst.location().port()).build();
        OutboundPacket pkt = new DefaultOutboundPacket(dst.location().deviceId(),
            treatment,context.inPacket().unparsed());
        packetService.emit(pkt);
    }

    /* Install a rule forwarding the packet to the specified port. */
    private void setUpConnectivity(PacketContext context, HostId srcId, HostId dstId) {
        TrafficSelector selector = DefaultTrafficSelector.emptySelector();
        TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();

        Key key;
        if (srcId.toString().compareTo(dstId.toString()) < 0) {
            key = Key.of(srcId.toString() + dstId.toString(), appId);
        } else {
            key = Key.of(dstId.toString() + srcId.toString(), appId);
        }

        HostToHostIntent intent = (HostToHostIntent) intentService.getIntent(key);
        if (intent != null) {
            if (WITHDRAWN_STATES.contains(intentService.getIntentState(key))) {
                /* This intent has been withdrawn, just insert it once more! */

                /* Build host-to-host intent and submit to Intent Service */
                HostToHostIntent hostIntent;
                hostIntent = HostToHostIntent.builder()
                    .appId(appId).key(key).one(srcId).two(dstId)
                    .selector(selector).treatment(treatment).build();
                intentService.submit(hostIntent);
            } else if (intentService.getIntentState(key) == IntentState.FAILED) {
                /* Special case: handle failed intent */
                TrafficSelector objectiveSelector = DefaultTrafficSelector.builder()
                        .matchEthSrc(srcId.mac()).matchEthDst(dstId.mac()).build();
                TrafficTreatment dropTreatment = DefaultTrafficTreatment.builder()
                        .drop().build();
                ForwardingObjective objective = DefaultForwardingObjective.builder()
                        .withSelector(objectiveSelector)
                        .withTreatment(dropTreatment)
                        .fromApp(appId)
                        .withPriority(intent.priority() - 1)
                        .makeTemporary(DROP_RULE_TIMEOUT)
                        .withFlag(ForwardingObjective.Flag.VERSATILE)
                        .add();
                flowObjectiveService.forward(context.outPacket().sendThrough(), objective);
            }
        } else if (intent == null) {
            /** 
             * This intent has never been inserted before, 
             * we should submit it to Intent Service.
             */

            /* Build host-to-host intent and submit to Intent Service */
            HostToHostIntent hostIntent;
            hostIntent = HostToHostIntent.builder()
                    .appId(appId).key(key).one(srcId).two(dstId)
                    .selector(selector).treatment(treatment).build();
            intentService.submit(hostIntent);
        }

    }

}
