package me.rtx4090.discordpdf.core;

import me.rtx4090.discordpdf.config.BotConfig;
import me.rtx4090.discordpdf.convert.HtmlPdfRenderer;
import me.rtx4090.discordpdf.convert.LibreOffice;
import me.rtx4090.discordpdf.net.SafeDownloader;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs one job end to end: create a private workspace, fetch the inputs, run the tool,
 * load the single output into memory, and delete the workspace before returning.
 * By the time the result is uploaded to Discord, no copy of any file remains on disk.
 */
public final class JobRunner {

    /** The finished output, held only in memory. */
    public record Output(byte[] data, String fileName, String messageKey, Object[] messageArgs) {
    }

    private final BotConfig config;
    private final SafeDownloader downloader;
    private final LibreOffice office;
    private final HtmlPdfRenderer html;

    public JobRunner(BotConfig config, SafeDownloader downloader, LibreOffice office, HtmlPdfRenderer html) {
        this.config = config;
        this.downloader = downloader;
        this.office = office;
        this.html = html;
    }

    public LibreOffice office() {
        return office;
    }

    public Output run(PdfTool tool, List<InputSource> sources, ToolOptions options) throws Exception {
        int max = Math.min(tool.maxInputs(), config.maxInputsPerJob());
        if (sources.size() > max) {
            throw new UserFacingException("error.too_many_inputs", max);
        }
        if (sources.size() < tool.minInputs()) {
            throw new UserFacingException(tool.minInputs() > 1 ? "error.need_more_inputs" : "error.no_input",
                    tool.minInputs());
        }
        try (Workspace ws = Workspace.create(config.workDir())) {
            List<InputFile> inputs = new ArrayList<>();
            for (InputSource s : sources) {
                inputs.add(s.fetch(ws, downloader, config.maxInputBytes()));
            }
            ToolResult result = tool.run(new ToolContext(inputs, options, ws, config, office, html));
            long size = Files.size(result.file());
            if (size > config.maxOutputBytes()) {
                throw new UserFacingException("error.output_too_large", I18n.size(size),
                        config.maxOutputBytes() / (1024 * 1024));
            }
            byte[] data = Files.readAllBytes(result.file());
            return new Output(data, result.fileName(), result.messageKey(), result.messageArgs());
        }
    }
}
