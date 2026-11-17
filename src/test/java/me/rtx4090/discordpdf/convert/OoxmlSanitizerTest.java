package me.rtx4090.discordpdf.convert;

import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OoxmlSanitizerTest {

    @Test
    void removesExternalNonHyperlinkRelationshipsOnly() throws Exception {
        Path dir = Files.createTempDirectory("ooxml");
        Path in = dir.resolve("in.docx");
        Path out = dir.resolve("out.docx");
        String rels = "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\".../relationships/styles\" Target=\"styles.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\".../relationships/image\" Target=\"http://169.254.169.254/x.png\" TargetMode=\"External\"/>"
                + "<Relationship TargetMode='External' Id=\"rId3\" Type=\".../relationships/oleObject\" Target=\"file:///etc/passwd\"></Relationship>"
                + "<Relationship Id=\"rId4\" Type=\".../relationships/hyperlink\" Target=\"https://example.com/\" TargetMode=\"External\"/>"
                + "</Relationships>";
        try (OutputStream os = Files.newOutputStream(in); ZipOutputStream zip = new ZipOutputStream(os)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/_rels/document.xml.rels"));
            zip.write(rels.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertEquals(2, OoxmlSanitizer.sanitize(in, out));

        try (ZipFile zip = new ZipFile(out.toFile())) {
            String cleaned = new String(zip.getInputStream(zip.getEntry("word/_rels/document.xml.rels")).readAllBytes(),
                    StandardCharsets.UTF_8);
            assertTrue(cleaned.contains("styles.xml"));
            assertTrue(cleaned.contains("https://example.com/"));
            assertFalse(cleaned.contains("169.254.169.254"));
            assertFalse(cleaned.contains("/etc/passwd"));
            assertEquals("<w:document/>", new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(),
                    StandardCharsets.UTF_8));
        }
        Files.delete(in);
        Files.delete(out);
        Files.delete(dir);
    }
}
