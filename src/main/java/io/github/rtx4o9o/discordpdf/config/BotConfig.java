package io.github.rtx4o9o.discordpdf.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime configuration, read from environment variables only.
 * Nothing here is ever written to disk by the bot.
 */
public record BotConfig(
        String discordToken,
        Optional<Long> devGuildId,
        long maxInputBytes,
        long maxOutputBytes,
        int maxPages,
        int maxInputsPerJob,
        int maxConcurrentJobs,
        Duration queueTimeout,
        Duration userCooldown,
        Duration downloadTimeout,
        Path workDir,
        Optional<Path> sofficePath,
        Duration officeTimeout,
        String officeCommandPrefix,
        Optional<Path> fontCjk,
        boolean defaultEphemeral,
        boolean logErrorDetails
) {

    private static final long MB = 1024L * 1024L;

    public static BotConfig fromEnv() {
        return from(System.getenv(), true);
    }

    /** Configuration for the offline CLI: no Discord token needed. */
    public static BotConfig forCli() {
        return from(System.getenv(), false);
    }

    static BotConfig from(Map<String, String> env, boolean requireToken) {
        String token = env.getOrDefault("DISCORD_TOKEN", "").trim();
        if (requireToken && token.isEmpty()) {
            throw new IllegalStateException("DISCORD_TOKEN is not set");
        }
        return new BotConfig(
                token,
                optLong(env, "DEV_GUILD_ID"),
                getLong(env, "MAX_INPUT_MB", 50) * MB,
                getLong(env, "MAX_OUTPUT_MB", 10) * MB,
                (int) getLong(env, "MAX_PAGES", 500),
                (int) clamp(getLong(env, "MAX_INPUTS_PER_JOB", 10), 1, 10),
                (int) clamp(getLong(env, "MAX_CONCURRENT_JOBS", 2), 1, 64),
                Duration.ofSeconds(getLong(env, "QUEUE_TIMEOUT_SECONDS", 120)),
                Duration.ofSeconds(getLong(env, "USER_COOLDOWN_SECONDS", 5)),
                Duration.ofSeconds(getLong(env, "DOWNLOAD_TIMEOUT_SECONDS", 60)),
                Path.of(env.getOrDefault("WORK_DIR",
                        Path.of(System.getProperty("java.io.tmpdir"), "discordpdf").toString())),
                optPath(env, "SOFFICE_PATH"),
                Duration.ofSeconds(getLong(env, "OFFICE_TIMEOUT_SECONDS", 180)),
                env.getOrDefault("OFFICE_COMMAND_PREFIX", "").trim(),
                optPath(env, "FONT_CJK"),
                Boolean.parseBoolean(env.getOrDefault("DEFAULT_EPHEMERAL", "true")),
                Boolean.parseBoolean(env.getOrDefault("LOG_ERROR_DETAILS", "false"))
        );
    }

    private static long getLong(Map<String, String> env, String key, long def) {
        String v = env.get(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(key + " must be a number");
        }
    }

    private static Optional<Long> optLong(Map<String, String> env, String key) {
        String v = env.get(key);
        if (v == null || v.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(getLong(env, key, 0));
    }

    private static Optional<Path> optPath(Map<String, String> env, String key) {
        String v = env.get(key);
        return (v == null || v.isBlank()) ? Optional.empty() : Optional.of(Path.of(v.trim()));
    }

    private static long clamp(long v, long min, long max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public String toString() {
        // Never print the token.
        return "BotConfig[maxInputMB=" + maxInputBytes / MB + ", maxOutputMB=" + maxOutputBytes / MB
                + ", maxPages=" + maxPages + ", maxConcurrentJobs=" + maxConcurrentJobs
                + ", workDir=" + workDir + ", soffice=" + sofficePath.map(Path::toString).orElse("auto") + "]";
    }
}
