package io.github.rtx4o9o.discordpdf.tools;

import io.github.rtx4o9o.discordpdf.convert.PdfStructure;
import io.github.rtx4o9o.discordpdf.core.FileKind;
import io.github.rtx4o9o.discordpdf.core.InputFile;
import io.github.rtx4o9o.discordpdf.core.PdfTool;
import io.github.rtx4o9o.discordpdf.core.ToolContext;
import io.github.rtx4o9o.discordpdf.core.ToolResult;
import io.github.rtx4o9o.discordpdf.core.UserFacingException;
import io.github.rtx4o9o.discordpdf.util.Images;
import io.github.rtx4o9o.discordpdf.util.PdfDocs;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * PDF -> Word (.docx or .doc).
 * <ul>
 *   <li>{@code mode=editable} (default): text, headings and lists as editable Word paragraphs.</li>
 *   <li>{@code mode=layout}: every page as a full-page picture; looks identical, not editable.</li>
 * </ul>
 * {@code .doc} output is produced from the .docx by LibreOffice.
 */
public final class PdfToWordTool implements PdfTool {

    /** Printable width/height of an A4 page with ~2 cm margins, in points. */
    private static final double MAX_W_PT = 451;
    private static final double MAX_H_PT = 680;

    @Override
    public String id() {
        return "pdf2word";
    }

    @Override
    public ToolResult run(ToolContext ctx) throws Exception {
        InputFile in = ctx.single("kind.pdf", FileKind.PDF);
        String format = ctx.options().choice("format", "docx");
        String mode = ctx.options().choice("mode", "editable");
        if (format.equals("doc") && !ctx.office().available()) {
            throw new UserFacingException("error.office_unavailable");
        }

        Path docx = ctx.workspace().dir().resolve("document.docx");
        int pages;
        try (PDDocument pdf = PdfDocs.load(in.path(), ctx.config().maxPages())) {
            pages = pdf.getNumberOfPages();
            if (mode.equals("layout")) {
                writeLayout(pdf, docx);
            } else {
                List<PdfStructure.Block> blocks = PdfStructure.extract(pdf);
                if (PdfStructure.isEmpty(blocks)) {
                    // Scanned document: fall back to pictures so the user still gets something useful.
                    writeLayout(pdf, docx);
                    mode = "layout";
                } else {
                    writeEditable(blocks, docx);
                }
            }
        }

        String key = mode.equals("layout") ? "result.pdf2word_layout" : "result.pdf2word";
        if (format.equals("doc")) {
            Path doc = ctx.office().convert(docx, "doc", ctx.workspace().newDir("office"));
            return ToolResult.of(doc, in.baseName() + ".doc", key, pages);
        }
        return ToolResult.of(docx, in.baseName() + ".docx", key, pages);
    }

    private static void writeEditable(List<PdfStructure.Block> blocks, Path target) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            boolean first = true;
            for (PdfStructure.Block b : blocks) {
                switch (b.type()) {
                    case PAGE_BREAK -> {
                        if (!first) {
                            doc.createParagraph().createRun().addBreak(BreakType.PAGE);
                        }
                    }
                    case HEADING -> {
                        XWPFParagraph p = doc.createParagraph();
                        p.setSpacingBefore(240);
                        p.setSpacingAfter(120);
                        XWPFRun r = p.createRun();
                        r.setBold(true);
                        r.setFontSize(switch (b.level()) {
                            case 1 -> 20;
                            case 2 -> 16;
                            case 3 -> 14;
                            default -> 12;
                        });
                        r.setText(b.text());
                        first = false;
                    }
                    case LIST_ITEM -> {
                        XWPFParagraph p = doc.createParagraph();
                        p.setIndentationLeft(360);
                        p.setIndentationHanging(360);
                        p.setSpacingAfter(60);
                        XWPFRun r = p.createRun();
                        r.setText((b.marker() == null ? "•" : b.marker()) + "\t" + b.text());
                        first = false;
                    }
                    case PARAGRAPH -> {
                        XWPFParagraph p = doc.createParagraph();
                        p.setSpacingAfter(160);
                        p.createRun().setText(b.text());
                        first = false;
                    }
                }
            }
            try (OutputStream os = Files.newOutputStream(target)) {
                doc.write(os);
            }
        }
    }

    private static void writeLayout(PDDocument pdf, Path target) throws IOException {
        PDFRenderer renderer = new PDFRenderer(pdf);
        try (XWPFDocument doc = new XWPFDocument()) {
            for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                PDRectangle box = pdf.getPage(i).getCropBox();
                boolean rotated = pdf.getPage(i).getRotation() % 180 != 0;
                double wPt = rotated ? box.getHeight() : box.getWidth();
                double hPt = rotated ? box.getWidth() : box.getHeight();
                double fit = Math.min(MAX_W_PT / wPt, MAX_H_PT / hPt);

                BufferedImage img = renderer.renderImageWithDPI(i, 150, ImageType.RGB);
                byte[] jpeg = Images.jpegBytes(img, 0.85f);

                XWPFParagraph p = doc.createParagraph();
                p.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun r = p.createRun();
                try {
                    r.addPicture(new ByteArrayInputStream(jpeg), Document.PICTURE_TYPE_JPEG, "page" + (i + 1) + ".jpg",
                            Units.toEMU(wPt * fit), Units.toEMU(hPt * fit));
                } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
                    throw new IOException(e);
                }
                if (i < pdf.getNumberOfPages() - 1) {
                    r.addBreak(BreakType.PAGE);
                }
            }
            try (OutputStream os = Files.newOutputStream(target)) {
                doc.write(os);
            }
        }
    }
}
