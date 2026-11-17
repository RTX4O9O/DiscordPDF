package me.rtx4090.discordpdf.convert;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextConversionTest {

    @Test
    void joinsLatinWithSpaceCjkWithoutAndDehyphenates() {
        assertEquals("hello world", PdfStructure.join("hello", "world"));
        assertEquals("這是一段中文", PdfStructure.join("這是一段", "中文"));
        assertEquals("information", PdfStructure.join("infor-", "mation"));
        assertEquals("Covid- 19", PdfStructure.join("Covid-", "19"));
    }

    @Test
    void writesMarkdownWithEscaping() {
        List<PdfStructure.Block> blocks = List.of(
                new PdfStructure.Block(PdfStructure.Type.HEADING, "Intro *draft*", 1, null, 1),
                new PdfStructure.Block(PdfStructure.Type.PARAGRAPH, "# not a heading <b>", 0, null, 1),
                new PdfStructure.Block(PdfStructure.Type.LIST_ITEM, "first", 0, null, 1),
                new PdfStructure.Block(PdfStructure.Type.LIST_ITEM, "second", 0, "2.", 1),
                new PdfStructure.Block(PdfStructure.Type.PAGE_BREAK, "", 0, null, 2),
                new PdfStructure.Block(PdfStructure.Type.PARAGRAPH, "2024. A year", 0, null, 2));

        String expected = """
                # Intro \\*draft\\*

                \\# not a heading \\<b\\>

                - first
                2. second

                ---

                <!-- page 2 -->

                2024\\. A year
                """;
        assertEquals(expected, MarkdownWriter.write(blocks, true));
        String noMarkers = MarkdownWriter.write(blocks, false);
        assertEquals(false, noMarkers.contains("page 2"));
    }
}
