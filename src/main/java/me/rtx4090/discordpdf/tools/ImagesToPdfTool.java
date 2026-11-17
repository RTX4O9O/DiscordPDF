package me.rtx4090.discordpdf.tools;

import me.rtx4090.discordpdf.core.FileKind;
import me.rtx4090.discordpdf.core.InputFile;
import me.rtx4090.discordpdf.core.PdfTool;
import me.rtx4090.discordpdf.core.ToolContext;
import me.rtx4090.discordpdf.core.ToolResult;
import me.rtx4090.discordpdf.core.UserFacingException;
import me.rtx4090.discordpdf.util.Images;
import me.rtx4090.discordpdf.util.JpegMetadata;
import me.rtx4090.discordpdf.util.PdfDocs;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * JPG (and PNG/GIF/BMP) -> PDF, one image per page in the order given.
 *
 * <p>Privacy: JPEGs are embedded without re-compression but with EXIF/XMP/IPTC removed,
 * so GPS coordinates, camera serial numbers and similar data never end up in the PDF.
 * The EXIF orientation is applied as page rotation. Other formats are decoded and
 * re-encoded losslessly, which drops their metadata as well.
 *
 * <p>{@code page_size}: {@code a4} (default), {@code letter}, or {@code fit} (page = image).
 */
public final class ImagesToPdfTool implements PdfTool {

    private static final float MARGIN = 24f;
    private static final float MAX_PAGE = 14_400f; // PDF user-space limit (200 inches)

    @Override
    public String id() {
        return "jpg2pdf";
    }

    @Override
    public int maxInputs() {
        return 10;
    }

    @Override
    public ToolResult run(ToolContext ctx) throws Exception {
        List<InputFile> inputs = ctx.all("kind.image", FileKind.JPEG, FileKind.PNG, FileKind.GIF, FileKind.BMP);
        String pageSize = ctx.options().choice("page_size", "a4");
        Path target = ctx.workspace().newFile(".pdf");
        try (PDDocument doc = new PDDocument()) {
            for (InputFile in : inputs) {
                addImage(doc, in, pageSize);
            }
            PdfDocs.saveClean(doc, target);
        }
        return ToolResult.of(target, inputs.get(0).baseName() + ".pdf", "result.jpg2pdf", inputs.size());
    }

    private static void addImage(PDDocument doc, InputFile in, String pageSize) throws IOException {
        Dimension dim = Images.dimensions(in.path());
        if ((long) dim.width * dim.height > Images.MAX_PIXELS) {
            throw new UserFacingException("error.image_too_large");
        }
        PDImageXObject image;
        int rotation = 0;
        if (in.kind() == FileKind.JPEG) {
            JpegMetadata.Result stripped = JpegMetadata.strip(Files.readAllBytes(in.path()));
            rotation = JpegMetadata.rotationFor(stripped.orientation());
            image = JPEGFactory.createFromByteArray(doc, stripped.jpeg());
        } else {
            BufferedImage bi = Images.readChecked(in.path());
            image = LosslessFactory.createFromImage(doc, bi);
        }

        float imgW = image.getWidth();
        float imgH = image.getHeight();
        boolean quarterTurn = rotation == 90 || rotation == 270;
        // Size of the picture as the reader will see it (after rotation).
        float shownW = quarterTurn ? imgH : imgW;
        float shownH = quarterTurn ? imgW : imgH;

        float pageW;
        float pageH;
        if (pageSize.equals("fit")) {
            // 96 dpi -> points, capped at the PDF maximum.
            float scale = Math.min(0.75f, MAX_PAGE / Math.max(shownW, shownH));
            pageW = shownW * scale;
            pageH = shownH * scale;
        } else {
            PDRectangle paper = pageSize.equals("letter") ? PDRectangle.LETTER : PDRectangle.A4;
            boolean landscape = shownW > shownH;
            pageW = landscape ? paper.getHeight() : paper.getWidth();
            pageH = landscape ? paper.getWidth() : paper.getHeight();
        }

        // Build the page in unrotated coordinates, then let /Rotate turn it.
        float boxW = quarterTurn ? pageH : pageW;
        float boxH = quarterTurn ? pageW : pageH;
        PDPage page = new PDPage(new PDRectangle(boxW, boxH));
        page.setRotation(rotation);
        doc.addPage(page);

        float margin = pageSize.equals("fit") ? 0 : MARGIN;
        float availW = boxW - 2 * margin;
        float availH = boxH - 2 * margin;
        float fit = Math.min(availW / imgW, availH / imgH);
        float drawW = imgW * fit;
        float drawH = imgH * fit;
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.drawImage(image, (boxW - drawW) / 2, (boxH - drawH) / 2, drawW, drawH);
        }
    }
}
