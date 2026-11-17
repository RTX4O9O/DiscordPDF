package me.rtx4090.discordpdf.convert;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recovers a simple logical structure (headings, paragraphs, list items, page breaks)
 * from a PDF's text layer, using font size and weight relative to the body text.
 * Used by PDF -> Markdown and PDF -> Word.
 *
 * <p>Scanned PDFs (images only) have no text layer; they produce an empty result.
 */
public final class PdfStructure {

    public enum Type { HEADING, PARAGRAPH, LIST_ITEM, PAGE_BREAK }

    /**
     * @param type   block type
     * @param text   plain text (not escaped)
     * @param level  heading level 1..4 (headings only)
     * @param marker list marker such as "1." for ordered items, null for bullets
     * @param page   1-based page number
     */
    public record Block(Type type, String text, int level, String marker, int page) {
    }

    private record Line(String text, double size, double boldRatio, int page) {
    }

    private static final Pattern BULLET = Pattern.compile("^(?:[•●○◦▪▫■□‣⁃∙·]\\s*|[-–—*]\\s+)(.+)$");
    private static final Pattern ORDERED = Pattern.compile("^((?:\\d{1,3}|[a-zA-Z])[.)]\\s+|\\(\\d{1,3}\\)\\s*|（\\d{1,3}）\\s*)(.+)$");

    private PdfStructure() {
    }

    public static List<Block> extract(PDDocument doc) throws IOException {
        Collector c = new Collector();
        c.getText(doc);
        return classify(c.paragraphs, c.bodySize());
    }

    /** True when the document has (almost) no extractable text, e.g. a scan. */
    public static boolean isEmpty(List<Block> blocks) {
        int chars = 0;
        for (Block b : blocks) {
            if (b.type() != Type.PAGE_BREAK) {
                chars += b.text().length();
            }
        }
        return chars < 3;
    }

    // ---------------------------------------------------------------- collection

    private static final class Collector extends PDFTextStripper {

        final List<List<Line>> paragraphs = new ArrayList<>();
        private final Map<Integer, Integer> sizeHistogram = new HashMap<>();
        private List<Line> paragraph = new ArrayList<>();
        private final StringBuilder line = new StringBuilder();
        private double sizeSum;
        private int chars;
        private int boldChars;
        private int page;

        Collector() throws IOException {
            super();
            setSortByPosition(false);
        }

        @Override
        protected void startPage(PDPage p) throws IOException {
            page++;
            super.startPage(p);
        }

        @Override
        protected void endPage(PDPage p) throws IOException {
            flushLine();
            endParagraph();
            super.endPage(p);
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            line.append(text);
            for (TextPosition tp : positions) {
                float s = tp.getFontSizeInPt();
                if (s <= 0.5f) {
                    s = tp.getHeight();
                }
                sizeSum += s;
                chars++;
                if (isBold(tp.getFont())) {
                    boldChars++;
                }
            }
        }

        @Override
        protected void writeWordSeparator() {
            line.append(' ');
        }

        @Override
        protected void writeLineSeparator() {
            flushLine();
        }

        @Override
        protected void writeParagraphEnd() {
            flushLine();
            endParagraph();
        }

        private void flushLine() {
            String text = line.toString().strip();
            if (!text.isEmpty() && chars > 0) {
                double avg = sizeSum / chars;
                paragraph.add(new Line(text, avg, boldChars / (double) chars, page));
                sizeHistogram.merge((int) Math.round(avg * 2), text.length(), Integer::sum);
            }
            line.setLength(0);
            sizeSum = 0;
            chars = 0;
            boldChars = 0;
        }

        private void endParagraph() {
            if (!paragraph.isEmpty()) {
                paragraphs.add(paragraph);
                paragraph = new ArrayList<>();
            }
        }

        double bodySize() {
            return sizeHistogram.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(e -> e.getKey() / 2.0)
                    .orElse(11.0);
        }

        private static boolean isBold(PDFont font) {
            if (font == null) {
                return false;
            }
            String name = font.getName();
            if (name != null) {
                String n = name.toLowerCase(Locale.ROOT);
                if (n.contains("bold") || n.contains("black") || n.contains("heavy") || n.contains("semibold")
                        || n.contains("demi") || n.contains("medium,bold")) {
                    return true;
                }
            }
            PDFontDescriptor d = font.getFontDescriptor();
            return d != null && (d.isForceBold() || d.getFontWeight() >= 600);
        }
    }

