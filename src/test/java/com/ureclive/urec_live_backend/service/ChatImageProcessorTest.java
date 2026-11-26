package com.ureclive.urec_live_backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.ByteOrder;
import java.util.Arrays;

import static com.ureclive.urec_live_backend.service.ChatPhotoFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatImageProcessorTest {

    private final ChatImageProcessor processor = new ChatImageProcessor();

    // ── Metadata ───────────────────────────────────────────────────────────

    @Test
    void theStoredCopyHasNoExifOrGpsData() throws Exception {
        byte[] upload = withExif(jpeg(64, 48, Color.GREEN), 1);
        assertTrue(hasExif(upload), "fixture has EXIF");
        assertTrue(contains(upload, GPS_MARKER), "fixture has the GPS block");

        ChatImageProcessor.ProcessedImage out = processor.process(upload);

        assertFalse(hasExif(out.jpeg()));
        assertFalse(contains(out.jpeg(), GPS_MARKER));
        assertFalse(contains(out.jpeg(), "Exif"));
        assertTrue(ChatImageProcessor.looksLikeJpeg(out.jpeg()));
        BufferedImage decoded = decode(out.jpeg());
        assertEquals(64, decoded.getWidth());
        assertEquals(48, decoded.getHeight());
        assertEquals(64, out.width());
        assertEquals(48, out.height());
    }

    // ── Size ───────────────────────────────────────────────────────────────

    @Test
    void wideAndTallPhotosAreCappedAt2048OnTheLongEdge() throws Exception {
        ChatImageProcessor.ProcessedImage wide = processor.process(jpeg(3000, 1500, Color.GRAY));
        assertEquals(2048, wide.width());
        assertEquals(1024, wide.height());

        // Odd sizes go through subsampled decoding (4100 / 2048 = step 2) and then exact scaling
        ChatImageProcessor.ProcessedImage tall = processor.process(jpeg(1000, 4100, Color.GRAY));
        assertEquals(2048, tall.height());
        assertEquals(1000.0 / 4100, (double) tall.width() / tall.height(), 0.01);

        BufferedImage decoded = decode(tall.jpeg());
        assertEquals(tall.width(), decoded.getWidth());
        assertEquals(tall.height(), decoded.getHeight());
    }

    @Test
    void photosWithinTheCapKeepTheirSize() throws Exception {
        ChatImageProcessor.ProcessedImage out = processor.process(jpeg(1600, 1200, Color.ORANGE));
        assertEquals(1600, out.width());
        assertEquals(1200, out.height());
    }

    @Test
    void grayscaleJpegsAreAccepted() throws Exception {
        BufferedImage gray = new BufferedImage(30, 20, BufferedImage.TYPE_BYTE_GRAY);
        ChatImageProcessor.ProcessedImage out = processor.process(encode(gray));
        assertEquals(30, out.width());
        assertEquals(20, out.height());
    }

    // ── Orientation ────────────────────────────────────────────────────────

    @Test
    void aPhoneShotTakenSidewaysIsStoredUpright() throws Exception {
        // Orientation 6: the stored picture must turn 90° clockwise, so its top-left corner ends up top-right
        ChatImageProcessor.ProcessedImage out = processor.process(withExif(markedJpeg(80, 40, 16), 6));
        assertEquals(40, out.width());
        assertEquals(80, out.height());
        BufferedImage upright = decode(out.jpeg());
        assertTrue(isRed(upright, 34, 5), "red corner moved to the top right");
        assertFalse(isRed(upright, 5, 5));
        assertFalse(hasExif(out.jpeg()));
    }

    @Test
    void littleEndianExifIsReadToo() throws Exception {
        // Orientation 8: a quarter turn counterclockwise puts the top-left corner bottom-left
        ChatImageProcessor.ProcessedImage out =
                processor.process(withExif(markedJpeg(80, 40, 16), 8, ByteOrder.LITTLE_ENDIAN));
        assertEquals(40, out.width());
        assertEquals(80, out.height());
        BufferedImage upright = decode(out.jpeg());
        assertTrue(isRed(upright, 5, 74), "red corner moved to the bottom left");
        assertFalse(isRed(upright, 5, 5));
    }

    @Test
    void upsideDownShotsAreTurnedAround() throws Exception {
        ChatImageProcessor.ProcessedImage out = processor.process(withExif(markedJpeg(80, 40, 16), 3));
        assertEquals(80, out.width());
        assertEquals(40, out.height());
        assertTrue(isRed(decode(out.jpeg()), 74, 34), "red corner moved to the bottom right");
    }

    /** Every orientation, pixel for pixel, on an image small enough to list each pixel. */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void eachExifOrientationMapsPixelsAsTheSpecSays(int orientation) {
        int w = 3;
        int h = 2;
        BufferedImage source = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) source.setRGB(x, y, pixel(x, y));
        }

        BufferedImage out = ChatImageProcessor.orient(source, orientation);

        boolean swaps = orientation >= 5;
        assertEquals(swaps ? h : w, out.getWidth());
        assertEquals(swaps ? w : h, out.getHeight());
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                int[] from = switch (orientation) {
                    case 1 -> new int[] {x, y};
                    case 2 -> new int[] {w - 1 - x, y};
                    case 3 -> new int[] {w - 1 - x, h - 1 - y};
                    case 4 -> new int[] {x, h - 1 - y};
                    case 5 -> new int[] {y, x};
                    case 6 -> new int[] {y, h - 1 - x};
                    case 7 -> new int[] {w - 1 - y, h - 1 - x};
                    default -> new int[] {w - 1 - y, x};
                };
                assertEquals(pixel(from[0], from[1]), out.getRGB(x, y) & 0xFFFFFF,
                        "orientation " + orientation + " at " + x + "," + y);
            }
        }
    }

    private static int pixel(int x, int y) {
        return ((x * 60 + 10) << 16) | ((y * 90 + 20) << 8) | 200;
    }

    @Test
    void orientationIsReadOnlyFromAWellFormedTag() {
        byte[] plain = jpeg(8, 8, Color.WHITE);
        assertEquals(1, ChatImageProcessor.ExifOrientation.read(plain));
        assertEquals(6, ChatImageProcessor.ExifOrientation.read(withExif(plain, 6)));
        assertEquals(1, ChatImageProcessor.ExifOrientation.read(withExif(plain, 9)), "out of range");
        assertEquals(1, ChatImageProcessor.ExifOrientation.read(withExif(plain, 0)), "out of range");

        // Cut off in the middle of the EXIF segment
        byte[] withTag = withExif(plain, 6);
        assertEquals(1, ChatImageProcessor.ExifOrientation.read(Arrays.copyOf(withTag, 30)));
        // A byte-order mark that's neither II nor MM
        byte[] badOrder = withTag.clone();
        badOrder[12] = 'X';
        assertEquals(1, ChatImageProcessor.ExifOrientation.read(badOrder));
        assertEquals(1, ChatImageProcessor.ExifOrientation.read(new byte[] {(byte) 0xFF, (byte) 0xD8}));
    }

    // ── Rejections ─────────────────────────────────────────────────────────

    @Test
    void onlyJpegsAreAccepted() {
        assertFalse(ChatImageProcessor.looksLikeJpeg(png(4, 4)));
        assertFalse(ChatImageProcessor.looksLikeJpeg(new byte[] {(byte) 0xFF, (byte) 0xD8}));
        assertFalse(ChatImageProcessor.looksLikeJpeg(null));
        assertTrue(ChatImageProcessor.looksLikeJpeg(jpeg(4, 4, Color.BLACK)));
        assertThrows(ChatImageProcessor.InvalidImageException.class, () -> processor.process(png(4, 4)));
    }

    @Test
    void aFileThatOnlyStartsLikeAJpegIsRejected() {
        byte[] junk = new byte[2000];
        Arrays.fill(junk, (byte) 0x41);
        junk[0] = (byte) 0xFF;
        junk[1] = (byte) 0xD8;
        junk[2] = (byte) 0xFF;
        assertThrows(ChatImageProcessor.InvalidImageException.class, () -> processor.process(junk));

        byte[] truncated = Arrays.copyOf(jpeg(200, 200, Color.RED), 120);
        assertThrows(ChatImageProcessor.InvalidImageException.class, () -> processor.process(truncated));
    }

    @Test
    void aHugeDeclaredSizeIsRejectedBeforeDecoding() {
        // 20000 x 20000 is 400 megapixels: decoding it would need gigabytes
        byte[] bomb = withDeclaredSize(jpeg(16, 16, Color.RED), 20000, 20000);
        ChatImageProcessor.InvalidImageException e =
                assertThrows(ChatImageProcessor.InvalidImageException.class, () -> processor.process(bomb));
        assertTrue(e.getMessage().contains("20000x20000"), e.getMessage());
    }
}
