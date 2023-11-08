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

import org.apache.felix.service.command.CommandSession;
import org.apache.karaf.shell.console.CommandSessionHolder;
import org.apache.karaf.shell.console.completer.ArgumentCompleter;
import org.apache.karaf.shell.console.completer.StringsCompleter;

import java.util.Arrays;
import java.util.List;

/**
 * String completer which excludes strings already included in the preceding argument list.
 */
public class UniqueStringsCompleter extends StringsCompleter {

    @Override
    public int complete(String buffer, int cursor, List candidates) {

        // Get all preceding arguments
        CommandSession session = CommandSessionHolder.getSession();
        List<String> prevArgsList = Arrays.asList(((ArgumentCompleter.ArgumentList) session
                .get("ARGUMENTS_LIST")).getArguments());

        super.complete(buffer, cursor, candidates);

        // Remove from candidate list all strings included in preceding arguments
        candidates.removeAll(prevArgsList);

        return candidates.isEmpty() ? -1 : 0;
    }

}
