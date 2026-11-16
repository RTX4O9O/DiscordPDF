package io.github.rtx4o9o.discordpdf.tools;

import io.github.rtx4o9o.discordpdf.convert.MarkdownWriter;
import io.github.rtx4o9o.discordpdf.convert.PdfStructure;
import io.github.rtx4o9o.discordpdf.core.FileKind;
import io.github.rtx4o9o.discordpdf.core.InputFile;
import io.github.rtx4o9o.discordpdf.core.PdfTool;
import io.github.rtx4o9o.discordpdf.core.ToolContext;
import io.github.rtx4o9o.discordpdf.core.ToolResult;
import io.github.rtx4o9o.discordpdf.core.UserFacingException;
import io.github.rtx4o9o.discordpdf.util.PdfDocs;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** PDF -> Markdown: headings, paragraphs and lists recovered from the text layer. */
public final class PdfToMarkdownTool implements PdfTool {

    @Override
    public String id() {
        return "pdf2md";
    }

    @Override
    public ToolResult run(ToolContext ctx) throws Exception {
        InputFile in = ctx.single("kind.pdf", FileKind.PDF);
        boolean pageMarkers = ctx.options().bool("page_breaks", false);
        List<PdfStructure.Block> blocks;
        int pages;
        try (PDDocument doc = PdfDocs.load(in.path(), ctx.config().maxPages())) {
            pages = doc.getNumberOfPages();
            blocks = PdfStructure.extract(doc);
        }
        if (PdfStructure.isEmpty(blocks)) {
            throw new UserFacingException("error.no_text_layer");
        }
        String markdown = MarkdownWriter.write(blocks, pageMarkers);
        Path target = ctx.workspace().newFile(".md");
        Files.writeString(target, markdown, StandardCharsets.UTF_8);
        return ToolResult.of(target, in.baseName() + ".md", "result.pdf2md", pages);
    }
}
