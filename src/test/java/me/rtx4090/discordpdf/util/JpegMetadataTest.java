package me.rtx4090.discordpdf.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpegMetadataTest {

    /** A real JPEG with an EXIF segment (orientation + a fake GPS marker string) and a comment injected. */
    static byte[] jpegWithExif(int orientation, boolean bigEndian) throws Exception {
        BufferedImage img = new BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", bos);
        byte[] plain = bos.toByteArray();

        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        if (bigEndian) {
            tiff.writeBytes(new byte[]{'M', 'M', 0, 42, 0, 0, 0, 8});
            tiff.writeBytes(new byte[]{0, 1});                                    // 1 entry
            tiff.writeBytes(new byte[]{0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0});
        } else {
            tiff.writeBytes(new byte[]{'I', 'I', 42, 0, 8, 0, 0, 0});
            tiff.writeBytes(new byte[]{1, 0});
            tiff.writeBytes(new byte[]{0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientation, 0, 0, 0});
        }
        tiff.writeBytes(new byte[]{0, 0, 0, 0});                                  // next IFD
        tiff.writeBytes("GPS-SECRET-25.0330N-121.5654E".getBytes(StandardCharsets.US_ASCII));

        byte[] exifPayload = concat("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), tiff.toByteArray());
        byte[] app1 = segment(0xE1, exifPayload);
        byte[] com = segment(0xFE, "taken by Alice".getBytes(StandardCharsets.US_ASCII));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(plain, 0, 2);                 // SOI
        out.writeBytes(app1);
        out.writeBytes(com);
        out.write(plain, 2, plain.length - 2);  // rest of the original file
        return out.toByteArray();
    }

    private static byte[] segment(int marker, byte[] payload) {
        int len = payload.length + 2;
        return concat(new byte[]{(byte) 0xFF, (byte) marker, (byte) (len >> 8), (byte) len}, payload);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static boolean contains(byte[] hay, String needle) {
        return new String(hay, StandardCharsets.ISO_8859_1).contains(needle);
    }

    @Test
    void stripsExifAndCommentsAndReadsOrientation() throws Exception {
        for (boolean be : new boolean[]{true, false}) {
            byte[] in = jpegWithExif(6, be);
            assertTrue(contains(in, "GPS-SECRET"));
            JpegMetadata.Result r = JpegMetadata.strip(in);
            assertEquals(6, r.orientation());
            assertEquals(90, JpegMetadata.rotationFor(r.orientation()));
            assertFalse(contains(r.jpeg(), "GPS-SECRET"), "EXIF must be removed");
            assertFalse(contains(r.jpeg(), "Alice"), "comment must be removed");
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(r.jpeg()));
            assertNotNull(decoded, "result must still be a valid JPEG");
            assertEquals(16, decoded.getWidth());
        }
    }

    @Test
    void leavesNonJpegUntouched() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        JpegMetadata.Result r = JpegMetadata.strip(png);
        assertArrayEquals(png, r.jpeg());
        assertEquals(1, r.orientation());
    }

    @Test
    void rotationTable() {
        assertEquals(0, JpegMetadata.rotationFor(1));
        assertEquals(180, JpegMetadata.rotationFor(3));
        assertEquals(270, JpegMetadata.rotationFor(8));
    }
}
