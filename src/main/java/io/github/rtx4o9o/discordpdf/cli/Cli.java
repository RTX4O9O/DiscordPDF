package io.github.rtx4o9o.discordpdf.cli;

import io.github.rtx4o9o.discordpdf.config.BotConfig;
import io.github.rtx4o9o.discordpdf.convert.HtmlPdfRenderer;
import io.github.rtx4o9o.discordpdf.convert.LibreOffice;
import io.github.rtx4o9o.discordpdf.core.I18n;
import io.github.rtx4o9o.discordpdf.core.InputSource;
import io.github.rtx4o9o.discordpdf.core.JobRunner;
import io.github.rtx4o9o.discordpdf.core.PdfTool;
import io.github.rtx4o9o.discordpdf.core.ToolOptions;
import io.github.rtx4o9o.discordpdf.core.UserFacingException;
import io.github.rtx4o9o.discordpdf.core.Workspace;
import io.github.rtx4o9o.discordpdf.net.SafeDownloader;
import io.github.rtx4o9o.discordpdf.tools.Tools;
import io.github.rtx4o9o.discordpdf.util.FontLocator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline command line, handy for testing the tools without Discord:
 * <pre>
 *   java -jar discordpdf.jar cli merge a.pdf b.pdf
 *   java -jar discordpdf.jar cli split in.pdf ranges=1-3,5 mode=extract
 *   java -jar discordpdf.jar cli md2pdf README.md -o out/
 *   java -jar discordpdf.jar cli compress https://example.com/file.pdf level=high
 * </pre>
 * Arguments containing '=' are options; http(s) arguments are downloaded with the same
 * protections as the bot; everything else is a local file.
 */
public final class Cli {

    private Cli() {
    }

    public static int run(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: cli <tool> [inputs...] [key=value...] [-o outDir]");
            System.err.println("tools: " + String.join(", ", Tools.all().keySet()));
            return 2;
        }
        Map<String, PdfTool> tools = Tools.all();
        PdfTool tool = tools.get(args[0]);
        if (tool == null) {
            System.err.println("unknown tool: " + args[0] + " (available: " + String.join(", ", tools.keySet()) + ")");
            return 2;
        }
        Path outDir = Path.of(".");
        List<InputSource> sources = new ArrayList<>();
        Map<String, Object> options = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.equals("-o") && i + 1 < args.length) {
                outDir = Path.of(args[++i]);
            } else if (a.startsWith("http://") || a.startsWith("https://")) {
                sources.add(InputSource.Remote.link(a));
            } else if (a.contains("=") && !Files.exists(Path.of(a))) {
                int eq = a.indexOf('=');
                options.put(a.substring(0, eq), a.substring(eq + 1));
            } else {
                sources.add(new InputSource.Local(Path.of(a)));
            }
        }

        BotConfig config = BotConfig.forCli();
        Workspace.redirectJvmTemp(config.workDir());
        LibreOffice office = new LibreOffice(config.sofficePath(), config.officeTimeout(), config.officeCommandPrefix());
        JobRunner runner = new JobRunner(config, new SafeDownloader(config.downloadTimeout()), office,
                new HtmlPdfRenderer(FontLocator.locate(config.fontCjk())));
        Locale locale = Locale.getDefault().getLanguage().equals("zh") ? Locale.TRADITIONAL_CHINESE : Locale.ENGLISH;
        try {
            JobRunner.Output out = runner.run(tool, sources, new ToolOptions(options));
            Files.createDirectories(outDir);
            Path target = outDir.resolve(out.fileName());
            Files.write(target, out.data());
            System.out.println(I18n.get(locale, out.messageKey(), out.messageArgs()));
            System.out.println("-> " + target.toAbsolutePath());
            return 0;
        } catch (UserFacingException e) {
            System.err.println(I18n.get(locale, e.key(), e.args()));
            return 1;
        }
    }
}
