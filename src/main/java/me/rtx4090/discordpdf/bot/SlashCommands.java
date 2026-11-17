package me.rtx4090.discordpdf.bot;

import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.ArrayList;
import java.util.List;

/**
 * Definition of the {@code /pdf} slash command and its sub-commands.
 * Descriptions are English by default with Traditional Chinese (zh-TW) localisations.
 *
 * <p>Every file tool accepts its input either as an uploaded attachment or as a URL.
 */
final class SlashCommands {

    static final String COMMAND = "pdf";
    static final String OPT_PUBLIC = "public";
    static final String OPT_URL = "url";
    static final String OPT_URLS = "urls";
    static final String OPT_FILE = "file";

    /** Attachment option names, in the order their files are used. */
    static final List<String> ATTACHMENT_OPTIONS;

    static {
        List<String> names = new ArrayList<>();
        names.add(OPT_FILE);
        for (int i = 1; i <= 10; i++) {
            names.add("file" + i);
        }
        for (int i = 1; i <= 10; i++) {
            names.add("image" + i);
        }
        ATTACHMENT_OPTIONS = List.copyOf(names);
    }

    private static final DiscordLocale ZH = DiscordLocale.CHINESE_TAIWAN;

    private SlashCommands() {
    }

    /**
     * @param global true for a global command (usable in servers, the bot DM and, when the
     *               app is user-installed, in any DM); false for a single test server
     */
    static SlashCommandData build(boolean global) {
        SlashCommandData cmd = Commands.slash(COMMAND, "Privacy-first PDF tools: merge, split, compress, convert")
                .setDescriptionLocalization(ZH, "隱私優先的 PDF 工具：合併、拆分、壓縮、轉檔");
        cmd.addSubcommands(
                merge(),
                singleFile("split", "Split a PDF by page ranges or every N pages", "依頁碼範圍或每 N 頁拆分 PDF")
                        .addOptions(
                                opt(OptionType.STRING, "ranges", "Page ranges, e.g. 1-3,5,8- (default: every page)",
                                        "頁碼範圍，例如 1-3,5,8-（預設：每頁一份）").setMaxLength(200),
                                opt(OptionType.INTEGER, "every", "Split into chunks of N pages", "每 N 頁拆成一份")
                                        .setRequiredRange(1, 10_000),
                                opt(OptionType.STRING, "mode", "With ranges: one file per range, or one file with the selected pages",
                                        "搭配範圍：每個範圍一個檔案，或全部選取頁合成一個檔案")
                                        .addChoice("separate · 每個範圍一個檔案", "separate")
                                        .addChoice("extract · 擷取成單一檔案", "extract"))
                        .addOptions(publicOption()),
                singleFile("compress", "Reduce PDF file size", "壓縮 PDF 檔案大小")
                        .addOptions(
                                opt(OptionType.STRING, "level", "Compression level (default: medium)", "壓縮程度（預設：中）")
                                        .addChoice("low · 低（畫質最好）", "low")
                                        .addChoice("medium · 中（建議）", "medium")
                                        .addChoice("high · 高（檔案最小）", "high"))
                        .addOptions(publicOption()),
                singleFile("pdf2md", "PDF to Markdown", "PDF 轉 Markdown")
                        .addOptions(
                                opt(OptionType.BOOLEAN, "page_breaks", "Mark page boundaries in the output",
                                        "在輸出中標示分頁位置"))
                        .addOptions(publicOption()),
                singleFile("md2pdf", "Markdown to PDF (file, URL or pasted text)", "Markdown 轉 PDF（檔案、網址或直接貼上文字）")
                        .addOptions(
                                opt(OptionType.STRING, "text", "Markdown text; type \\n for a new line",
                                        "Markdown 文字；換行請輸入 \\n").setMaxLength(6000))
                        .addOptions(publicOption()),
                singleFile("pdf2word", "PDF to Word (.docx / .doc)", "PDF 轉 Word（.docx / .doc）")
                        .addOptions(
                                opt(OptionType.STRING, "format", "Output format (default: docx)", "輸出格式（預設：docx）")
                                        .addChoice("docx", "docx")
                                        .addChoice("doc", "doc"),
                                opt(OptionType.STRING, "mode", "editable = editable text, layout = exact look as pictures",
                                        "editable＝可編輯文字，layout＝以圖片保留原始版面")
                                        .addChoice("editable · 可編輯", "editable")
                                        .addChoice("layout · 保留版面", "layout"))
                        .addOptions(publicOption()),
                singleFile("pdf2ppt", "PDF to PowerPoint (.pptx / .ppt)", "PDF 轉 PowerPoint（.pptx / .ppt）")
                        .addOptions(
                                opt(OptionType.STRING, "format", "Output format (default: pptx)", "輸出格式（預設：pptx）")
                                        .addChoice("pptx", "pptx")
                                        .addChoice("ppt", "ppt"))
                        .addOptions(publicOption()),
                singleFile("word2pdf", "Word (.docx / .doc) to PDF", "Word（.docx / .doc）轉 PDF")
                        .addOptions(publicOption()),
                singleFile("ppt2pdf", "PowerPoint (.pptx / .ppt) to PDF", "PowerPoint（.pptx / .ppt）轉 PDF")
                        .addOptions(publicOption()),
                singleFile("pdf2jpg", "PDF pages to JPG images", "PDF 頁面轉 JPG 圖片")
                        .addOptions(
                                opt(OptionType.INTEGER, "dpi", "Resolution 72-300 (default 150)", "解析度 72–300（預設 150）")
                                        .setRequiredRange(72, 300),
                                opt(OptionType.STRING, "pages", "Pages to convert, e.g. 1-3,5 (default: all)",
                                        "要轉換的頁面，例如 1-3,5（預設：全部）").setMaxLength(200))
                        .addOptions(publicOption()),
                jpg2pdf(),
                sub("help", "How to use discordpdf", "使用說明"),
                sub("privacy", "What happens to your files", "你的檔案會被怎麼處理"));

        if (global) {
            cmd.setIntegrationTypes(IntegrationType.GUILD_INSTALL, IntegrationType.USER_INSTALL);
            cmd.setContexts(InteractionContextType.GUILD, InteractionContextType.BOT_DM,
                    InteractionContextType.PRIVATE_CHANNEL);
        }
        return cmd;
    }

