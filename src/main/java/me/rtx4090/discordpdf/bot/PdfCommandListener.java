package me.rtx4090.discordpdf.bot;

import me.rtx4090.discordpdf.config.BotConfig;
import me.rtx4090.discordpdf.core.I18n;
import me.rtx4090.discordpdf.core.InputSource;
import me.rtx4090.discordpdf.core.JobRunner;
import me.rtx4090.discordpdf.core.PdfTool;
import me.rtx4090.discordpdf.core.ToolOptions;
import me.rtx4090.discordpdf.core.UserFacingException;
import me.rtx4090.discordpdf.net.SafeDownloader;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Handles {@code /pdf ...}. The interaction is acknowledged immediately (deferred), the job
 * runs on a virtual thread behind a concurrency limit, and the result replaces the
 * "thinking" message. Logs contain only the tool name, outcome and duration — never file
 * names, URLs, file contents, user or server IDs.
 */
final class PdfCommandListener extends ListenerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(PdfCommandListener.class);

    private final BotConfig config;
    private final JobRunner runner;
    private final Map<String, PdfTool> tools;
    private final ExecutorService executor;
    private final Semaphore slots;
    private final UserCooldown cooldown;

    PdfCommandListener(BotConfig config, JobRunner runner, Map<String, PdfTool> tools, ExecutorService executor) {
        this.config = config;
        this.runner = runner;
        this.tools = tools;
        this.executor = executor;
        this.slots = new Semaphore(config.maxConcurrentJobs(), true);
        this.cooldown = new UserCooldown(config.userCooldown());
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!SlashCommands.COMMAND.equals(event.getName()) || event.getSubcommandName() == null) {
            return;
        }
        Locale locale = I18n.fromTag(event.getUserLocale().getLocale());
        String sub = event.getSubcommandName();

        if (sub.equals("help") || sub.equals("privacy")) {
            event.reply(I18n.get(locale, sub + ".text", I18n.size(config.maxInputBytes()),
                            I18n.size(config.maxOutputBytes()), config.maxPages()))
                    .setEphemeral(true).queue();
            return;
        }
        PdfTool tool = tools.get(sub);
        if (tool == null) {
            event.reply(I18n.get(locale, "error.generic")).setEphemeral(true).queue();
            return;
        }

        long wait = cooldown.tryAcquire(event.getUser().getIdLong());
        if (wait > 0) {
            event.reply("⏳ " + I18n.get(locale, "error.cooldown", wait)).setEphemeral(true).queue();
            return;
        }

        List<InputSource> sources;
        ToolOptions options;
        try {
            sources = collectSources(event);
            options = collectOptions(event);
        } catch (UserFacingException e) {
            event.reply("⚠️ " + I18n.get(locale, e.key(), e.args())).setEphemeral(true).queue();
            return;
        }

        OptionMapping publicOpt = event.getOption(SlashCommands.OPT_PUBLIC);
        boolean ephemeral = publicOpt != null ? !publicOpt.getAsBoolean() : config.defaultEphemeral();

        event.deferReply(ephemeral).queue(
                hook -> executor.execute(() -> runJob(hook, tool, sources, options, locale)),
                error -> LOG.warn("could not acknowledge interaction: {}", error.getClass().getSimpleName()));
    }

    private void runJob(InteractionHook hook, PdfTool tool, List<InputSource> sources,
                        ToolOptions options, Locale locale) {
        boolean acquired = false;
        long start = System.nanoTime();
        try {
            acquired = slots.tryAcquire();
            if (!acquired) {
                hook.editOriginal("🕒 " + I18n.get(locale, "status.queued")).queue();
                acquired = slots.tryAcquire(config.queueTimeout().toMillis(), TimeUnit.MILLISECONDS);
                if (!acquired) {
                    hook.editOriginal("⚠️ " + I18n.get(locale, "error.busy")).queue();
                    LOG.info("job tool={} status=busy", tool.id());
                    return;
                }
                hook.editOriginal("⚙️ " + I18n.get(locale, "status.working")).queue();
            }

            JobRunner.Output out = runner.run(tool, sources, options);
            String message = "✅ " + I18n.get(locale, out.messageKey(), out.messageArgs());
            hook.editOriginal(new MessageEditBuilder()
                            .setContent(message)
                            .setFiles(List.of(FileUpload.fromData(out.data(), out.fileName())))
                            .build())
                    .queue(ok -> { }, err -> LOG.warn("upload failed: {}", err.getClass().getSimpleName()));
            LOG.info("job tool={} status=ok ms={}", tool.id(), elapsedMs(start));
        } catch (UserFacingException e) {
            hook.editOriginal("⚠️ " + I18n.get(locale, e.key(), e.args())).queue();
            LOG.info("job tool={} status=rejected reason={} ms={}", tool.id(), e.key(), elapsedMs(start));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            hook.editOriginal("⚠️ " + I18n.get(locale, "error.generic")).queue();
        } catch (OutOfMemoryError e) {
            hook.editOriginal("⚠️ " + I18n.get(locale, "error.too_complex")).queue();
            LOG.error("job tool={} status=oom ms={}", tool.id(), elapsedMs(start));
        } catch (Exception | LinkageError e) {
            hook.editOriginal("⚠️ " + I18n.get(locale, "error.generic")).queue();
            logFailure(tool, e, start);
        } finally {
            if (acquired) {
                slots.release();
            }
        }
    }

    /** Attachments first (file, file1..file10, image1..image10), then pasted URLs. */
    private List<InputSource> collectSources(SlashCommandInteractionEvent event) {
        List<InputSource> sources = new ArrayList<>();
        for (String name : SlashCommands.ATTACHMENT_OPTIONS) {
            OptionMapping m = event.getOption(name);
            if (m != null && m.getType() == OptionType.ATTACHMENT) {
                Message.Attachment a = m.getAsAttachment();
                sources.add(new InputSource.Remote(SafeDownloader.parse(a.getUrl()), a.getFileName(), a.getSize()));
            }
        }
        for (String name : List.of(SlashCommands.OPT_URL, SlashCommands.OPT_URLS)) {
            OptionMapping m = event.getOption(name);
            if (m == null) {
                continue;
            }
            for (String raw : m.getAsString().trim().split("[\\s,]+")) {
                if (!raw.isBlank()) {
                    sources.add(InputSource.Remote.link(raw));
                }
            }
        }
        if (sources.size() > config.maxInputsPerJob()) {
            throw new UserFacingException("error.too_many_inputs", config.maxInputsPerJob());
        }
        return sources;
    }

    private static ToolOptions collectOptions(SlashCommandInteractionEvent event) {
        Map<String, Object> values = new HashMap<>();
        for (OptionMapping m : event.getOptions()) {
            switch (m.getType()) {
                case STRING -> values.put(m.getName(), m.getAsString());
                case INTEGER -> values.put(m.getName(), m.getAsLong());
                case BOOLEAN -> values.put(m.getName(), m.getAsBoolean());
                default -> {
                    // attachments are handled as sources
                }
            }
        }
        return new ToolOptions(values);
    }

    private void logFailure(PdfTool tool, Throwable e, long start) {
        if (config.logErrorDetails()) {
            LOG.error("job tool={} status=error ms={}", tool.id(), elapsedMs(start), e);
            return;
        }
        // Exception messages can contain file names or text from the document, so by default
        // only the exception type and the location in our code are logged.
        StackTraceElement where = null;
        for (StackTraceElement el : e.getStackTrace()) {
            if (el.getClassName().startsWith("io.github.rtx4o9o.discordpdf")) {
                where = el;
                break;
            }
        }
        LOG.error("job tool={} status=error type={} at={} ms={}", tool.id(), e.getClass().getName(),
                where == null ? "?" : where.getClassName() + ":" + where.getLineNumber(), elapsedMs(start));
    }

    private static long elapsedMs(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
