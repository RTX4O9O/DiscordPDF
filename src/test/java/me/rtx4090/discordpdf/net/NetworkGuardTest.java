package me.rtx4090.discordpdf.net;

import me.rtx4090.discordpdf.core.UserFacingException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkGuardTest {

    private static boolean pub(String literal) throws Exception {
        return NetworkGuard.isPublic(InetAddress.getByName(literal));
    }

    @Test
    void blocksPrivateAndSpecialIpv4() throws Exception {
        for (String ip : new String[]{"127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.1",
                "169.254.169.254", "100.64.0.1", "0.0.0.0", "0.1.2.3", "224.0.0.1", "255.255.255.255",
                "198.18.0.1", "192.0.2.10", "203.0.113.5"}) {
            assertFalse(pub(ip), ip);
        }
    }

    @Test
    void allowsPublicIpv4() throws Exception {
        for (String ip : new String[]{"1.1.1.1", "8.8.8.8", "162.159.128.233", "172.32.0.1", "100.128.0.1"}) {
            assertTrue(pub(ip), ip);
        }
    }

    @Test
    void blocksPrivateAndTunnelledIpv6() throws Exception {
        for (String ip : new String[]{"::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "ff02::1",
                "::ffff:127.0.0.1", "::ffff:10.0.0.1", "64:ff9b::a00:1", "2002:a00:1::", "2001:db8::1",
                "2001:0::1", "::127.0.0.1"}) {
            assertFalse(pub(ip), ip);
        }
    }

    @Test
    void allowsPublicIpv6() throws Exception {
        for (String ip : new String[]{"2606:4700:4700::1111", "2001:4860:4860::8888", "64:ff9b::808:808",
                "2002:808:808::"}) {
            assertTrue(pub(ip), ip);
        }
    }

    @Test
    void rejectsBadSchemesCredentialsAndPrivateLiterals() {
        assertEquals("error.url_invalid", assertThrows(UserFacingException.class,
                () -> NetworkGuard.checkUri(URI.create("file:///etc/passwd"))).key());
        assertEquals("error.url_invalid", assertThrows(UserFacingException.class,
                () -> NetworkGuard.checkUri(URI.create("ftp://example.com/a.pdf"))).key());
        assertEquals("error.url_invalid", assertThrows(UserFacingException.class,
                () -> NetworkGuard.checkUri(URI.create("https://user:pw@example.com/a.pdf"))).key());
        assertEquals("error.url_blocked", assertThrows(UserFacingException.class,
                () -> NetworkGuard.checkUri(URI.create("http://127.0.0.1:8080/x"))).key());
        assertEquals("error.url_blocked", assertThrows(UserFacingException.class,
                () -> NetworkGuard.checkUri(URI.create("http://[::1]/x"))).key());
        assertEquals("error.url_blocked", assertThrows(UserFacingException.class,
                () -> NetworkGuard.checkUri(URI.create("http://169.254.169.254/latest/meta-data/"))).key());
        assertEquals("error.url_blocked", assertThrows(UserFacingException.class,
                () -> NetworkGuard.checkUri(URI.create("http://localhost/x"))).key());
    }
}
