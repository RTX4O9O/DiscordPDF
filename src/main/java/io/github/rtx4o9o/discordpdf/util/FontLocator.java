package io.github.rtx4o9o.discordpdf.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

/**
 * Finds TrueType fonts to embed in generated PDFs. PDFBox can only embed fonts with
 * TrueType (glyf) outlines, so .ttf files are preferred; CFF-based .otf/.ttc files are skipped.
 *
 * <p>CJK font lookup order:
 * <ol>
 *   <li>{@code FONT_CJK} environment variable, if set (optional override)</li>
 *   <li>a font bundled in the jar under {@code src/main/resources/font/}</li>
 *   <li>well-known system locations</li>
 * </ol>
 * A bundled font is copied once to the JVM temp directory, because the PDF renderer needs a
 * real file; the copy is removed when the program exits.
 */
public final class FontLocator {

    /** Fonts used by the HTML renderer. Any of them may be absent. */
    public record Fonts(Optional<Path> sans, Optional<Path> sansBold, Optional<Path> mono, Optional<Path> cjk) {
    }

    private static final List<String> SANS = List.of(
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/TTF/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
            "C:/Windows/Fonts/arial.ttf",
            "/Library/Fonts/Arial.ttf");

    private static final List<String> SANS_BOLD = List.of(
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
            "C:/Windows/Fonts/arialbd.ttf",
            "/Library/Fonts/Arial Bold.ttf");

    private static final List<String> MONO = List.of(
            "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
            "/usr/share/fonts/dejavu/DejaVuSansMono.ttf",
            "/usr/share/fonts/TTF/DejaVuSansMono.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationMono-Regular.ttf",
            "C:/Windows/Fonts/consola.ttf");

    /** Classpath locations checked for a bundled CJK font, in order. */
    private static final List<String> BUNDLED_CJK = List.of(
            "/font/DroidSansFallbackFull.ttf",
            "/font/DroidSansFallback.ttf",
            "/font/NotoSansTC-Regular.ttf");

    private static final List<String> CJK = List.of(
            "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
            "/usr/share/fonts/truetype/noto/NotoSansTC-Regular.ttf",
            "/usr/share/fonts/truetype/noto/NotoSansCJKtc-Regular.ttf",
            "/usr/share/fonts/truetype/arphic-ukai/ukai.ttf",
            "/usr/share/fonts/google-droid/DroidSansFallbackFull.ttf",
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            "/Library/Fonts/Arial Unicode.ttf");

    private FontLocator() {
    }

    public static Fonts locate(Optional<Path> cjkOverride) {
        Optional<Path> cjk = cjkOverride.filter(Files::isRegularFile)
                .or(FontLocator::bundledCjk)
                .or(() -> first(CJK));
        return new Fonts(first(SANS), first(SANS_BOLD), first(MONO), cjk);
    }

    private static Optional<Path> extracted;

    /** Copies the first bundled CJK font found on the classpath to a temp file (once). */
    private static synchronized Optional<Path> bundledCjk() {
        if (extracted != null && extracted.filter(Files::isRegularFile).isPresent()) {
            return extracted;
        }
        extracted = Optional.empty();
        for (String resource : BUNDLED_CJK) {
            try (InputStream in = FontLocator.class.getResourceAsStream(resource)) {
                if (in == null) {
                    continue;
                }
                Path tmp = Files.createTempFile("discordpdf-font-", ".ttf");
                tmp.toFile().deleteOnExit();
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                extracted = Optional.of(tmp);
                break;
            } catch (IOException e) {
                // try the next candidate, then fall back to system fonts
            }
        }
        return extracted;
    }

    private static Optional<Path> first(List<String> candidates) {
        for (String c : candidates) {
            Path p = Path.of(c);
            if (Files.isRegularFile(p)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
