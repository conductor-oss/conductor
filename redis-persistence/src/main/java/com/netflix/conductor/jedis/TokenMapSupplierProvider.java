package com.netflix.conductor.jedis;

import com.google.common.collect.Lists;

import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.TokenMapSupplier;
import com.netflix.dyno.connectionpool.impl.lb.HostToken;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;

public class TokenMapSupplierProvider implements Provider<TokenMapSupplier> {
    private final HostSupplier hostSupplier;

    @Inject
    public TokenMapSupplierProvider(HostSupplier hostSupplier) {
        this.hostSupplier = hostSupplier;
    }

    @Override
    public TokenMapSupplier get() {
        return new TokenMapSupplier() {

            // FIXME This isn't particularly safe, but it is equivalent to the existing code.
            // FIXME It seems like we should be supply tokens for more than one host?
            HostToken token = new HostToken(1L, Lists.newArrayList(hostSupplier.getHosts()).get(0));

            @Override
            public List<HostToken> getTokens(Set<Host> activeHosts) {
                return Arrays.asList(token);
            }

            @Override
            public HostToken getTokenForHost(Host host, Set<Host> activeHosts) {
                return token;
            }
        };
    }
}