    private static SubcommandData merge() {
        SubcommandData s = sub("merge", "Merge up to 10 PDFs in order", "依序合併最多 10 個 PDF");
        for (int i = 1; i <= 10; i++) {
            s.addOptions(opt(OptionType.ATTACHMENT, "file" + i, "PDF #" + i, "第 " + i + " 個 PDF"));
        }
        s.addOptions(opt(OptionType.STRING, OPT_URLS, "PDF links, separated by spaces (used after the uploads)",
                "PDF 網址，以空白分隔（排在上傳檔案之後）").setMaxLength(4000));
        s.addOptions(publicOption());
        return s;
    }

    private static SubcommandData jpg2pdf() {
        SubcommandData s = sub("jpg2pdf", "Images (JPG/PNG) to one PDF, one image per page",
                "圖片（JPG/PNG）合成一個 PDF，每頁一張");
        for (int i = 1; i <= 10; i++) {
            s.addOptions(opt(OptionType.ATTACHMENT, "image" + i, "Image #" + i, "第 " + i + " 張圖片"));
        }
        s.addOptions(
                opt(OptionType.STRING, OPT_URLS, "Image links, separated by spaces (used after the uploads)",
                        "圖片網址，以空白分隔（排在上傳檔案之後）").setMaxLength(4000),
                opt(OptionType.STRING, "page_size", "Page size (default: A4)", "頁面大小（預設：A4）")
                        .addChoice("A4", "a4")
                        .addChoice("Letter", "letter")
                        .addChoice("fit · 與圖片相同", "fit"));
        s.addOptions(publicOption());
        return s;
    }

    /** A sub-command that takes one file, as an upload or a URL. */
    private static SubcommandData singleFile(String name, String en, String zh) {
        return sub(name, en, zh).addOptions(
                opt(OptionType.ATTACHMENT, OPT_FILE, "File to convert (upload)", "要處理的檔案（上傳）"),
                opt(OptionType.STRING, OPT_URL, "...or a link to the file", "……或檔案的網址").setMaxLength(2000));
    }

    private static OptionData publicOption() {
        return opt(OptionType.BOOLEAN, OPT_PUBLIC, "Post the result visibly in the channel (default: only you)",
                "將結果公開貼在頻道（預設：只有你看得到）");
    }

    private static SubcommandData sub(String name, String en, String zh) {
        return new SubcommandData(name, en).setDescriptionLocalization(ZH, zh);
    }

    private static OptionData opt(OptionType type, String name, String en, String zh) {
        return new OptionData(type, name, en, false).setDescriptionLocalization(ZH, zh);
    }
}
