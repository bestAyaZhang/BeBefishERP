package com.bebefish.erp.file.infrastructure;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Reads only the bounded EXIF orientation field; malformed metadata leaves pixels untouched. */
final class JpegOrientation {
    private JpegOrientation() { }

    static int read(byte[] bytes) {
        if (bytes.length < 4 || (bytes[0] & 255) != 255 || (bytes[1] & 255) != 216) return 1;
        int offset = 2;
        while (offset + 4 <= bytes.length && (bytes[offset++] & 255) == 255) {
            int marker = bytes[offset++] & 255;
            if (marker == 218 || marker == 217) break;
            int length = ((bytes[offset] & 255) << 8) | (bytes[offset + 1] & 255);
            if (length < 2 || length > bytes.length - offset) break;
            if (marker == 225 && length >= 16 && bytes[offset + 2] == 'E' && bytes[offset + 3] == 'x'
                    && bytes[offset + 4] == 'i' && bytes[offset + 5] == 'f' && bytes[offset + 6] == 0 && bytes[offset + 7] == 0) {
                int result = tiff(bytes, offset + 8, offset + length);
                if (result != 1) return result;
            }
            offset += length;
        }
        return 1;
    }

    private static int tiff(byte[] bytes, int start, int end) {
        if (start + 8 > end) return 1;
        boolean little = bytes[start] == 'I' && bytes[start + 1] == 'I';
        if (!little && !(bytes[start] == 'M' && bytes[start + 1] == 'M')) return 1;
        var data = ByteBuffer.wrap(bytes).order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        if (data.getShort(start + 2) != 42) return 1;
        long first = start + Integer.toUnsignedLong(data.getInt(start + 4));
        if (first < start || first + 2 > end) return 1;
        int entries = Short.toUnsignedInt(data.getShort((int) first));
        for (int i = 0; i < entries; i++) {
            long address = first + 2 + (long) i * 12;
            if (address + 12 > end) break;
            int position = (int) address;
            if (Short.toUnsignedInt(data.getShort(position)) == 274 && data.getShort(position + 2) == 3 && data.getInt(position + 4) == 1) {
                int value = Short.toUnsignedInt(data.getShort(position + 8));
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    static BufferedImage apply(BufferedImage image, int orientation) {
        if (orientation == 1) return image;
        int w = image.getWidth(), h = image.getHeight();
        var transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);
            default -> new AffineTransform();
        };
        var result = new BufferedImage(orientation >= 5 ? h : w, orientation >= 5 ? w : h,
                image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var graphics = result.createGraphics();
        try { graphics.drawImage(image, transform, null); } finally { graphics.dispose(); }
        image.flush(); return result;
    }
}
