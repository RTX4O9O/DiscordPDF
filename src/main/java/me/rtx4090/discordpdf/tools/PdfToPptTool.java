package me.rtx4090.discordpdf.tools;

import me.rtx4090.discordpdf.core.FileKind;
import me.rtx4090.discordpdf.core.InputFile;
import me.rtx4090.discordpdf.core.PdfTool;
import me.rtx4090.discordpdf.core.ToolContext;
import me.rtx4090.discordpdf.core.ToolResult;
import me.rtx4090.discordpdf.util.Images;
import me.rtx4090.discordpdf.util.PdfDocs;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.PictureShape;
import org.apache.poi.sl.usermodel.Slide;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.xslf.usermodel.XMLSlideShow;

import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PDF -> PowerPoint (.pptx or .ppt). Each page becomes one slide holding a high-resolution
 * picture of the page, so the slides look exactly like the PDF. The slide size follows the
 * first page; pages of other sizes are centred.
 */
public final class PdfToPptTool implements PdfTool {

    private static final int DPI = 150;

    @Override
    public String id() {
        return "pdf2ppt";
    }

    @Override
    public ToolResult run(ToolContext ctx) throws Exception {
        InputFile in = ctx.single("kind.pdf", FileKind.PDF);
        boolean legacy = ctx.options().choice("format", "pptx").equals("ppt");
        Path target = ctx.workspace().newFile(legacy ? ".ppt" : ".pptx");
        int pages;
        try (PDDocument pdf = PdfDocs.load(in.path(), ctx.config().maxPages());
             SlideShow<?, ?> show = legacy ? new HSLFSlideShow() : new XMLSlideShow()) {
            pages = pdf.getNumberOfPages();
            build(pdf, show);
            try (OutputStream os = Files.newOutputStream(target)) {
                show.write(os);
            }
        }
        return ToolResult.of(target, in.baseName() + (legacy ? ".ppt" : ".pptx"), "result.pdf2ppt", pages);
    }

    private static void build(PDDocument pdf, SlideShow<?, ?> show) throws IOException {
        double[] first = displaySize(pdf.getPage(0));
        Dimension slideSize = new Dimension((int) Math.round(first[0]), (int) Math.round(first[1]));
        show.setPageSize(slideSize);
        PDFRenderer renderer = new PDFRenderer(pdf);
        for (int i = 0; i < pdf.getNumberOfPages(); i++) {
            double[] size = displaySize(pdf.getPage(i));
            BufferedImage img = renderer.renderImageWithDPI(i, DPI, ImageType.RGB);
            byte[] jpeg = Images.jpegBytes(img, 0.88f);

            Slide<?, ?> slide = show.createSlide();
            PictureData data = show.addPicture(jpeg, PictureData.PictureType.JPEG);
            PictureShape<?, ?> picture = slide.createPicture(data);

            double fit = Math.min(slideSize.width / size[0], slideSize.height / size[1]);
            double w = size[0] * fit;
            double h = size[1] * fit;
            picture.setAnchor(new Rectangle2D.Double((slideSize.width - w) / 2, (slideSize.height - h) / 2, w, h));
        }
    }

    /** Page size in points as displayed (rotation applied). */
    private static double[] displaySize(PDPage page) {
        PDRectangle box = page.getCropBox();
        boolean rotated = page.getRotation() % 180 != 0;
        return rotated
                ? new double[]{box.getHeight(), box.getWidth()}
                : new double[]{box.getWidth(), box.getHeight()};
    }
}
