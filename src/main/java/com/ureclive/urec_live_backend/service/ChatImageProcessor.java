package com.ureclive.urec_live_backend.service;

import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Turns an uploaded JPEG into the copy chat stores: decoded and re-encoded with ImageIO, which drops
 * every metadata segment (EXIF, GPS, camera details, embedded thumbnails), with the long edge capped
 * at {@link #MAX_LONG_EDGE}. The EXIF orientation is applied to the pixels first, so a phone photo
 * still shows upright once the tag is gone.
 */
@Component
public class ChatImageProcessor {

    public static final int MAX_LONG_EDGE = 2048;
    /** Checked from the header before decoding, so a small file can't claim a huge image. */
    static final long MAX_PIXELS = 50_000_000L;
    static final float QUALITY = 0.85f;

    public record ProcessedImage(byte[] jpeg, int width, int height) {}

    /** The upload couldn't be read as a photo. */
    public static class InvalidImageException extends Exception {
        public InvalidImageException(String message) {
            super(message);
        }

        public InvalidImageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** JPEG files start with the SOI marker FF D8, then another marker. */
    public static boolean looksLikeJpeg(byte[] bytes) {
        return bytes != null && bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
    }

    public ProcessedImage process(byte[] jpeg) throws InvalidImageException {
        if (!looksLikeJpeg(jpeg)) throw new InvalidImageException("Not a JPEG");
        BufferedImage decoded = decode(jpeg);
        BufferedImage upright = orient(fit(decoded), ExifOrientation.read(jpeg));
        return new ProcessedImage(encode(upright), upright.getWidth(), upright.getHeight());
    }

    private static BufferedImage decode(byte[] jpeg) throws InvalidImageException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
        if (!readers.hasNext()) throw new IllegalStateException("No JPEG reader available");
        ImageReader reader = readers.next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(jpeg))) {
            // Metadata is never read, so none of it can reach the copy that's stored
            reader.setInput(in, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                throw new InvalidImageException("Image is " + width + "x" + height);
            }
            ImageReadParam param = reader.getDefaultReadParam();
            // Decode big photos at a fraction of their size (never below the cap), which saves most of the memory
            int step = Math.max(width, height) / MAX_LONG_EDGE;
            if (step > 1) param.setSourceSubsampling(step, step, 0, 0);
            BufferedImage image = reader.read(0, param);
            if (image == null) throw new InvalidImageException("Nothing decoded");
            return image;
        } catch (IOException | RuntimeException e) {
            // ImageIO reports corrupt or unsupported files (CMYK, truncated) both ways
            throw new InvalidImageException("Couldn't decode the photo", e);
        } finally {
            reader.dispose();
        }
    }

    /** Scales the image down to fit MAX_LONG_EDGE, as plain RGB (which also flattens grayscale). */
    static BufferedImage fit(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        double scale = Math.min(1.0, (double) MAX_LONG_EDGE / Math.max(width, height));
        int newWidth = Math.max(1, (int) Math.round(width * scale));
        int newHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage out = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, newWidth, newHeight, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /**
     * Applies an EXIF orientation (1 to 8) to the pixels. 1 is upright; 6 means the camera was turned
     * a quarter clockwise, so the picture is rotated 90° clockwise to show it upright; and so on.
     */
    static BufferedImage orient(BufferedImage image, int orientation) {
        if (orientation < 2 || orientation > 8) return image;
        int w = image.getWidth();
        int h = image.getHeight();
        boolean swaps = orientation >= 5;
        AffineTransform t = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // mirror left-right
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // rotate 180°
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // mirror top-bottom
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // transpose
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // rotate 90° clockwise
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);  // transverse
            default -> new AffineTransform(0, -1, 1, 0, 0, w);  // 8: rotate 90° counterclockwise
        };
        BufferedImage out = new BufferedImage(swaps ? h : w, swaps ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(image, t, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] encode(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IllegalStateException("No JPEG writer available");
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            // No metadata passed in: the writer adds only its plain JFIF header
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't encode the photo", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** Reads the orientation tag from a JPEG's EXIF segment; 1 (upright) when there is none or it can't be read. */
    static final class ExifOrientation {
        private static final int ORIENTATION_TAG = 0x0112;

        private ExifOrientation() {}

        static int read(byte[] jpeg) {
            try {
                int pos = 2; // after SOI
                while (pos + 4 <= jpeg.length) {
                    if ((jpeg[pos] & 0xFF) != 0xFF) return 1;
                    int marker = jpeg[pos + 1] & 0xFF;
                    if (marker == 0xFF) { pos++; continue; } // fill byte
                    // Start of scan or end of image: no more metadata segments
                    if (marker == 0xDA || marker == 0xD9) return 1;
                    int length = u16(jpeg, pos + 2, false);
                    if (length < 2) return 1;
                    int start = pos + 4;
                    if (marker == 0xE1 && isExif(jpeg, start, length - 2)) {
                        return fromTiff(jpeg, start + 6, start + length - 2);
                    }
                    pos += 2 + length;
                }
            } catch (IndexOutOfBoundsException e) {
                // A truncated or malformed segment: treat it as having no orientation
            }
            return 1;
        }

        private static boolean isExif(byte[] b, int start, int length) {
            return length >= 6 && b[start] == 'E' && b[start + 1] == 'x' && b[start + 2] == 'i'
                    && b[start + 3] == 'f' && b[start + 4] == 0 && b[start + 5] == 0;
        }

        /** Looks the tag up in IFD0 of the TIFF structure between tiff and end. */
        private static int fromTiff(byte[] b, int tiff, int end) {
            boolean little;
            if (b[tiff] == 'I' && b[tiff + 1] == 'I') little = true;
            else if (b[tiff] == 'M' && b[tiff + 1] == 'M') little = false;
            else return 1;
            if (u16(b, tiff + 2, little) != 42) return 1;
            long ifd = u32(b, tiff + 4, little);
            if (ifd < 8 || tiff + ifd + 2 > end) return 1;
            int entries = u16(b, (int) (tiff + ifd), little);
            for (int i = 0; i < entries; i++) {
                int entry = (int) (tiff + ifd + 2 + 12L * i);
                if (entry + 12 > end) return 1;
                if (u16(b, entry, little) == ORIENTATION_TAG) {
                    int type = u16(b, entry + 2, little);
                    if (type != 3) return 1; // SHORT
                    int value = u16(b, entry + 8, little);
                    return value >= 1 && value <= 8 ? value : 1;
                }
            }
            return 1;
        }

        private static int u16(byte[] b, int at, boolean little) {
            int a = b[at] & 0xFF;
            int c = b[at + 1] & 0xFF;
            return little ? (c << 8) | a : (a << 8) | c;
        }

        private static long u32(byte[] b, int at, boolean little) {
            long v = 0;
            for (int i = 0; i < 4; i++) {
                int shift = little ? 8 * i : 8 * (3 - i);
                v |= (long) (b[at + i] & 0xFF) << shift;
            }
            return v;
        }
    }
}
