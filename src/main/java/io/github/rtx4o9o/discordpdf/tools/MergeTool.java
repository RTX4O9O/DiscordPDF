package io.github.rtx4o9o.discordpdf.tools;

import io.github.rtx4o9o.discordpdf.core.FileKind;
import io.github.rtx4o9o.discordpdf.core.InputFile;
import io.github.rtx4o9o.discordpdf.core.PdfTool;
import io.github.rtx4o9o.discordpdf.core.ToolContext;
import io.github.rtx4o9o.discordpdf.core.ToolResult;
import io.github.rtx4o9o.discordpdf.core.UserFacingException;
import io.github.rtx4o9o.discordpdf.util.PdfDocs;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Merge PDF: concatenates PDFs in the order given (attachments first, then URLs). */
public final class MergeTool implements PdfTool {

    @Override
    public String id() {
        return "merge";
    }

    @Override
    public int minInputs() {
        return 2;
    }

    @Override
    public int maxInputs() {
        return 10;
    }

    @Override
    public ToolResult run(ToolContext ctx) throws Exception {
        List<InputFile> inputs = ctx.all("kind.pdf", FileKind.PDF);
        if (inputs.size() < 2) {
            throw new UserFacingException("error.merge_need_two");
        }
        int maxPages = ctx.config().maxPages();
        List<PDDocument> sources = new ArrayList<>();
        try (PDDocument out = new PDDocument()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            int pages = 0;
            for (InputFile in : inputs) {
                PDDocument src = PdfDocs.load(in.path(), maxPages);
                sources.add(src);
                pages += src.getNumberOfPages();
                if (pages > maxPages) {
                    throw new UserFacingException("error.too_many_pages", pages, maxPages);
                }
                merger.appendDocument(out, src);
            }
            Path target = ctx.workspace().newFile(".pdf");
            PdfDocs.saveClean(out, target);
            return ToolResult.of(target, inputs.get(0).baseName() + "_merged.pdf",
                    "result.merge", inputs.size(), pages);
        } finally {
            sources.forEach(PdfDocs::closeQuietly);
        }
    }
}
