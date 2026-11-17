package me.rtx4090.discordpdf.tools;

import me.rtx4090.discordpdf.convert.OfficeFallback;
import me.rtx4090.discordpdf.convert.OoxmlSanitizer;
import me.rtx4090.discordpdf.core.FileKind;
import me.rtx4090.discordpdf.core.InputFile;
import me.rtx4090.discordpdf.core.PdfTool;
import me.rtx4090.discordpdf.core.ToolContext;
import me.rtx4090.discordpdf.core.ToolResult;
import me.rtx4090.discordpdf.util.PdfDocs;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Word (.docx / .doc) -> PDF.
 * With LibreOffice installed the layout is preserved faithfully; without it a pure-Java
 * fallback keeps text, headings, lists, tables and pictures.
 */
public final class WordToPdfTool implements PdfTool {

    @Override
    public String id() {
        return "word2pdf";
    }

    @Override
    public ToolResult run(ToolContext ctx) throws Exception {
        InputFile in = ctx.single("kind.word", FileKind.DOCX, FileKind.DOC, FileKind.OLE2);
        FileKind kind = in.kind() == FileKind.OLE2 ? FileKind.DOC : in.kind();
        String outName = in.baseName() + ".pdf";

        if (ctx.office().available()) {
            Path work = ctx.workspace().newDir("office");
            Path named = work.resolve("document" + kind.extension());
            if (kind == FileKind.DOCX || kind == FileKind.PPTX) {
                OoxmlSanitizer.sanitize(in.path(), named); // drop external links before LibreOffice sees the file
            } else {
                Files.copy(in.path(), named);
            }
            Path pdf = ctx.office().convert(named, "pdf", work);
            int pages = PdfDocs.scrubFile(pdf);
            return ToolResult.of(pdf, outName, "result.word2pdf", pages);
        }

        String html = OfficeFallback.wordToHtml(in.path(), kind);
        Path target = ctx.workspace().newFile(".pdf");
        ctx.html().render(html, target);
        return ToolResult.of(target, outName, "result.word2pdf_fallback");
    }
}
