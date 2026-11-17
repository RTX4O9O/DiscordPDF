package me.rtx4090.discordpdf.net;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * JVM-wide DNS filter (JEP 418), registered through {@code META-INF/services}.
 *
 * <p>{@link NetworkGuard#checkUri} validates a URL before it is fetched, but the HTTP client
 * resolves the host again when it connects. A hostile DNS server could answer with a public
 * address the first time and a private one the second time ("DNS rebinding"). Filtering at the
 * resolver closes that gap: no hostname lookup in this JVM can ever return a non-public address.
 *
 * <p>"localhost" and the machine's own host name are passed through untouched (URLs pointing at
 * them are still rejected by {@link NetworkGuard}). Set {@code DISCORDPDF_DNS_GUARD=false} to
 * disable, e.g. when the bot must reach an internal proxy by name.
 */
public final class GuardedResolverProvider extends InetAddressResolverProvider {

    @Override
    public InetAddressResolver get(Configuration configuration) {
        InetAddressResolver builtin = configuration.builtinResolver();
        String disabled = System.getenv("DISCORDPDF_DNS_GUARD");
        if (disabled != null && disabled.trim().equalsIgnoreCase("false")) {
            return builtin;
        }
        return new Guarded(builtin, configuration.lookupLocalHostName());
    }

    @Override
    public String name() {
        return "discordpdf-dns-guard";
    }

    private static final class Guarded implements InetAddressResolver {

        private final InetAddressResolver delegate;
        private final String localHostName;

        Guarded(InetAddressResolver delegate, String localHostName) {
            this.delegate = delegate;
            this.localHostName = localHostName == null ? "" : localHostName.toLowerCase(Locale.ROOT);
        }

        @Override
        public Stream<InetAddress> lookupByName(String host, LookupPolicy policy) throws UnknownHostException {
            String h = host.toLowerCase(Locale.ROOT);
            if (h.equals("localhost") || h.equals(localHostName)) {
                return delegate.lookupByName(host, policy);
            }
            List<InetAddress> all = delegate.lookupByName(host, policy).toList();
            List<InetAddress> allowed = all.stream().filter(NetworkGuard::isPublic).toList();
            if (allowed.isEmpty()) {
                throw new UnknownHostException("non-public address blocked by discordpdf DNS guard");
            }
            return allowed.stream();
        }

        @Override
        public String lookupByAddress(byte[] addr) throws UnknownHostException {
            return delegate.lookupByAddress(addr);
        }
    }
}
