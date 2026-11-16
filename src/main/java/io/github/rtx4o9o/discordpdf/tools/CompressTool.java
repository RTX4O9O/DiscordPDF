package io.github.rtx4o9o.discordpdf.tools;

import io.github.rtx4o9o.discordpdf.core.FileKind;
import io.github.rtx4o9o.discordpdf.core.I18n;
import io.github.rtx4o9o.discordpdf.core.InputFile;
import io.github.rtx4o9o.discordpdf.core.PdfTool;
import io.github.rtx4o9o.discordpdf.core.ToolContext;
import io.github.rtx4o9o.discordpdf.core.ToolResult;
import io.github.rtx4o9o.discordpdf.util.Images;
import io.github.rtx4o9o.discordpdf.util.PdfDocs;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compress PDF. Re-encodes raster images as JPEG (down-sampled to a maximum edge length),
 * removes metadata, thumbnails and application private data, and saves with object-stream
 * compression. Vector content and text are untouched, so text stays sharp and selectable.
 *
 * <p>Levels: {@code low} (best quality), {@code medium} (default), {@code high} (smallest).
 */
public final class CompressTool implements PdfTool {

    private record Level(float quality, int maxEdge) {
    }

    private static Level level(String name) {
        return switch (name) {
            case "low" -> new Level(0.85f, 2400);
            case "high" -> new Level(0.5f, 1100);
            default -> new Level(0.7f, 1600);
        };
    }

    @Override
    public String id() {
        return "compress";
    }

    @Override
    public ToolResult run(ToolContext ctx) throws Exception {
        InputFile in = ctx.single("kind.pdf", FileKind.PDF);
        Level level = level(ctx.options().choice("level", "medium"));
        Path target = ctx.workspace().newFile(".pdf");

        try (PDDocument doc = PdfDocs.load(in.path(), ctx.config().maxPages())) {
            Map<COSStream, PDImageXObject> done = new IdentityHashMap<>();
            Set<COSBase> visitedForms = Collections.newSetFromMap(new IdentityHashMap<>());
            for (PDPage page : doc.getPages()) {
                PDResources res = page.getResources();
                if (res != null) {
                    processResources(doc, res, level, done, visitedForms, 0);
                }
            }
            PdfDocs.saveClean(doc, target);
        }

        long before = in.size();
        long after = Files.size(target);
        String name = in.baseName() + "_compressed.pdf";
        if (after >= before) {
            // Already optimised. Still return the cleaned copy (metadata removed).
            return ToolResult.of(target, name, "result.compress_nochange", sizeMb(before));
        }
        int saved = (int) Math.round(100.0 * (before - after) / before);
        return ToolResult.of(target, name, "result.compress", sizeMb(before), sizeMb(after), saved);
    }

    private static String sizeMb(long bytes) {
        return I18n.size(bytes);
    }

    private static void processResources(PDDocument doc, PDResources res, Level level,
                                         Map<COSStream, PDImageXObject> done, Set<COSBase> visitedForms,
                                         int depth) throws IOException {
        List<COSName> names = new ArrayList<>();
        for (COSName n : res.getXObjectNames()) {
            names.add(n);
        }
        for (COSName name : names) {
            PDXObject xo;
            try {
                xo = res.getXObject(name);
            } catch (IOException e) {
                continue; // broken object: leave it alone
            }
            if (xo instanceof PDImageXObject img) {
                PDImageXObject replacement = recompress(doc, img, level, done);
                if (replacement != null) {
                    res.put(name, replacement);
                }
            } else if (xo instanceof PDFormXObject form && depth < 12) {
                if (visitedForms.add(form.getCOSObject())) {
                    PDResources inner = form.getResources();
                    if (inner != null) {
                        processResources(doc, inner, level, done, visitedForms, depth + 1);
                    }
                }
            }
        }
    }

    private static PDImageXObject recompress(PDDocument doc, PDImageXObject img, Level level,
                                             Map<COSStream, PDImageXObject> done) {
        COSStream cos = img.getCOSObject();
        if (done.containsKey(cos)) {
            return done.get(cos);
        }
        PDImageXObject result = null;
        try {
            boolean masked = img.isStencil() || img.getSoftMask() != null
                    || cos.containsKey(COSName.MASK) || cos.containsKey(COSName.SMASK);
            if (!masked && img.getWidth() * (long) img.getHeight() >= 64L * 64L) {
                BufferedImage bi = img.getImage();
                if (bi != null && !bi.getColorModel().hasAlpha()) {
                    int edge = Math.max(bi.getWidth(), bi.getHeight());
                    double factor = edge > level.maxEdge() ? level.maxEdge() / (double) edge : 1.0;
                    BufferedImage scaled = Images.toOpaque(Images.scale(bi, factor));
                    PDImageXObject jpeg = JPEGFactory.createFromImage(doc, scaled, level.quality());
                    long oldLen = cos.getLength();
                    long newLen = jpeg.getCOSObject().getLength();
                    if (newLen < oldLen * 0.95) {
                        result = jpeg;
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            result = null; // unusual colour space or broken image: keep the original
        }
        done.put(cos, result);
        return result;
    }
}
