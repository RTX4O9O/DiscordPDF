package me.rtx4090.discordpdf.convert;

import java.util.List;

/** Serialises {@link PdfStructure.Block}s as GitHub-flavoured Markdown. */
public final class MarkdownWriter {

    private MarkdownWriter() {
    }

    public static String write(List<PdfStructure.Block> blocks, boolean pageMarkers) {
        StringBuilder md = new StringBuilder();
        boolean inList = false;
        for (PdfStructure.Block b : blocks) {
            if (inList && b.type() != PdfStructure.Type.LIST_ITEM) {
                md.append('\n');
                inList = false;
            }
            switch (b.type()) {
                case HEADING -> md.append("#".repeat(b.level())).append(' ')
                        .append(escapeInline(b.text())).append("\n\n");
                case PARAGRAPH -> md.append(escapeLeading(escapeInline(b.text()))).append("\n\n");
                case LIST_ITEM -> {
                    md.append(b.marker() == null ? "-" : b.marker()).append(' ')
                            .append(escapeInline(b.text())).append('\n');
                    inList = true;
                }
                case PAGE_BREAK -> {
                    if (pageMarkers) {
                        md.append("---\n\n<!-- page ").append(b.page()).append(" -->\n\n");
                    }
                }
            }
        }
        String out = md.toString().replaceAll("\n{3,}", "\n\n").strip();
        return out.isEmpty() ? "" : out + "\n";
    }

    /** Escapes characters that would otherwise start Markdown inline formatting or raw HTML. */
    static String escapeInline(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\', '`', '*', '_', '[', ']', '<', '>', '|' -> sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Escapes characters that would turn a plain paragraph into a heading, list or quote. */
    static String escapeLeading(String s) {
        if (s.isEmpty()) {
            return s;
        }
        char c = s.charAt(0);
        if (c == '#' || c == '+' || c == '-' || c == '=' || c == '~') {
            return "\\" + s;
        }
        int i = 0;
        while (i < s.length() && i < 9 && Character.isDigit(s.charAt(i))) {
            i++;
        }
        if (i > 0 && i < s.length() && (s.charAt(i) == '.' || s.charAt(i) == ')')) {
            return s.substring(0, i) + "\\" + s.substring(i);
        }
        return s;
    }
}
