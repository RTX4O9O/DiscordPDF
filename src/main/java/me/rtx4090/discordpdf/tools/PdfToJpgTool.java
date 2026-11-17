package me.rtx4090.discordpdf.tools;

import me.rtx4090.discordpdf.core.FileKind;
import me.rtx4090.discordpdf.core.I18n;
import me.rtx4090.discordpdf.core.InputFile;
import me.rtx4090.discordpdf.core.PdfTool;
import me.rtx4090.discordpdf.core.ToolContext;
import me.rtx4090.discordpdf.core.ToolResult;
import me.rtx4090.discordpdf.core.UserFacingException;
import me.rtx4090.discordpdf.util.Images;
import me.rtx4090.discordpdf.util.PageRanges;
import me.rtx4090.discordpdf.util.PdfDocs;
import me.rtx4090.discordpdf.util.ZipOut;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF -> JPG. Renders the selected pages ({@code pages}, default all) at {@code dpi}
 * (72-300, default 150). One page returns a .jpg, several return a .zip.
 */
public final class PdfToJpgTool implements PdfTool {

    /** Per-page pixel ceiling; the DPI is lowered for very large pages. */
    private static final double MAX_PAGE_PIXELS = 40_000_000d;

    @Override
    public String id() {
        return "pdf2jpg";
    }

    @Override
    public ToolResult run(ToolContext ctx) throws Exception {
        InputFile in = ctx.single("kind.pdf", FileKind.PDF);
        int dpi = (int) ctx.options().integer("dpi", 150, 72, 300);
        try (PDDocument pdf = PdfDocs.load(in.path(), ctx.config().maxPages())) {
            int count = pdf.getNumberOfPages();
            List<Integer> pages = ctx.options().string("pages")
                    .map(s -> PageRanges.indexes(PageRanges.parse(s, count)))
                    .orElseGet(() -> PageRanges.indexes(PageRanges.every(count, count)));

            PDFRenderer renderer = new PDFRenderer(pdf);
            List<ZipOut.Entry> images = new ArrayList<>();
            long total = 0;
            long limit = ctx.config().maxOutputBytes();
            String base = in.baseName();
            for (int index : pages) {
                PDRectangle box = pdf.getPage(index).getCropBox();
                double pixels = (box.getWidth() / 72.0 * dpi) * (box.getHeight() / 72.0 * dpi);
                float effectiveDpi = pixels > MAX_PAGE_PIXELS
                        ? (float) (dpi * Math.sqrt(MAX_PAGE_PIXELS / pixels))
                        : dpi;
                BufferedImage img = renderer.renderImageWithDPI(index, effectiveDpi, ImageType.RGB);
                Path file = ctx.workspace().newFile(".jpg");
                Images.writeJpeg(img, 0.88f, file);
                total += Files.size(file);
                if (total > limit) {
                    // Stop early instead of rendering every page of a result that cannot be uploaded.
                    throw new UserFacingException("error.output_too_large", "> " + I18n.size(limit), limit / (1024 * 1024));
                }
                images.add(new ZipOut.Entry(file, base + "_p" + (index + 1) + ".jpg"));
            }
            if (images.size() == 1) {
                ZipOut.Entry only = images.get(0);
                return ToolResult.of(only.file(), only.name(), "result.pdf2jpg", 1, dpi);
            }
            Path zip = ctx.workspace().newFile(".zip");
            ZipOut.write(zip, images);
            return ToolResult.of(zip, base + "_images.zip", "result.pdf2jpg", images.size(), dpi);
        }
    }
}
