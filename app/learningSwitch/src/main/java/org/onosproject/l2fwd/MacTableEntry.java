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
package org.onosproject.l2fwd;

import org.onosproject.net.PortNumber;

import java.time.Instant;
import java.time.Duration;

public class MacTableEntry {
    private final PortNumber portNumber;
    private final Instant lastAccess;
    private final Duration timeDuration;

    public MacTableEntry(PortNumber portNumber, Duration timeDuration) {
        this.portNumber = portNumber;
        this.lastAccess = Instant.now();
        this.timeDuration = timeDuration;
    }

    public PortNumber getPortNumber() {
        return this.portNumber;
    }

    public Instant getLastAccess() {
        return this.lastAccess;
    }

    public Duration getTimeDuration() {
        return this.timeDuration;
    }

    public boolean checkTimeout() {
        Instant current = Instant.now();
        Duration elapsed = Duration.between(this.lastAccess, current);
        return elapsed.getSeconds() > this.timeDuration.getSeconds();
    }
}