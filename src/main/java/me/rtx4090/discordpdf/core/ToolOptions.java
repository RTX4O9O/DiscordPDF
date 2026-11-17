package me.rtx4090.discordpdf.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Typed access to the options a user passed to a tool. Independent of Discord. */
public final class ToolOptions {

    private final Map<String, Object> values;

    public ToolOptions(Map<String, ?> values) {
        this.values = new HashMap<>(values);
    }

    public static ToolOptions empty() {
        return new ToolOptions(Map.of());
    }

    public Optional<String> string(String name) {
        Object v = values.get(name);
        if (v == null) {
            return Optional.empty();
        }
        String s = v.toString().trim();
        return s.isEmpty() ? Optional.empty() : Optional.of(s);
    }

    /** Same as {@link #string(String)} but keeps leading/trailing whitespace (for pasted text). */
    public Optional<String> rawString(String name) {
        Object v = values.get(name);
        return v == null ? Optional.empty() : Optional.of(v.toString());
    }

    public Optional<Long> integer(String name) {
        Object v = values.get(name);
        if (v instanceof Number n) {
            return Optional.of(n.longValue());
        }
        if (v != null) {
            try {
                return Optional.of(Long.parseLong(v.toString().trim()));
            } catch (NumberFormatException e) {
                throw new UserFacingException("error.invalid_number", name);
            }
        }
        return Optional.empty();
    }

    public long integer(String name, long def, long min, long max) {
        long v = integer(name).orElse(def);
        return Math.max(min, Math.min(max, v));
    }

    public boolean bool(String name, boolean def) {
        Object v = values.get(name);
        if (v instanceof Boolean b) {
            return b;
        }
        return v == null ? def : Boolean.parseBoolean(v.toString().trim());
    }

    public String choice(String name, String def) {
        return string(name).map(s -> s.toLowerCase(Locale.ROOT)).orElse(def);
    }
}
