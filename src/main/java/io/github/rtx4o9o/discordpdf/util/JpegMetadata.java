package io.github.rtx4o9o.discordpdf.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Removes privacy-sensitive metadata (EXIF incl. GPS position, camera serial numbers,
 * XMP, IPTC, comments) from a JPEG without re-encoding it, and reads the EXIF orientation
 * first so the picture can still be shown the right way up.
 *
 * <p>Kept segments: APP0 (JFIF), APP2 only when it is an ICC colour profile, APP14 (Adobe,
 * needed for CMYK) and everything from the start of the image data on.
 */
public final class JpegMetadata {

    /** Stripped bytes plus the EXIF orientation (1 = normal, 3 = 180°, 6 = 90° CW, 8 = 270° CW). */
    public record Result(byte[] jpeg, int orientation) {
    }

    private static final byte[] EXIF_HEADER = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] ICC_HEADER = "ICC_PROFILE\0".getBytes(StandardCharsets.ISO_8859_1);

    private JpegMetadata() {
    }

    public static Result strip(byte[] in) {
        if (in.length < 4 || (in[0] & 0xFF) != 0xFF || (in[1] & 0xFF) != 0xD8) {
            return new Result(in, 1); // not a JPEG; leave untouched
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(0xFF);
        out.write(0xD8);
        int orientation = 1;
        int pos = 2;
        while (pos + 4 <= in.length) {
            if ((in[pos] & 0xFF) != 0xFF) {
                return new Result(in, orientation); // malformed; keep the original
            }
            int marker = in[pos + 1] & 0xFF;
            if (marker == 0xFF) { // fill byte
                pos++;
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) {
                // Start of scan / end of image: copy the rest verbatim.
                out.write(in, pos, in.length - pos);
                return new Result(out.toByteArray(), orientation);
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                out.write(in, pos, 2);
                pos += 2;
                continue;
            }
            int len = ((in[pos + 2] & 0xFF) << 8) | (in[pos + 3] & 0xFF);
            if (len < 2 || pos + 2 + len > in.length) {
                return new Result(in, orientation);
            }
            int dataStart = pos + 4;
            int dataLen = len - 2;
            boolean keep;
            if (marker == 0xE1) { // APP1: EXIF or XMP
                if (startsWith(in, dataStart, dataLen, EXIF_HEADER)) {
                    orientation = readOrientation(in, dataStart + EXIF_HEADER.length, dataLen - EXIF_HEADER.length);
                }
                keep = false;
            } else if (marker == 0xE2) { // APP2: keep ICC profile only
                keep = startsWith(in, dataStart, dataLen, ICC_HEADER);
            } else if (marker == 0xE0 || marker == 0xEE) { // JFIF, Adobe
                keep = true;
            } else if (marker >= 0xE3 && marker <= 0xEF) { // other APPn (IPTC, maker data...)
                keep = false;
            } else if (marker == 0xFE) { // comment
                keep = false;
            } else {
                keep = true; // DQT, SOFn, DHT, DRI ...
            }
            if (keep) {
                out.write(in, pos, 2 + len);
            }
            pos += 2 + len;
        }
        return new Result(in, orientation);
    }

    private static boolean startsWith(byte[] data, int off, int len, byte[] prefix) {
        if (len < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[off + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Reads tag 0x0112 from IFD0 of a TIFF structure. */
    static int readOrientation(byte[] d, int tiff, int len) {
        try {
            if (len < 8) {
                return 1;
            }
            boolean little = d[tiff] == 'I' && d[tiff + 1] == 'I';
            int ifd = u32(d, tiff + 4, little);
            if (ifd < 8 || ifd + 2 > len) {
                return 1;
            }
            int count = u16(d, tiff + ifd, little);
            for (int i = 0; i < count; i++) {
                int entry = tiff + ifd + 2 + i * 12;
                if (entry + 12 > tiff + len) {
                    return 1;
                }
                if (u16(d, entry, little) == 0x0112) {
                    int v = u16(d, entry + 8, little);
                    return (v >= 1 && v <= 8) ? v : 1;
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            return 1;
        }
        return 1;
    }

    private static int u16(byte[] d, int off, boolean little) {
        int a = d[off] & 0xFF;
        int b = d[off + 1] & 0xFF;
        return little ? (b << 8) | a : (a << 8) | b;
    }

    private static int u32(byte[] d, int off, boolean little) {
        int a = d[off] & 0xFF;
        int b = d[off + 1] & 0xFF;
        int c = d[off + 2] & 0xFF;
        int e = d[off + 3] & 0xFF;
        return little ? (e << 24) | (c << 16) | (b << 8) | a : (a << 24) | (b << 16) | (c << 8) | e;
    }

    /** Clockwise rotation in degrees needed to display an image with this EXIF orientation. */
    public static int rotationFor(int orientation) {
        return switch (orientation) {
            case 3, 4 -> 180;
            case 5, 6 -> 90;
            case 7, 8 -> 270;
            default -> 0;
        };
    }
}
