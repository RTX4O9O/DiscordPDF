package io.github.rtx4o9o.discordpdf.convert;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.List;

/** Markdown (CommonMark + GFM tables, strikethrough, autolinks) to an HTML fragment. */
public final class MarkdownHtml {

    private static final List<Extension> EXTENSIONS = List.of(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            AutolinkExtension.create());

    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();

    /** Raw HTML in the Markdown is escaped, not passed through; unsafe link schemes are removed. */
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .escapeHtml(true)
            .sanitizeUrls(true)
            .build();

    private MarkdownHtml() {
    }

    public static String toHtml(String markdown) {
        String text = markdown.startsWith("﻿") ? markdown.substring(1) : markdown;
        Node document = PARSER.parse(text);
        return RENDERER.render(document);
    }
}
