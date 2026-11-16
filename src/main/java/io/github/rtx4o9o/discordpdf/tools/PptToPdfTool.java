package io.github.rtx4o9o.discordpdf.tools;

import io.github.rtx4o9o.discordpdf.convert.OfficeFallback;
import io.github.rtx4o9o.discordpdf.convert.OoxmlSanitizer;
import io.github.rtx4o9o.discordpdf.core.FileKind;
import io.github.rtx4o9o.discordpdf.core.InputFile;
import io.github.rtx4o9o.discordpdf.core.PdfTool;
import io.github.rtx4o9o.discordpdf.core.ToolContext;
import io.github.rtx4o9o.discordpdf.core.ToolResult;
import io.github.rtx4o9o.discordpdf.util.PdfDocs;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PowerPoint (.pptx / .ppt) -> PDF, one page per slide.
 * Uses LibreOffice when available (selectable text), otherwise renders slides with Apache POI.
 */
public final class PptToPdfTool implements PdfTool {

    @Override
    public String id() {
        return "ppt2pdf";
    }

    @Override
    public ToolResult run(ToolContext ctx) throws Exception {
        InputFile in = ctx.single("kind.ppt", FileKind.PPTX, FileKind.PPT, FileKind.OLE2);
        FileKind kind = in.kind() == FileKind.OLE2 ? FileKind.PPT : in.kind();
        String outName = in.baseName() + ".pdf";

        if (ctx.office().available()) {
            Path work = ctx.workspace().newDir("office");
            Path named = work.resolve("slides" + kind.extension());
            if (kind == FileKind.DOCX || kind == FileKind.PPTX) {
                OoxmlSanitizer.sanitize(in.path(), named); // drop external links before LibreOffice sees the file
            } else {
                Files.copy(in.path(), named);
            }
            Path pdf = ctx.office().convert(named, "pdf", work);
            int pages = PdfDocs.scrubFile(pdf);
            return ToolResult.of(pdf, outName, "result.ppt2pdf", pages);
        }

        Path target = ctx.workspace().newFile(".pdf");
        int slides = OfficeFallback.slidesToPdf(in.path(), target, ctx.config().maxPages());
        return ToolResult.of(target, outName, "result.ppt2pdf_fallback", slides);
    }
}
