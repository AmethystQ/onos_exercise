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

package org.onosproject.l2fwd.cli.commands;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.net.PortNumber;
import org.onlab.packet.IpAddress;
import org.onosproject.cli.AbstractShellCommand;

import org.onosproject.l2fwd.LayerTwoService;

/**
 * Command to install firewall rule on devices
 */
@Command(scope = "onos", name = "add-firewall-on-port",
        description = "Show Mac table on a switch")
public class AddFirewallOnPortCommand extends AbstractShellCommand {
    @Argument(index = 0, name = "src host", description = "The IP address of the source host",
              required = true, multiValued = false)
    private String srcIp = null;

    @Argument(index = 1, name = "dst host", description = "The IP address of the destination host",
              required = true, multiValued = false)
    private String dstIp = null;

    @Argument(index = 2, name = "restrained port", description = "Restrained port on destination host",
              required = true, multiValued = false)
    private String port = null;

    @Override
    protected void execute() {
        IpAddress srcIpAddress = IpAddress.valueOf(srcIp);
        IpAddress dstIpAddress = IpAddress.valueOf(dstIp);
        PortNumber dstPort = PortNumber.fromString(port);
        LayerTwoService layerTwoService = get(LayerTwoService.class);
        layerTwoService.installFirewallRule(srcIpAddress, dstIpAddress, dstPort);
    }
}
