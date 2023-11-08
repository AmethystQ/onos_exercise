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
import org.onosproject.net.DeviceId;
import org.onlab.packet.MacAddress;
import org.onosproject.cli.AbstractShellCommand;

import org.onosproject.l2fwd.MacTableEntry;
import org.onosproject.l2fwd.LayerTwoService;

import java.util.Map;

/**
 * Command to print flow table on a switch.
 */
@Command(scope = "onos", name = "show-mactable",
        description = "Show Mac table on a switch")
public class ShowMacTableCommand extends AbstractShellCommand {
    @Argument(index = 0, name = "device", description = "Device ID",
              required = true, multiValued = false)
    private String deviceStr = null;

    private static final String HEADER  = "\n\u001B[1;37mMAC Address\t\t" +
            "Port\t\tTimeout\t\tLast Access\u001B[0m";
    private static final String SEPARATOR   = "\u001B[1;37m------------" +
            "-----------------------------------------------------------" +
            "--------------------------------------\u001B[0m";
    private static final String FORMAT  = "\u001B[1;32m%" +
                "s\u001B[0m\t\t\u001B[1;37m%s\t\t%s\t\t%s\u001B[0m\n";

    @Override
    protected void execute() {
        DeviceId deviceId = DeviceId.deviceId(deviceStr);
        LayerTwoService layerTwoService = get(LayerTwoService.class);
        Map<MacAddress, MacTableEntry> macTable = layerTwoService.getDeviceMacTable(deviceId);
        if (!macTable.isEmpty()) {
            print(HEADER);
            for (Map.Entry<MacAddress, MacTableEntry> entry : macTable.entrySet()) {
                print(SEPARATOR);
                MacAddress macAddress = entry.getKey();
                MacTableEntry macTableEntry = entry.getValue();
                print(FORMAT,
                        macAddress.toString(),
                        macTableEntry.getPortNumber().toString(),
                        macTableEntry.getTimeDuration().toString(),
                        macTableEntry.getLastAccess().toString());
            }
        } else {
            print(" Empty Mac Table");
        }
    }
}
