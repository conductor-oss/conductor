/*
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.conductor.jedis;

import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.TokenMapSupplier;
import com.netflix.dyno.connectionpool.impl.lb.HostToken;
import com.netflix.dyno.connectionpool.impl.utils.CollectionUtils;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TokenMapSupplierProvider implements Provider<TokenMapSupplier> {
    private final List<HostToken> hostTokens;

    @Inject
    public TokenMapSupplierProvider() {
        this.hostTokens = new ArrayList<>();
    }

    @Override
    public TokenMapSupplier get() {
        return new TokenMapSupplier() {
            @Override
            public List<HostToken> getTokens(Set<Host> activeHosts) {
                long i = activeHosts.size();
                for (Host host : activeHosts) {
                    HostToken hostToken = new HostToken(i, host);
                    hostTokens.add(hostToken);
                    i--;
                }
                return hostTokens;
            }

            @Override
            public HostToken getTokenForHost(Host host, Set<Host> activeHosts) {
                return CollectionUtils.find(hostTokens, token -> token.getHost().compareTo(host) == 0);
            }
        };
    }
}
