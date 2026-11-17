package me.rtx4090.discordpdf.core;

import java.text.Normalizer;

/** Helpers for building output file names. */
public final class Names {

    private static final int MAX_BASE = 60;

    private Names() {
    }

    /**
     * Strips the extension and any character that is not a letter, digit, '-', '_' or '.',
     * so a hostile name cannot inject paths or control characters. Falls back to "document".
     */
    public static String safeBaseName(String name) {
        if (name == null || name.isBlank()) {
            return "document";
        }
        String base = name;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        base = base.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = Normalizer.normalize(base, Normalizer.Form.NFC);
        StringBuilder sb = new StringBuilder();
        base.codePoints().forEach(cp -> {
            if (Character.isLetterOrDigit(cp) || cp == '-' || cp == '_') {
                sb.appendCodePoint(cp);
            } else if (Character.isWhitespace(cp) || cp == '.') {
                sb.append('_');
            }
        });
        String out = sb.toString().replaceAll("_+", "_").replaceAll("^_|_$", "");
        if (out.isEmpty()) {
            return "document";
        }
        if (out.codePointCount(0, out.length()) > MAX_BASE) {
            out = out.substring(0, out.offsetByCodePoints(0, MAX_BASE));
        }
        return out;
    }
}
