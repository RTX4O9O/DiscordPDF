package me.rtx4090.discordpdf;

import me.rtx4090.discordpdf.bot.DiscordBot;
import me.rtx4090.discordpdf.cli.Cli;
import me.rtx4090.discordpdf.config.BotConfig;

import java.util.Arrays;

/**
 * Entry point.
 * <ul>
 *   <li>{@code java -jar discordpdf.jar} — run the Discord bot (needs {@code DISCORD_TOKEN})</li>
 *   <li>{@code java -jar discordpdf.jar cli <tool> ...} — run a tool locally, no Discord</li>
 * </ul>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        // Headless rendering (PDFBox / POI use Java2D); no display needed on servers.
        System.setProperty("java.awt.headless", "true");

        if (args.length > 0 && args[0].equals("cli")) {
            System.exit(Cli.run(Arrays.copyOfRange(args, 1, args.length)));
            return;
        }
        DiscordBot.start(BotConfig.fromEnv());
    }
}
