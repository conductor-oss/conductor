/*
 * Copyright 2025 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.conductoross.conductor.tasks.http.providers.interceptors;

import java.util.List;

import org.conductoross.conductor.tasks.http.config.HttpWorkerBlockConfig;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HostCheckerImplTest {

    private HostCheckerImpl checker(List<String> hosts, List<String> allowedHosts) {
        HttpWorkerBlockConfig config = new HttpWorkerBlockConfig();
        config.setHosts(hosts);
        config.setAllowedHosts(allowedHosts);
        return new HostCheckerImpl(config);
    }

    @Test
    public void exactMatchIsBlocked() {
        HostChecker checker = checker(List.of("internal-db-host"), List.of());
        assertTrue(checker.isBlocked("internal-db-host"));
        assertFalse(checker.isBlocked("public-host"));
    }

    @Test
    public void matchIsCaseInsensitive() {
        HostChecker checker = checker(List.of("Internal.Corp"), List.of());
        assertTrue(checker.isBlocked("internal.corp"));
        assertTrue(checker.isBlocked("INTERNAL.CORP"));
    }

    @Test
    public void singleLevelWildcardMatchesOneLabelOnly() {
        HostChecker checker = checker(List.of("*.internal.corp"), List.of());
        assertTrue(checker.isBlocked("db.internal.corp"));
        // Wildcard is single-level: does not span an extra dotted label.
        assertFalse(checker.isBlocked("a.b.internal.corp"));
        // The bare apex is not matched by *.internal.corp.
        assertFalse(checker.isBlocked("internal.corp"));
    }

    @Test
    public void allowedHostOverridesBlock() {
        HostChecker checker = checker(List.of("*.internal.corp"), List.of("api.internal.corp"));
        assertFalse(checker.isBlocked("api.internal.corp"));
        assertTrue(checker.isBlocked("db.internal.corp"));
        assertTrue(checker.isExplicitlyAllowed("api.internal.corp"));
        assertFalse(checker.isExplicitlyAllowed("db.internal.corp"));
    }

    @Test
    public void emptyConfigBlocksNothing() {
        HostChecker checker = checker(List.of(), List.of());
        assertFalse(checker.isBlocked("anything.example.com"));
    }
}
