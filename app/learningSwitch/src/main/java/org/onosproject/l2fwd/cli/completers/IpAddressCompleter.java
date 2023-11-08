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
package org.onosproject.l2fwd.cli.completers;

import org.apache.karaf.shell.console.completer.StringsCompleter;
import org.onosproject.cli.AbstractCompleter;
import org.onosproject.cli.AbstractShellCommand;

import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;

import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;

public class IpAddressCompleter extends AbstractCompleter {
    @Override
    public int complete(String buffer, int cursor, List<String> candidates) {
        // Delegate string completer
        StringsCompleter delegate = new StringsCompleter();
        HostService service = AbstractShellCommand.get(HostService.class);
        Iterator<Host> it = service.getHosts().iterator();
        SortedSet<String> strings = delegate.getStrings();

        while (it.hasNext()) {
            it.next().ipAddresses().forEach(ip -> {
                strings.add(ip.toString());
            });
        }

        // Now let the completer do the work for figuring out what to offer.
        return delegate.complete(buffer, cursor, candidates);
    }
}
