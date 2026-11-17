package me.rtx4090.discordpdf.core;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreTest {

    @Test
    void safeBaseNameStripsPathsAndOddCharacters() {
        assertEquals("report_2024", Names.safeBaseName("report 2024.pdf"));
        assertEquals("passwd", Names.safeBaseName("../../etc/passwd"));
        assertEquals("報告書_最終版", Names.safeBaseName("報告書 最終版.docx"));
        assertEquals("document", Names.safeBaseName("..."));
        assertEquals("document", Names.safeBaseName(null));
        assertEquals("ab", Names.safeBaseName("a\u0000b\n.pdf"));
        assertEquals(60, Names.safeBaseName("x".repeat(200) + ".pdf").length());
    }

    @Test
    void sniffsByContentNotName() throws Exception {
        Path dir = Files.createTempDirectory("sniff");
        try {
            Path pdf = Files.write(dir.resolve("a.jpg"), "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII));
            assertEquals(FileKind.PDF, FileTypes.sniff(pdf, "a.jpg"));

            Path jpg = Files.write(dir.resolve("b"), new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
            assertEquals(FileKind.JPEG, FileTypes.sniff(jpg, "b.pdf"));

            Path md = Files.writeString(dir.resolve("c.md"), "# 標題\n\n內容 text", StandardCharsets.UTF_8);
            assertEquals(FileKind.TEXT, FileTypes.sniff(md, "c.md"));

            Path bin = Files.write(dir.resolve("d.md"), new byte[]{1, 0, 2, 3});
            assertEquals(FileKind.UNKNOWN, FileTypes.sniff(bin, "d.md"));

            Path docx = dir.resolve("e.zip");
            try (OutputStream os = Files.newOutputStream(docx); ZipOutputStream zip = new ZipOutputStream(os)) {
                zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("word/document.xml"));
                zip.closeEntry();
            }
            assertEquals(FileKind.DOCX, FileTypes.sniff(docx, "e.zip"));

            byte[] ole = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0};
            Path doc = Files.write(dir.resolve("f"), ole);
            assertEquals(FileKind.DOC, FileTypes.sniff(doc, "old.DOC"));
            assertEquals(FileKind.PPT, FileTypes.sniff(doc, "old.ppt"));
            assertEquals(FileKind.OLE2, FileTypes.sniff(doc, "noext"));
        } finally {
            try (var s = Files.walk(dir)) {
                s.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    void workspaceIsDeletedOnClose() throws Exception {
        Path base = Files.createTempDirectory("ws");
        Path dir;
        try (Workspace ws = Workspace.create(base)) {
            dir = ws.dir();
            Files.writeString(ws.newFile(".txt"), "secret");
            Files.writeString(ws.newDir("sub").resolve("x"), "secret");
            assertTrue(Files.exists(dir));
        }
        assertFalse(Files.exists(dir));
        Files.delete(base);
    }

    @Test
    void everyMessageKeyExistsInBothLanguagesAndFormats() throws Exception {
        Properties en = load("/i18n/messages.properties");
        Properties zh = load("/i18n/messages_zh_TW.properties");
        assertEquals(en.keySet(), zh.keySet(), "both bundles must define the same keys");
        Object[] args = {"a", "b", "c", "d"};
        for (Properties p : new Properties[]{en, zh}) {
            for (String key : p.stringPropertyNames()) {
                String pattern = p.getProperty(key);
                assertFalse(pattern.contains("'"), key + " must not contain an apostrophe");
                new MessageFormat(pattern, Locale.ROOT).format(args); // throws on a broken pattern
            }
        }
    }

    @Test
    void i18nResolvesNestedKeysAndLocales() {
        assertEquals("“x.txt” is not a PDF.", I18n.get(Locale.ENGLISH, "error.wrong_type", "x.txt", "@kind.pdf"));
        assertEquals("「x.txt」不是PDF 檔。", I18n.get(Locale.TRADITIONAL_CHINESE, "error.wrong_type", "x.txt", "@kind.pdf"));
        assertEquals(Locale.TRADITIONAL_CHINESE, I18n.fromTag("zh-TW"));
        assertEquals(Locale.ENGLISH, I18n.fromTag("fr"));
        assertEquals("1.50 MB", I18n.size(1572864));
    }

    private static Properties load(String resource) throws Exception {
        Properties p = new Properties();
        try (InputStream in = CoreTest.class.getResourceAsStream(resource)) {
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }
}
