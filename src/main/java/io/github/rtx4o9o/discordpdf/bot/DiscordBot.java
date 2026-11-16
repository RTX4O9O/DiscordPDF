package io.github.rtx4o9o.discordpdf.bot;

import io.github.rtx4o9o.discordpdf.config.BotConfig;
import io.github.rtx4o9o.discordpdf.convert.HtmlPdfRenderer;
import io.github.rtx4o9o.discordpdf.convert.LibreOffice;
import io.github.rtx4o9o.discordpdf.core.JobRunner;
import io.github.rtx4o9o.discordpdf.core.Workspace;
import io.github.rtx4o9o.discordpdf.net.SafeDownloader;
import io.github.rtx4o9o.discordpdf.tools.Tools;
import io.github.rtx4o9o.discordpdf.util.FontLocator;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Starts the bot. It connects with no privileged intents at all: it never reads message
 * content, member lists or presences — it only receives the slash commands sent to it.
 */
public final class DiscordBot {

    private static final Logger LOG = LoggerFactory.getLogger(DiscordBot.class);

    private DiscordBot() {
    }

    public static void start(BotConfig config) throws InterruptedException, IOException {
        Workspace.purgeStale(config.workDir());
        Workspace.redirectJvmTemp(config.workDir());

        LibreOffice office = new LibreOffice(config.sofficePath(), config.officeTimeout(), config.officeCommandPrefix());
        FontLocator.Fonts fonts = FontLocator.locate(config.fontCjk());
        JobRunner runner = new JobRunner(config, new SafeDownloader(config.downloadTimeout()), office,
                new HtmlPdfRenderer(fonts));
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        JDA jda = JDABuilder.createLight(config.discordToken(), EnumSet.noneOf(GatewayIntent.class))
                .addEventListeners(new PdfCommandListener(config, runner, Tools.all(), executor))
                .setActivity(Activity.customStatus("/pdf help"))
                .build();
        jda.awaitReady();

        if (config.devGuildId().isPresent()) {
            Guild guild = jda.getGuildById(config.devGuildId().get());
            if (guild == null) {
                LOG.warn("DEV_GUILD_ID is set but the bot is not in that server; registering globally instead");
                jda.updateCommands().addCommands(SlashCommands.build(true)).queue();
            } else {
                guild.updateCommands().addCommands(SlashCommands.build(false)).queue();
            }
        } else {
            jda.updateCommands().addCommands(SlashCommands.build(true)).queue();
        }

        LOG.info("discordpdf ready ({}); LibreOffice={}, CJK font={}", config,
                office.available() ? "found" : "missing (fallbacks in use)",
                fonts.cjk().isPresent() ? "found" : "missing (CJK text in Markdown->PDF will not render)");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            jda.shutdown();
            executor.shutdownNow();
            Workspace.purgeStale(config.workDir());
        }, "discordpdf-shutdown"));
    }
}
