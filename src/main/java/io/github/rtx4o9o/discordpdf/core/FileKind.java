package io.github.rtx4o9o.discordpdf.core;

/** File types recognised from their content (magic bytes), not from the name the user gave. */
public enum FileKind {
    PDF(".pdf"),
    DOCX(".docx"),
    DOC(".doc"),
    PPTX(".pptx"),
    PPT(".ppt"),
    /** An OLE2 container we could not narrow down to .doc or .ppt. */
    OLE2(".bin"),
    JPEG(".jpg"),
    PNG(".png"),
    GIF(".gif"),
    BMP(".bmp"),
    WEBP(".webp"),
    TEXT(".md"),
    UNKNOWN(".bin");

    private final String extension;

    FileKind(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public boolean isImage() {
        return this == JPEG || this == PNG || this == GIF || this == BMP || this == WEBP;
    }
}
