package me.rtx4090.discordpdf.util;

import me.rtx4090.discordpdf.core.UserFacingException;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses page selections such as {@code "1-3, 5, 8-"} (1-based, inclusive).
 * An open end ({@code "8-"}) means "to the last page"; {@code "-3"} means "from page 1".
 */
public final class PageRanges {

    /** A 1-based inclusive range. */
    public record Range(int from, int to) {
        public int size() {
            return to - from + 1;
        }

        public String label() {
            return from == to ? "p" + from : "p" + from + "-" + to;
        }
    }

    private PageRanges() {
    }

    public static List<Range> parse(String spec, int pageCount) {
        if (spec == null || spec.isBlank()) {
            throw new UserFacingException("error.invalid_ranges");
        }
        List<Range> out = new ArrayList<>();
        String normalized = spec.replaceAll("\\s*[-–~－]\\s*", "-");
        for (String raw : normalized.split("[,;，、\\s]+")) {
            String part = raw.trim().replace('–', '-').replace('~', '-').replace('－', '-');
            if (part.isEmpty()) {
                continue;
            }
            int from;
            int to;
            try {
                int dash = part.indexOf('-');
                if (dash < 0) {
                    from = to = Integer.parseInt(part);
                } else {
                    String a = part.substring(0, dash).trim();
                    String b = part.substring(dash + 1).trim();
                    from = a.isEmpty() ? 1 : Integer.parseInt(a);
                    to = b.isEmpty() ? pageCount : Integer.parseInt(b);
                }
            } catch (NumberFormatException e) {
                throw new UserFacingException("error.invalid_ranges");
            }
            if (from < 1 || to < from) {
                throw new UserFacingException("error.invalid_ranges");
            }
            if (to > pageCount) {
                throw new UserFacingException("error.range_out_of_bounds", pageCount);
            }
            out.add(new Range(from, to));
        }
        if (out.isEmpty()) {
            throw new UserFacingException("error.invalid_ranges");
        }
        if (out.size() > 200) {
            throw new UserFacingException("error.too_many_ranges", 200);
        }
        return out;
    }

    /** Fixed-size chunks: every {@code n} pages. */
    public static List<Range> every(int n, int pageCount) {
        if (n < 1) {
            throw new UserFacingException("error.invalid_ranges");
        }
        List<Range> out = new ArrayList<>();
        for (int start = 1; start <= pageCount; start += n) {
            out.add(new Range(start, Math.min(pageCount, start + n - 1)));
        }
        return out;
    }

    /** Flattens ranges into a list of 0-based page indexes, keeping order and duplicates. */
    public static List<Integer> indexes(List<Range> ranges) {
        List<Integer> out = new ArrayList<>();
        for (Range r : ranges) {
            for (int p = r.from(); p <= r.to(); p++) {
                out.add(p - 1);
            }
        }
        return out;
    }
}
