package me.rtx4090.discordpdf.bot;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user rate limit kept only in memory. User IDs are never stored as-is: they are hashed
 * with a random salt generated at start-up, so even a memory dump cannot be linked back to
 * Discord accounts, and everything is forgotten on restart.
 */
final class UserCooldown {

    private final long cooldownNanos;
    private final byte[] salt = new byte[32];
    private final Map<String, Long> lastUse = new ConcurrentHashMap<>();

    UserCooldown(Duration cooldown) {
        this.cooldownNanos = cooldown.toNanos();
        new SecureRandom().nextBytes(salt);
    }

    /** Returns 0 if the user may start a job now, otherwise the seconds left to wait. */
    long tryAcquire(long userId) {
        if (cooldownNanos <= 0) {
            return 0;
        }
        long now = System.nanoTime();
        String key = hash(userId);
        long[] wait = {0};
        lastUse.compute(key, (k, last) -> {
            if (last != null && now - last < cooldownNanos) {
                wait[0] = Math.max(1, Duration.ofNanos(cooldownNanos - (now - last)).toSeconds());
                return last;
            }
            return now;
        });
        if (lastUse.size() > 10_000) {
            lastUse.values().removeIf(t -> now - t > cooldownNanos);
        }
        return wait[0];
    }

    private String hash(long userId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(ByteBuffer.allocate(Long.BYTES).putLong(userId).array());
            return Base64.getEncoder().withoutPadding().encodeToString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
