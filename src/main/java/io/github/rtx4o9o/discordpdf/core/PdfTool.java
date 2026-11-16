package io.github.rtx4o9o.discordpdf.core;

/**
 * One conversion tool. Implementations must not keep any state between calls,
 * must only write inside {@link ToolContext#workspace()} and must not log user data.
 */
public interface PdfTool {

    /** Sub-command name, e.g. {@code merge}. */
    String id();

    /** Minimum number of input files (0 when the tool can work from a text option alone). */
    default int minInputs() {
        return 1;
    }

    /** Maximum number of input files. */
    default int maxInputs() {
        return 1;
    }

    ToolResult run(ToolContext ctx) throws Exception;
}
