package io.github.rtx4o9o.discordpdf.core;

import io.github.rtx4o9o.discordpdf.config.BotConfig;
import io.github.rtx4o9o.discordpdf.convert.HtmlPdfRenderer;
import io.github.rtx4o9o.discordpdf.convert.LibreOffice;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Everything a tool needs for one job. */
public record ToolContext(
        List<InputFile> inputs,
        ToolOptions options,
        Workspace workspace,
        BotConfig config,
        LibreOffice office,
        HtmlPdfRenderer html
) {

    /** The only input, checked against the accepted kinds. */
    public InputFile single(String expectedKey, FileKind... accepted) {
        if (inputs.isEmpty()) {
            throw new UserFacingException("error.no_input");
        }
        if (inputs.size() != 1) {
            throw new UserFacingException("error.need_one_input");
        }
        InputFile in = inputs.get(0);
        requireKind(in, expectedKey, accepted);
        return in;
    }

    /** All inputs, each checked against the accepted kinds. */
    public List<InputFile> all(String expectedKey, FileKind... accepted) {
        for (InputFile in : inputs) {
            requireKind(in, expectedKey, accepted);
        }
        return inputs;
    }

    private static void requireKind(InputFile in, String expectedKey, FileKind... accepted) {
        Set<FileKind> ok = accepted.length == 0 ? EnumSet.allOf(FileKind.class) : EnumSet.copyOf(Arrays.asList(accepted));
        if (!ok.contains(in.kind())) {
            throw new UserFacingException("error.wrong_type", in.displayName(), "@" + expectedKey);
        }
    }
}
