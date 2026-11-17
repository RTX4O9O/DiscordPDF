package me.rtx4090.discordpdf.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Packs several outputs into one zip. Entry timestamps are fixed so no clock data leaks. */
public final class ZipOut {

    /** A file to pack and the name it should have inside the archive. */
    public record Entry(Path file, String name) {
    }

    private static final FileTime FIXED_TIME = FileTime.fromMillis(315532800000L); // 1980-01-01

    private ZipOut() {
    }

    public static void write(Path target, List<Entry> entries) throws IOException {
        Set<String> used = new HashSet<>();
        try (OutputStream os = Files.newOutputStream(target); ZipOutputStream zip = new ZipOutputStream(os)) {
            for (Entry e : entries) {
                String name = unique(e.name(), used);
                ZipEntry ze = new ZipEntry(name);
                ze.setLastModifiedTime(FIXED_TIME);
                zip.putNextEntry(ze);
                Files.copy(e.file(), zip);
                zip.closeEntry();
            }
        }
    }

    private static String unique(String name, Set<String> used) {
        if (used.add(name)) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; ; i++) {
            String candidate = stem + "_" + i + ext;
            if (used.add(candidate)) {
                return candidate;
            }
        }
    }
}
