package io.github.rtx4o9o.discordpdf.convert;

import io.github.rtx4o9o.discordpdf.core.FileKind;
import io.github.rtx4o9o.discordpdf.core.UserFacingException;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.sl.usermodel.Slide;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.sl.usermodel.SlideShowFactory;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.jsoup.nodes.Entities;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java conversions used when LibreOffice is not installed.
 * Slides are rendered faithfully (as images); Word documents keep text, headings,
 * lists, tables, bold/italic and embedded pictures but not exact layout.
 */
public final class OfficeFallback {

    private static final Pattern HEADING_STYLE = Pattern.compile("(?i)(?:heading|標題|标题|title)\\s*(\\d)?");
    private static final long MAX_INLINE_IMAGE_BYTES = 20L * 1024 * 1024;

    private OfficeFallback() {
    }

    // ------------------------------------------------------------------ slides

    /** Renders every slide at 2x and writes them as PDF pages of the slide size. */
    public static int slidesToPdf(Path input, Path target, int maxPages) throws IOException {
        try (SlideShow<?, ?> show = openSlides(input); PDDocument pdf = new PDDocument()) {
            Dimension size = show.getPageSize();
            int count = show.getSlides().size();
            if (count == 0) {
                throw new UserFacingException("error.office_empty");
            }
            if (count > maxPages) {
                throw new UserFacingException("error.too_many_pages", count, maxPages);
            }
            double scale = 2.0;
            int w = (int) Math.ceil(size.width * scale);
            int h = (int) Math.ceil(size.height * scale);
            for (Slide<?, ?> slide : show.getSlides()) {
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                    g.setPaint(Color.WHITE);
                    g.fillRect(0, 0, w, h);
                    g.scale(scale, scale);
                    slide.draw(g);
                } finally {
                    g.dispose();
                }
                PDPage page = new PDPage(new PDRectangle(size.width, size.height));
                pdf.addPage(page);
                PDImageXObject image = JPEGFactory.createFromImage(pdf, img, 0.9f);
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                    cs.drawImage(image, 0, 0, size.width, size.height);
                }
            }
            pdf.save(target.toFile());
            return count;
        }
    }

    private static SlideShow<?, ?> openSlides(Path input) throws IOException {
        try {
            return SlideShowFactory.create(input.toFile(), null, true);
        } catch (EncryptedDocumentException e) {
            throw new UserFacingException("error.office_encrypted");
        }
    }

    // ------------------------------------------------------------------ word

    /** Extracts a Word document as sanitisable HTML. */
    public static String wordToHtml(Path input, FileKind kind) throws IOException {
        try (InputStream in = Files.newInputStream(input)) {
            return kind == FileKind.DOCX ? docxToHtml(in) : docToHtml(in);
        } catch (EncryptedDocumentException e) {
            throw new UserFacingException("error.office_encrypted");
        }
    }

    private static String docxToHtml(InputStream in) throws IOException {
        StringBuilder html = new StringBuilder();
        long[] imageBudget = {MAX_INLINE_IMAGE_BYTES};
        try (XWPFDocument doc = new XWPFDocument(in)) {
            boolean inList = false;
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph p) {
                    boolean isListItem = p.getNumID() != null;
                    if (inList && !isListItem) {
                        html.append("</ul>");
                        inList = false;
                    }
                    String inner = runsToHtml(p, imageBudget);
                    if (inner.isBlank()) {
                        continue;
                    }
                    int level = headingLevel(p.getStyle());
                    if (isListItem) {
                        if (!inList) {
                            html.append("<ul>");
                            inList = true;
                        }
                        html.append("<li>").append(inner).append("</li>");
                    } else if (level > 0) {
                        html.append("<h").append(level).append('>').append(inner).append("</h").append(level).append('>');
                    } else {
                        html.append("<p>").append(inner).append("</p>");
                    }
                } else if (element instanceof XWPFTable t) {
                    if (inList) {
                        html.append("</ul>");
                        inList = false;
                    }
                    html.append("<table>");
                    for (XWPFTableRow row : t.getRows()) {
                        html.append("<tr>");
                        for (XWPFTableCell cell : row.getTableCells()) {
                            html.append("<td>").append(escape(cell.getText())).append("</td>");
                        }
                        html.append("</tr>");
                    }
                    html.append("</table>");
                }
            }
            if (inList) {
                html.append("</ul>");
            }
        }
        return html.toString();
    }

    private static String runsToHtml(XWPFParagraph p, long[] imageBudget) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun run : p.getRuns()) {
            String text = run.text();
            if (text != null && !text.isEmpty()) {
                String t = escape(text).replace("\n", "<br/>");
                if (run.isBold()) {
                    t = "<strong>" + t + "</strong>";
                }
                if (run.isItalic()) {
                    t = "<em>" + t + "</em>";
                }
                sb.append(t);
            }
            for (XWPFPicture pic : run.getEmbeddedPictures()) {
                XWPFPictureData data = pic.getPictureData();
                if (data == null) {
                    continue;
                }
                String mime = mimeFor(data.suggestFileExtension());
                byte[] bytes = data.getData();
                if (mime != null && bytes != null && bytes.length <= imageBudget[0]) {
                    imageBudget[0] -= bytes.length;
                    sb.append("<img src=\"data:").append(mime).append(";base64,")
                            .append(Base64.getEncoder().encodeToString(bytes)).append("\"/>");
                }
            }
        }
        return sb.toString();
    }

    private static String mimeFor(String ext) {
        if (ext == null) {
            return null;
        }
        return switch (ext.toLowerCase(Locale.ROOT)) {
            case "png" -> "image/png";
            case "jpeg", "jpg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            default -> null;
        };
    }

    private static int headingLevel(String style) {
        if (style == null) {
            return 0;
        }
        Matcher m = HEADING_STYLE.matcher(style);
        if (!m.find()) {
            return 0;
        }
        if (m.group(1) == null) {
            return 1; // "Title"
        }
        int level = Integer.parseInt(m.group(1));
        return Math.max(1, Math.min(6, level));
    }

    private static String docToHtml(InputStream in) throws IOException {
        StringBuilder html = new StringBuilder();
        try (HWPFDocument doc = new HWPFDocument(in); WordExtractor extractor = new WordExtractor(doc)) {
            for (String para : extractor.getParagraphText()) {
                String clean = para.replaceAll("[\\x00-\\x08\\x0B-\\x1F\\x7F]", " ").strip();
                if (!clean.isEmpty()) {
                    html.append("<p>").append(escape(clean)).append("</p>");
                }
            }
        }
        return html.toString();
    }

    private static String escape(String s) {
        return Entities.escape(s == null ? "" : s);
    }
}
