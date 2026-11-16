package io.github.rtx4o9o.discordpdf.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Finds TrueType fonts to embed in generated PDFs. PDFBox can only embed fonts with
 * TrueType (glyf) outlines, so .ttf files are preferred; CFF-based .otf/.ttc files are skipped.
 *
 * <p>The Docker image installs DejaVu (Latin, Greek, Cyrillic) and Droid Sans Fallback
 * (Chinese, Japanese, Korean). Set {@code FONT_CJK} to use another CJK .ttf, e.g. Noto Sans TC.
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

    private static final List<String> CJK = List.of(
            "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
            "/usr/share/fonts/truetype/noto/NotoSansTC-Regular.ttf",
            "/usr/share/fonts/truetype/noto/NotoSansCJKtc-Regular.ttf",
            "/usr/share/fonts/truetype/arphic-ukai/ukai.ttf",
            "/usr/share/fonts/google-droid/DroidSansFallbackFull.ttf",
            "C:/Windows/Fonts/msjh.ttf",
            "/Library/Fonts/Arial Unicode.ttf");

    private FontLocator() {
    }

    public static Fonts locate(Optional<Path> cjkOverride) {
        Optional<Path> cjk = cjkOverride.filter(Files::isRegularFile).or(() -> first(CJK));
        return new Fonts(first(SANS), first(SANS_BOLD), first(MONO), cjk);
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
