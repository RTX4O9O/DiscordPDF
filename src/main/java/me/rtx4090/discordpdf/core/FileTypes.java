package me.rtx4090.discordpdf.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Detects a file's real type from its first bytes. The file name is only used as a tie-breaker. */
public final class FileTypes {

    private static final int HEAD = 1024;

    private FileTypes() {
    }

    public static FileKind sniff(Path file, String nameHint) throws IOException {
        byte[] head = readHead(file);
        String ext = extensionOf(nameHint);

        if (indexOf(head, "%PDF-".getBytes(StandardCharsets.US_ASCII)) >= 0) {
            return FileKind.PDF;
        }
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) {
            return FileKind.JPEG;
        }
        if (startsWith(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return FileKind.PNG;
        }
        if (startsWith(head, 'G', 'I', 'F', '8')) {
            return FileKind.GIF;
        }
        if (startsWith(head, 'B', 'M')) {
            return FileKind.BMP;
        }
        if (head.length >= 12 && startsWith(head, 'R', 'I', 'F', 'F')
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return FileKind.WEBP;
        }
        if (startsWith(head, 'P', 'K', 0x03, 0x04)) {
            return sniffZip(file);
        }
        if (startsWith(head, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
            return switch (ext) {
                case "doc", "dot" -> FileKind.DOC;
                case "ppt", "pps", "pot" -> FileKind.PPT;
                default -> FileKind.OLE2;
            };
        }
        if (looksLikeText(file)) {
            return FileKind.TEXT;
        }
        return FileKind.UNKNOWN;
    }

    private static FileKind sniffZip(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int seen = 0;
            while (entries.hasMoreElements() && seen++ < 5000) {
                String name = entries.nextElement().getName();
                if (name.equals("word/document.xml")) {
                    return FileKind.DOCX;
                }
                if (name.equals("ppt/presentation.xml")) {
                    return FileKind.PPTX;
                }
            }
        } catch (IOException ignored) {
            // not a readable zip
        }
        return FileKind.UNKNOWN;
    }

    /** Valid UTF-8 without NUL bytes in the first 64 KiB. */
    private static boolean looksLikeText(Path file) throws IOException {
        byte[] buf;
        try (InputStream in = Files.newInputStream(file)) {
            buf = in.readNBytes(64 * 1024);
        }
        if (buf.length == 0) {
            return false;
        }
        for (byte b : buf) {
            if (b == 0) {
                return false;
            }
        }
        // If we only read a prefix, a multi-byte sequence may be cut at the end: decode with
        // endOfInput=false so that case is not reported as an error.
        boolean wholeFile = buf.length < 64 * 1024;
        CoderResult result = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(buf), CharBuffer.allocate(buf.length), wholeFile);
        return !result.isError();
    }

    public static String extensionOf(String name) {
        if (name == null) {
            return "";
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String base = name.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        return dot < 0 ? "" : base.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] readHead(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(HEAD);
        }
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(byte[] data, byte[] needle) {
        outer:
        for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
