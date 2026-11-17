package me.rtx4090.discordpdf.tools;

import me.rtx4090.discordpdf.core.FileKind;
import me.rtx4090.discordpdf.core.InputFile;
import me.rtx4090.discordpdf.core.PdfTool;
import me.rtx4090.discordpdf.core.ToolContext;
import me.rtx4090.discordpdf.core.ToolResult;
import me.rtx4090.discordpdf.util.PageRanges;
import me.rtx4090.discordpdf.util.PageRanges.Range;
import me.rtx4090.discordpdf.util.PdfDocs;
import me.rtx4090.discordpdf.util.ZipOut;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Split PDF.
 * <ul>
 *   <li>{@code ranges="1-3,5,8-"} + {@code mode=separate} (default): one PDF per range</li>
 *   <li>{@code ranges=...} + {@code mode=extract}: one PDF containing only the selected pages</li>
 *   <li>{@code every=N}: chunks of N pages</li>
 *   <li>nothing: one PDF per page</li>
 * </ul>
 * Several outputs are returned as a zip.
 */
public final class SplitTool implements PdfTool {

    @Override
    public String id() {
        return "split";
    }

    @Override
    public ToolResult run(ToolContext ctx) throws Exception {
        InputFile in = ctx.single("kind.pdf", FileKind.PDF);
        Optional<String> rangesOpt = ctx.options().string("ranges");
        long every = ctx.options().integer("every", 0, 0, 10_000);
        String mode = ctx.options().choice("mode", "separate");

        try (PDDocument src = PdfDocs.load(in.path(), ctx.config().maxPages())) {
            int pageCount = src.getNumberOfPages();
            List<Range> ranges;
            if (rangesOpt.isPresent()) {
                ranges = PageRanges.parse(rangesOpt.get(), pageCount);
            } else {
                ranges = PageRanges.every(every > 0 ? (int) every : 1, pageCount);
            }

            String base = in.baseName();
            if (rangesOpt.isPresent() && mode.equals("extract")) {
                Path target = ctx.workspace().newFile(".pdf");
                extract(src, ranges, target);
                int pages = PageRanges.indexes(ranges).size();
                return ToolResult.of(target, base + "_extracted.pdf", "result.split_extract", pages);
            }

            List<ZipOut.Entry> parts = new ArrayList<>();
            for (Range r : ranges) {
                Path part = ctx.workspace().newFile(".pdf");
                writeRange(src, r, part);
                parts.add(new ZipOut.Entry(part, base + "_" + r.label() + ".pdf"));
            }
            if (parts.size() == 1) {
                ZipOut.Entry only = parts.get(0);
                return ToolResult.of(only.file(), only.name(), "result.split", 1);
            }
            Path zip = ctx.workspace().newFile(".zip");
            ZipOut.write(zip, parts);
            return ToolResult.of(zip, base + "_split.zip", "result.split", parts.size());
        }
    }

    private static void writeRange(PDDocument src, Range r, Path target) throws IOException {
        Splitter splitter = new Splitter();
        splitter.setStartPage(r.from());
        splitter.setEndPage(r.to());
        splitter.setSplitAtPage(r.size());
        List<PDDocument> docs = splitter.split(src);
        try {
            PDDocument part = docs.get(0);
            PdfDocs.saveClean(part, target);
        } finally {
            docs.forEach(PdfDocs::closeQuietly);
        }
    }

    /** All selected ranges, in the order given, in a single document. */
    private static void extract(PDDocument src, List<Range> ranges, Path target) throws IOException {
        List<PDDocument> pieces = new ArrayList<>();
        try (PDDocument out = new PDDocument()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            for (Range r : ranges) {
                Splitter splitter = new Splitter();
                splitter.setStartPage(r.from());
                splitter.setEndPage(r.to());
                splitter.setSplitAtPage(r.size());
                List<PDDocument> docs = splitter.split(src);
                pieces.addAll(docs);
                merger.appendDocument(out, docs.get(0));
            }
            PdfDocs.saveClean(out, target);
        } finally {
            pieces.forEach(PdfDocs::closeQuietly);
        }
    }
}
