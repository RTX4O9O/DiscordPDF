package io.github.rtx4o9o.discordpdf.convert;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.github.rtx4o9o.discordpdf.util.FontLocator;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders an HTML fragment to PDF with openhtmltopdf.
 *
 * <p>Security: the renderer never touches the network or the file system. Every URI it would
 * resolve (images, style sheets, fonts) is refused except inline {@code data:} URIs, and the
 * HTML is sanitised first so that only plain document markup remains.
 */
public final class HtmlPdfRenderer {

    static final String FAMILY_SANS = "DocSans";
    static final String FAMILY_BOLD = "DocSansBold";
    static final String FAMILY_MONO = "DocMono";
    static final String FAMILY_CJK = "DocCJK";

    private final FontLocator.Fonts fonts;
    private final String css;

    public HtmlPdfRenderer(FontLocator.Fonts fonts) {
        this.fonts = fonts;
        this.css = loadCss();
    }

    public boolean hasCjkFont() {
        return fonts.cjk().isPresent();
    }

    /**
     * @param bodyHtml HTML for the {@code <body>}; may come from untrusted input
     * @param target   output PDF
     */
    public void render(String bodyHtml, Path target) throws IOException {
        Document doc = Jsoup.parse("<!DOCTYPE html><html><head><meta charset=\"utf-8\"/><title></title></head><body></body></html>");
        doc.head().appendElement("style").appendText(css);
        doc.body().html(bodyHtml);
        sanitize(doc);
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml).charset(StandardCharsets.UTF_8);

        org.w3c.dom.Document w3c = new W3CDom().fromJsoup(doc);

        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        // Deny every external resource; allow only inline data: URIs.
        builder.useUriResolver((baseUri, uri) -> uri != null && uri.startsWith("data:") ? uri : null);
        fonts.sans().ifPresent(p -> builder.useFont(p.toFile(), FAMILY_SANS));
        fonts.sansBold().ifPresent(p -> builder.useFont(p.toFile(), FAMILY_BOLD));
        fonts.mono().ifPresent(p -> builder.useFont(p.toFile(), FAMILY_MONO));
        fonts.cjk().ifPresent(p -> builder.useFont(p.toFile(), FAMILY_CJK));
        builder.withW3cDocument(w3c, "about:blank");
        try (OutputStream os = Files.newOutputStream(target)) {
            builder.toStream(os);
            builder.run();
        }
    }

    /** Keeps document markup only: no scripts, frames, forms, external images or inline styles. */
    static void sanitize(Document doc) {
        doc.select("script, iframe, frame, frameset, object, embed, applet, link, base, form, input, "
                + "button, select, textarea, video, audio, source, svg, math, meta[http-equiv]").remove();
        doc.body().select("style").remove();
        doc.body().select("[style]").removeAttr("style");
        for (Element el : doc.body().getAllElements()) {
            el.attributes().asList().stream()
                    .filter(a -> a.getKey().toLowerCase().startsWith("on"))
                    .map(org.jsoup.nodes.Attribute::getKey)
                    .toList()
                    .forEach(el::removeAttr);
        }
        for (Element img : doc.select("img")) {
            String src = img.attr("src");
            if (!src.startsWith("data:image/")) {
                String alt = img.attr("alt");
                img.replaceWith(new Element("span").addClass("img-placeholder")
                        .text(alt.isBlank() ? "[image]" : "[" + alt + "]"));
            }
        }
        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href").trim().toLowerCase();
            if (!(href.startsWith("http://") || href.startsWith("https://") || href.startsWith("mailto:") || href.startsWith("#"))) {
                a.removeAttr("href");
            }
        }
    }

    private static String loadCss() {
        try (InputStream in = HtmlPdfRenderer.class.getResourceAsStream("/styles/document.css")) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
