package me.rtx4090.discordpdf.util;

import me.rtx4090.discordpdf.core.UserFacingException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/** Small image helpers built on the JDK only. */
public final class Images {

    /** Refuse decoding anything larger than this many pixels (decompression-bomb guard). */
    public static final long MAX_PIXELS = 60_000_000L;

    private Images() {
    }

    /** Reads only the header to get the dimensions, without decoding pixels. */
    public static Dimension dimensions(Path file) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(file.toFile())) {
            if (iis == null) {
                throw new UserFacingException("error.image_unreadable");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new UserFacingException("error.image_unreadable");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                return new Dimension(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
    }

    /** Decodes an image after checking its pixel count. */
    public static BufferedImage readChecked(Path file) throws IOException {
        Dimension d = dimensions(file);
        if ((long) d.width * d.height > MAX_PIXELS) {
            throw new UserFacingException("error.image_too_large");
        }
        BufferedImage img = ImageIO.read(file.toFile());
        if (img == null) {
            throw new UserFacingException("error.image_unreadable");
        }
        return img;
    }

    /** Encodes an RGB/gray image as baseline JPEG with the given quality (0..1). No metadata is written. */
    public static void writeJpeg(BufferedImage image, float quality, OutputStream out) throws IOException {
        BufferedImage rgb = toOpaque(image);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writer available");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    public static byte[] jpegBytes(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        writeJpeg(image, quality, bos);
        return bos.toByteArray();
    }

    public static void writeJpeg(BufferedImage image, float quality, Path target) throws IOException {
        try (OutputStream os = Files.newOutputStream(target)) {
            writeJpeg(image, quality, os);
        }
    }

    /** Flattens transparency onto white and converts to a type the JPEG writer accepts. */
    public static BufferedImage toOpaque(BufferedImage src) {
        int type = src.getType();
        if (type == BufferedImage.TYPE_INT_RGB || type == BufferedImage.TYPE_3BYTE_BGR
                || type == BufferedImage.TYPE_BYTE_GRAY) {
            return src;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** High-quality downscale; returns the source if no scaling is needed. */
    public static BufferedImage scale(BufferedImage src, double factor) {
        if (factor >= 0.999) {
            return src;
        }
        int w = Math.max(1, (int) Math.round(src.getWidth() * factor));
        int h = Math.max(1, (int) Math.round(src.getHeight() * factor));
        int type = src.getType() == BufferedImage.TYPE_BYTE_GRAY ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB;
        BufferedImage out = new BufferedImage(w, h, type);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
