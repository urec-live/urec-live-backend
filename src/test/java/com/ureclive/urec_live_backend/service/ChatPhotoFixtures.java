package com.ureclive.urec_live_backend.service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** JPEGs for the chat photo tests, including ones carrying EXIF with an orientation and GPS data. */
public final class ChatPhotoFixtures {

    /** Written into the GPS block, so a test can check it's gone from the stored copy. */
    public static final String GPS_MARKER = "SECRET-GPS-40.4237N-86.9212W";

    private ChatPhotoFixtures() {}

    /** A plain JPEG (no EXIF) of one color. */
    public static byte[] jpeg(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return encode(image);
    }

    /** Blue, with a red square in the top-left corner, to see where a rotation puts it. */
    public static byte[] markedJpeg(int width, int height, int square) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.RED);
        g.fillRect(0, 0, square, square);
        g.dispose();
        return encode(image);
    }

    public static byte[] encode(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "jpeg", out)) throw new IllegalStateException("no JPEG writer");
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] png(int width, int height) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The JPEG with an EXIF segment (big-endian, like most cameras) holding the orientation and a GPS block. */
    public static byte[] withExif(byte[] jpeg, int orientation) {
        return withExif(jpeg, orientation, ByteOrder.BIG_ENDIAN);
    }

    public static byte[] withExif(byte[] jpeg, int orientation, ByteOrder order) {
        byte[] datum = (GPS_MARKER + "\0").getBytes(StandardCharsets.US_ASCII);
        int ifd0Offset = 8;
        int ifd0Entries = 2;
        int gpsOffset = ifd0Offset + 2 + 12 * ifd0Entries + 4;
        int gpsEntries = 2;
        int datumOffset = gpsOffset + 2 + 12 * gpsEntries + 4;

        ByteBuffer tiff = ByteBuffer.allocate(datumOffset + datum.length).order(order);
        byte mark = (byte) (order == ByteOrder.LITTLE_ENDIAN ? 'I' : 'M');
        tiff.put(mark).put(mark).putShort((short) 42).putInt(ifd0Offset);
        tiff.putShort((short) ifd0Entries);
        // Orientation: SHORT, 1 value, left-justified in the 4-byte field
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        // GPSInfo: pointer to the GPS IFD
        tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(gpsOffset);
        tiff.putInt(0);
        tiff.putShort((short) gpsEntries);
        // GPSLatitudeRef "N"
        tiff.putShort((short) 0x0001).putShort((short) 2).putInt(2).put((byte) 'N').put((byte) 0).putShort((short) 0);
        // GPSMapDatum, stored past the IFD
        tiff.putShort((short) 0x0012).putShort((short) 2).putInt(datum.length).putInt(datumOffset);
        tiff.putInt(0);
        tiff.put(datum);

        byte[] header = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        int length = 2 + header.length + tiff.capacity();
        ByteBuffer out = ByteBuffer.allocate(jpeg.length + 2 + length).order(ByteOrder.BIG_ENDIAN);
        out.put(jpeg, 0, 2); // SOI
        out.put((byte) 0xFF).put((byte) 0xE1).putShort((short) length).put(header).put(tiff.array());
        out.put(jpeg, 2, jpeg.length - 2);
        return out.array();
    }

    /** The JPEG with its frame header claiming another size, as a crafted file might. */
    public static byte[] withDeclaredSize(byte[] jpeg, int width, int height) {
        byte[] copy = jpeg.clone();
        for (int i = 2; i + 8 < copy.length; i++) {
            int marker = copy[i + 1] & 0xFF;
            if ((copy[i] & 0xFF) == 0xFF && (marker == 0xC0 || marker == 0xC2)) {
                copy[i + 5] = (byte) (height >> 8);
                copy[i + 6] = (byte) height;
                copy[i + 7] = (byte) (width >> 8);
                copy[i + 8] = (byte) width;
                return copy;
            }
        }
        throw new IllegalStateException("no frame header");
    }

    /** True if the JPEG has an APP1 "Exif" segment before its image data. */
    public static boolean hasExif(byte[] jpeg) {
        int pos = 2;
        while (pos + 4 <= jpeg.length && (jpeg[pos] & 0xFF) == 0xFF) {
            int marker = jpeg[pos + 1] & 0xFF;
            if (marker == 0xDA) return false;
            int length = ((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF);
            if (marker == 0xE1 && pos + 10 <= jpeg.length
                    && new String(jpeg, pos + 4, 4, StandardCharsets.US_ASCII).equals("Exif")) {
                return true;
            }
            pos += 2 + length;
        }
        return false;
    }

    public static boolean contains(byte[] data, String text) {
        byte[] needle = text.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i + needle.length <= data.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    public static BufferedImage decode(byte[] jpeg) {
        try {
            return ImageIO.read(new java.io.ByteArrayInputStream(jpeg));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Red clearly outweighs blue at this pixel (JPEG blurs edges, so tests sample inside the square). */
    public static boolean isRed(BufferedImage image, int x, int y) {
        Color c = new Color(image.getRGB(x, y));
        return c.getRed() > 150 && c.getBlue() < 100;
    }
}
