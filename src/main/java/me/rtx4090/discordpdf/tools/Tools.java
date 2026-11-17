package me.rtx4090.discordpdf.tools;

import me.rtx4090.discordpdf.core.PdfTool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All available tools, keyed by sub-command name, in menu order. */
public final class Tools {

    private Tools() {
    }

    public static Map<String, PdfTool> all() {
        Map<String, PdfTool> map = new LinkedHashMap<>();
        for (PdfTool t : List.of(
                new MergeTool(),
                new SplitTool(),
                new CompressTool(),
                new PdfToMarkdownTool(),
                new MarkdownToPdfTool(),
                new PdfToWordTool(),
                new PdfToPptTool(),
                new WordToPdfTool(),
                new PptToPdfTool(),
                new PdfToJpgTool(),
                new ImagesToPdfTool())) {
            map.put(t.id(), t);
        }
        return map;
    }
}
