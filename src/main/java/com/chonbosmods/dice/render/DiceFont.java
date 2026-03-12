package com.chonbosmods.dice.render;

/**
 * Bitmap font for rendering numbers 0-9 on die faces.
 * Each glyph is 5 wide x 7 tall, packed into a long (35 bits).
 */
public final class DiceFont {

    public static final long[] GLYPHS = {
        0b01110_10001_10011_10101_11001_10001_01110L, // 0
        0b00100_01100_00100_00100_00100_00100_01110L, // 1
        0b01110_10001_00001_00110_01000_10000_11111L, // 2
        0b01110_10001_00001_00110_00001_10001_01110L, // 3
        0b00010_00110_01010_10010_11111_00010_00010L, // 4
        0b11111_10000_11110_00001_00001_10001_01110L, // 5
        0b01110_10001_10000_11110_10001_10001_01110L, // 6
        0b11111_00001_00010_00100_01000_01000_01000L, // 7
        0b01110_10001_10001_01110_10001_10001_01110L, // 8
        0b01110_10001_10001_01111_00001_10001_01110L, // 9
    };

    public static final int GLYPH_WIDTH = 5;
    public static final int GLYPH_HEIGHT = 7;

    private DiceFont() {}

    /**
     * Draw a number (1-20) centered at (cx, cy) in the pixel buffer.
     */
    public static void drawNumber(int[] pixels, int stride,
                                  int num, int cx, int cy,
                                  int color, float scale) {
        String digits = Integer.toString(num);
        int scaledW = Math.round(GLYPH_WIDTH * scale);
        int scaledH = Math.round(GLYPH_HEIGHT * scale);
        int gap = Math.max(1, Math.round(scale));
        int totalWidth = digits.length() * scaledW + (digits.length() - 1) * gap;

        int startX = cx - totalWidth / 2;
        int startY = cy - scaledH / 2;

        for (int d = 0; d < digits.length(); d++) {
            int digit = digits.charAt(d) - '0';
            long glyph = GLYPHS[digit];
            int ox = startX + d * (scaledW + gap);

            for (int gy = 0; gy < GLYPH_HEIGHT; gy++) {
                for (int gx = 0; gx < GLYPH_WIDTH; gx++) {
                    int bitIndex = (GLYPH_HEIGHT - 1 - gy) * GLYPH_WIDTH + (GLYPH_WIDTH - 1 - gx);
                    if ((glyph & (1L << bitIndex)) != 0) {
                        int px0 = ox + Math.round(gx * scale);
                        int py0 = startY + Math.round(gy * scale);
                        int px1 = ox + Math.round((gx + 1) * scale);
                        int py1 = startY + Math.round((gy + 1) * scale);
                        for (int py = py0; py < py1; py++) {
                            if (py < 0 || py >= stride) continue;
                            for (int px = px0; px < px1; px++) {
                                if (px < 0 || px >= stride) continue;
                                pixels[py * stride + px] = color;
                            }
                        }
                    }
                }
            }
        }
    }
}
