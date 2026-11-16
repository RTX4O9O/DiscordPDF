package io.github.rtx4o9o.discordpdf.tools;

import io.github.rtx4o9o.discordpdf.convert.MarkdownHtml;
import io.github.rtx4o9o.discordpdf.core.FileKind;
import io.github.rtx4o9o.discordpdf.core.InputFile;
import io.github.rtx4o9o.discordpdf.core.PdfTool;
import io.github.rtx4o9o.discordpdf.core.ToolContext;
import io.github.rtx4o9o.discordpdf.core.ToolResult;
import io.github.rtx4o9o.discordpdf.core.UserFacingException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Markdown -> PDF. Input is a .md file / URL, or Markdown pasted into the {@code text} option.
 * Remote images are not fetched (they are replaced by their alt text) so rendering a document
 * can never be used to make the bot request arbitrary URLs.
 */
public final class MarkdownToPdfTool implements PdfTool {

    private static final long MAX_MARKDOWN_BYTES = 5L * 1024 * 1024;

    @Override
    public String id() {
        return "md2pdf";
    }

    @Override
    public int minInputs() {
        return 0; // the text option is enough
    }

    @Override
    public ToolResult run(ToolContext ctx) throws Exception {
        Optional<String> pasted = ctx.options().rawString("text").filter(s -> !s.isBlank());
        String markdown;
        String baseName;
        if (pasted.isPresent()) {
            if (!ctx.inputs().isEmpty()) {
                throw new UserFacingException("error.text_and_file");
            }
            markdown = pasted.get().replace("\\n", "\n");
            baseName = "markdown";
        } else {
            InputFile in = ctx.single("kind.markdown", FileKind.TEXT);
            if (in.size() > MAX_MARKDOWN_BYTES) {
                throw new UserFacingException("error.input_too_large", MAX_MARKDOWN_BYTES / (1024 * 1024));
            }
            markdown = Files.readString(in.path(), StandardCharsets.UTF_8);
            baseName = in.baseName();
        }
        if (markdown.isBlank()) {
            throw new UserFacingException("error.markdown_empty");
        }
        String html = MarkdownHtml.toHtml(markdown);
        Path target = ctx.workspace().newFile(".pdf");
        ctx.html().render(html, target);
        String key = ctx.html().hasCjkFont() ? "result.md2pdf" : "result.md2pdf_nocjk";
        return ToolResult.of(target, baseName + ".pdf", key);
    }
}
