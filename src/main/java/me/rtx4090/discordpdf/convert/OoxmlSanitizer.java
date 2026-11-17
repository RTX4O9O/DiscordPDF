package me.rtx4090.discordpdf.convert;

import me.rtx4090.discordpdf.core.UserFacingException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Removes external references from .docx / .pptx files before they are handed to LibreOffice.
 *
 * <p>OOXML parts can point at resources outside the file ({@code TargetMode="External"}):
 * linked pictures, OLE objects, frames, attached templates. LibreOffice may fetch them while
 * rendering, which would let a crafted document make the bot request internal URLs (SSRF) or
 * reveal the server's IP address. Hyperlinks are kept: they are only written into the PDF,
 * never fetched.
 */
public final class OoxmlSanitizer {

    private static final Pattern RELATIONSHIP = Pattern.compile(
            "<Relationship\\b[^>]*?(?:/>|>\\s*</Relationship>)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXTERNAL = Pattern.compile("TargetMode\\s*=\\s*[\"']External[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern HYPERLINK = Pattern.compile("Type\\s*=\\s*[\"'][^\"']*/hyperlink[\"']", Pattern.CASE_INSENSITIVE);

    private static final long MAX_RELS_BYTES = 8L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 1024L * 1024 * 1024;
    private static final int MAX_ENTRIES = 20_000;

    private OoxmlSanitizer() {
    }

    /** Copies {@code in} to {@code out} without external relationships; returns how many were removed. */
    public static int sanitize(Path in, Path out) throws IOException {
        int removed = 0;
        long total = 0;
        int entries = 0;
        try (ZipFile zip = new ZipFile(in.toFile());
             OutputStream os = Files.newOutputStream(out);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            Enumeration<? extends ZipEntry> it = zip.entries();
            byte[] buf = new byte[64 * 1024];
            while (it.hasMoreElements()) {
                if (++entries > MAX_ENTRIES) {
                    throw new UserFacingException("error.office_failed");
                }
                ZipEntry entry = it.nextElement();
                zos.putNextEntry(new ZipEntry(entry.getName()));
                try (InputStream is = zip.getInputStream(entry)) {
                    if (entry.getName().endsWith(".rels")) {
                        byte[] data = is.readNBytes((int) MAX_RELS_BYTES + 1);
                        if (data.length > MAX_RELS_BYTES) {
                            throw new UserFacingException("error.office_failed");
                        }
                        total += data.length;
                        String xml = new String(data, StandardCharsets.UTF_8);
                        StringBuilder cleaned = new StringBuilder(xml.length());
                        Matcher m = RELATIONSHIP.matcher(xml);
                        int last = 0;
                        while (m.find()) {
                            String rel = m.group();
                            if (EXTERNAL.matcher(rel).find() && !HYPERLINK.matcher(rel).find()) {
                                cleaned.append(xml, last, m.start());
                                last = m.end();
                                removed++;
                            }
                        }
                        cleaned.append(xml, last, xml.length());
                        zos.write(cleaned.toString().getBytes(StandardCharsets.UTF_8));
                    } else {
                        int n;
                        while ((n = is.read(buf)) >= 0) {
                            total += n;
                            if (total > MAX_TOTAL_BYTES) {
                                throw new UserFacingException("error.office_failed"); // zip bomb
                            }
                            zos.write(buf, 0, n);
                        }
                    }
                }
                zos.closeEntry();
            }
        }
        return removed;
    }
}