    // ---------------------------------------------------------------- classification

    private static List<Block> classify(List<List<Line>> paragraphs, double body) {
        List<Block> out = new ArrayList<>();
        int lastPage = 1;
        for (List<Line> para : paragraphs) {
            int page = para.get(0).page();
            if (page != lastPage) {
                out.add(new Block(Type.PAGE_BREAK, "", 0, null, page));
                lastPage = page;
            }
            int heading = headingLevel(para, body);
            if (heading > 0) {
                out.add(new Block(Type.HEADING, joinLines(para, 0, para.size()), heading, null, page));
                continue;
            }
            splitLists(para, out);
        }
        return out;
    }

    private static int headingLevel(List<Line> para, double body) {
        if (para.size() > 3) {
            return 0;
        }
        int len = 0;
        double weighted = 0;
        double bold = 0;
        for (Line l : para) {
            len += l.text().length();
            weighted += l.size() * l.text().length();
            bold += l.boldRatio() * l.text().length();
        }
        if (len == 0 || len > 150) {
            return 0;
        }
        double ratio = (weighted / len) / body;
        if (ratio >= 1.75) {
            return 1;
        }
        if (ratio >= 1.35) {
            return 2;
        }
        if (ratio >= 1.15) {
            return 3;
        }
        String text = para.get(0).text();
        char last = text.charAt(text.length() - 1);
        if (para.size() == 1 && bold / len >= 0.9 && len <= 80 && ratio >= 0.95
                && ".。:：,，;；".indexOf(last) < 0
                && !BULLET.matcher(text).matches()) {
            return 4;
        }
        return 0;
    }

    /** Emits list items for bullet / numbered lines and paragraphs for everything else. */
    private static void splitLists(List<Line> para, List<Block> out) {
        int page = para.get(0).page();
        StringBuilder plain = null;
        String itemText = null;
        String itemMarker = null;
        for (Line l : para) {
            Matcher b = BULLET.matcher(l.text());
            Matcher o = ORDERED.matcher(l.text());
            boolean isBullet = b.matches();
            boolean isOrdered = !isBullet && o.matches();
            if (isBullet || isOrdered) {
                if (plain != null) {
                    out.add(new Block(Type.PARAGRAPH, plain.toString(), 0, null, page));
                    plain = null;
                }
                if (itemText != null) {
                    out.add(new Block(Type.LIST_ITEM, itemText, 0, itemMarker, page));
                }
                itemText = isBullet ? b.group(1).strip() : o.group(2).strip();
                itemMarker = isBullet ? null : normalizeMarker(o.group(1));
            } else if (itemText != null) {
                itemText = join(itemText, l.text());
            } else {
                plain = plain == null ? new StringBuilder(l.text()) : new StringBuilder(join(plain.toString(), l.text()));
            }
        }
        if (itemText != null) {
            out.add(new Block(Type.LIST_ITEM, itemText, 0, itemMarker, page));
        }
        if (plain != null) {
            out.add(new Block(Type.PARAGRAPH, plain.toString(), 0, null, page));
        }
    }

    private static String normalizeMarker(String m) {
        String digits = m.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? m.strip() : digits + ".";
    }

    private static String joinLines(List<Line> lines, int from, int to) {
        String acc = lines.get(from).text();
        for (int i = from + 1; i < to; i++) {
            acc = join(acc, lines.get(i).text());
        }
        return acc;
    }

    /** Joins two visual lines: no space between CJK characters, de-hyphenates Latin words. */
    static String join(String a, String b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        int last = a.codePointBefore(a.length());
        int first = b.codePointAt(0);
        if (last == '-' && a.length() >= 2 && Character.isLetter(a.charAt(a.length() - 2)) && Character.isLowerCase(first)) {
            return a.substring(0, a.length() - 1) + b;
        }
        if (isCjk(last) || isCjk(first)) {
            return a + b;
        }
        return a + " " + b;
    }

    static boolean isCjk(int cp) {
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        if (script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL
                || script == Character.UnicodeScript.BOPOMOFO) {
            return true;
        }
        Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
        return block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }
}
