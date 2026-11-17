package me.rtx4090.discordpdf.core;

import java.nio.file.Path;

/**
 * A downloaded input inside the job workspace.
 *
 * @param path        location inside the {@link Workspace}
 * @param displayName name used only to build the output file name sent back to the same user
 * @param kind        type detected from the content
 * @param size        size in bytes
 */
public record InputFile(Path path, String displayName, FileKind kind, long size) {

    /** File name without extension, made safe for use in an output file name. */
    public String baseName() {
        return Names.safeBaseName(displayName);
    }
}
