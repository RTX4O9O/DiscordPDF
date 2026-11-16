package io.github.rtx4o9o.discordpdf.net;

import io.github.rtx4o9o.discordpdf.core.UserFacingException;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * SSRF protection: only public internet addresses may be fetched.
 * Blocks loopback, private, link-local (incl. cloud metadata 169.254.169.254), CGNAT,
 * multicast, documentation/benchmark ranges and IPv6 equivalents / tunnels.
 */
public final class NetworkGuard {

    private NetworkGuard() {
    }

    /** Validates scheme, credentials and that every address the host resolves to is public. */
    public static void checkUri(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw new UserFacingException("error.url_invalid");
        }
        if (uri.getRawUserInfo() != null) {
            throw new UserFacingException("error.url_invalid");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new UserFacingException("error.url_invalid");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(stripBrackets(host));
        } catch (UnknownHostException e) {
            throw new UserFacingException("error.url_unresolvable");
        }
        if (addresses.length == 0) {
            throw new UserFacingException("error.url_unresolvable");
        }
        for (InetAddress a : addresses) {
            if (!isPublic(a)) {
                throw new UserFacingException("error.url_blocked");
            }
        }
    }

    private static String stripBrackets(String host) {
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    public static boolean isPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return false;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            return isPublicV4(b);
        }
        if (a instanceof Inet6Address) {
            return isPublicV6(b);
        }
        return false;
    }

    static boolean isPublicV4(byte[] b) {
        int o0 = b[0] & 0xFF;
        int o1 = b[1] & 0xFF;
        int o2 = b[2] & 0xFF;
        if (o0 == 0) return false;                                  // 0.0.0.0/8 "this network"
        if (o0 == 10) return false;                                 // 10/8 private
        if (o0 == 100 && o1 >= 64 && o1 <= 127) return false;       // 100.64/10 CGNAT
        if (o0 == 127) return false;                                // loopback
        if (o0 == 169 && o1 == 254) return false;                   // link-local, cloud metadata
        if (o0 == 172 && o1 >= 16 && o1 <= 31) return false;        // 172.16/12 private
        if (o0 == 192 && o1 == 0 && o2 == 0) return false;          // 192.0.0/24 IETF protocol
        if (o0 == 192 && o1 == 0 && o2 == 2) return false;          // TEST-NET-1
        if (o0 == 192 && o1 == 88 && o2 == 99) return false;        // 6to4 relay anycast
        if (o0 == 192 && o1 == 168) return false;                   // 192.168/16 private
        if (o0 == 198 && (o1 == 18 || o1 == 19)) return false;      // 198.18/15 benchmarking
        if (o0 == 198 && o1 == 51 && o2 == 100) return false;       // TEST-NET-2
        if (o0 == 203 && o1 == 0 && o2 == 113) return false;        // TEST-NET-3
        if (o0 >= 224) return false;                                // multicast, reserved, broadcast
        return true;
    }

    static boolean isPublicV6(byte[] b) {
        // ::/96 (IPv4-compatible, deprecated), ::, ::1
        if (allZero(b, 0, 12)) {
            return allZero(b, 12, 16) ? false : isPublicV4(slice(b, 12));
        }
        // ::ffff:0:0/96 IPv4-mapped (Java usually converts these to Inet4Address already)
        if (allZero(b, 0, 10) && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF) {
            return isPublicV4(slice(b, 12));
        }
        int w0 = ((b[0] & 0xFF) << 8) | (b[1] & 0xFF);
        int w1 = ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
        if ((b[0] & 0xFE) == 0xFC) return false;                    // fc00::/7 unique local
        if ((w0 & 0xFFC0) == 0xFE80) return false;                  // fe80::/10 link-local
        if ((w0 & 0xFFC0) == 0xFEC0) return false;                  // fec0::/10 site-local
        if ((b[0] & 0xFF) == 0xFF) return false;                    // ff00::/8 multicast
        if (w0 == 0x2001 && w1 == 0x0DB8) return false;             // documentation
        if (w0 == 0x2001 && w1 == 0x0000) return false;             // Teredo tunnelling
        if (w0 == 0x0100 && allZero(b, 2, 8)) return false;         // 100::/64 discard
        if (w0 == 0x0064 && w1 == 0xFF9B) {                         // 64:ff9b::/96 NAT64
            return isPublicV4(slice(b, 12));
        }
        if (w0 == 0x2002) {                                         // 2002::/16 6to4
            return isPublicV4(slice(b, 2));
        }
        return true;
    }

    private static boolean allZero(byte[] b, int from, int to) {
        for (int i = from; i < to; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static byte[] slice(byte[] b, int from) {
        return new byte[]{b[from], b[from + 1], b[from + 2], b[from + 3]};
    }
}
