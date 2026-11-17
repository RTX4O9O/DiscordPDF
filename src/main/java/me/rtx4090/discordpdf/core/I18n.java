package me.rtx4090.discordpdf.core;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Message lookup. Bundles live in {@code src/main/resources/i18n/messages*.properties}
 * (UTF-8). Arguments starting with {@code @} are themselves treated as keys.
 */
public final class I18n {

    private static final String BUNDLE = "i18n.messages";

    private I18n() {
    }

    public static String get(Locale locale, String key, Object... args) {
        // No fallback to the JVM default locale: English is the base bundle.
        // Note: avoid apostrophes in messages, MessageFormat treats ' as a quote character.
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, locale,
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        String pattern;
        try {
            pattern = bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        Object[] resolved = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a instanceof String s && s.startsWith("@")) {
                resolved[i] = get(locale, s.substring(1));
            } else {
                resolved[i] = a;
            }
        }
        return new MessageFormat(pattern, locale).format(resolved);
    }

    /** Maps a Discord locale tag (e.g. "zh-TW") to a supported Java locale. */
    public static Locale fromTag(String tag) {
        if (tag == null) {
            return Locale.ENGLISH;
        }
        String t = tag.toLowerCase(Locale.ROOT);
        if (t.startsWith("zh")) {
            return Locale.TRADITIONAL_CHINESE;
        }
        return Locale.ENGLISH;
    }

    /** Formats a byte count as "1.2 MB". */
    public static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
