package io.github.rtx4o9o.discordpdf.core;

import java.nio.file.Path;

/**
 * The single file a tool hands back (several outputs are always zipped first),
 * plus an i18n message describing the result.
 */
public record ToolResult(Path file, String fileName, String messageKey, Object[] messageArgs) {

    public static ToolResult of(Path file, String fileName, String messageKey, Object... args) {
        return new ToolResult(file, fileName, messageKey, args);
    }
}
