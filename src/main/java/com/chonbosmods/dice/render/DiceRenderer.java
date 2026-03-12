package com.chonbosmods.dice.render;

/**
 * Software rasterizer: takes mesh data + rotation quaternion, outputs a 128x128 ARGB pixel buffer.
 * Pipeline: rotate vertices -> backface cull -> depth sort -> scanline fill -> edge draw.
 */
public class DiceRenderer {

    public static final int WIDTH = 128;
    public static final int HEIGHT = 128;

    private static final int COLOR_BODY = 0xFF2A2A4C;
    private static final int COLOR_EDGE = 0xFF4A4A7C;
    private static final float[] LIGHT_DIR = Nat20Math.normalize(new float[]{-0.5f, 0.7f, 0.5f});
    private static final float AMBIENT = 0.3f;

    // Projection scale: maps world units to pixels. RADIUS=5 should fill ~80% of 128px.
    private static final float PROJ_SCALE = (WIDTH * 0.4f) / DiceGeometry.RADIUS;
    private static final float CENTER_X = WIDTH / 2f;
    private static final float CENTER_Y = HEIGHT / 2f;

    private final int[] pixels = new int[WIDTH * HEIGHT];

    public void render(float[] rotationQuat) {
        // 1. Clear buffer (transparent black)
        java.util.Arrays.fill(pixels, 0);

        // 2. Transform vertices and normals
        float[] matrix = Nat20Math.quaternionToMatrix3x3(rotationQuat);

        float[][] rotatedVerts = new float[DiceGeometry.VERTICES.length][];
        for (int i = 0; i < DiceGeometry.VERTICES.length; i++) {
            rotatedVerts[i] = Nat20Math.rotateVector(matrix, DiceGeometry.VERTICES[i]);
        }

        float[][] rotatedNormals = new float[DiceGeometry.FACE_NORMALS.length][];
        for (int i = 0; i < DiceGeometry.FACE_NORMALS.length; i++) {
            rotatedNormals[i] = Nat20Math.rotateVector(matrix, DiceGeometry.FACE_NORMALS[i]);
        }

        // 3. Backface cull + compute rotated centers for depth sorting
        float[][] rotatedCenters = new float[DiceGeometry.FACE_CENTERS.length][];
        for (int i = 0; i < DiceGeometry.FACE_CENTERS.length; i++) {
            rotatedCenters[i] = Nat20Math.rotateVector(matrix, DiceGeometry.FACE_CENTERS[i]);
        }

        // Collect visible face indices
        int[] visible = new int[20];
        float[] depths = new float[20];
        int count = 0;
        for (int i = 0; i < 20; i++) {
            if (rotatedNormals[i][2] > 0) { // facing camera (positive Z)
                visible[count] = i;
                depths[count] = rotatedCenters[i][2];
                count++;
            }
        }

        // Sort by depth: far (small Z) to near (large Z) for painter's algorithm
        for (int i = 0; i < count - 1; i++) {
            for (int j = i + 1; j < count; j++) {
                if (depths[j] < depths[i]) {
                    float tmpD = depths[i]; depths[i] = depths[j]; depths[j] = tmpD;
                    int tmpV = visible[i]; visible[i] = visible[j]; visible[j] = tmpV;
                }
            }
        }

        // 4. Render each visible face
        for (int vi = 0; vi < count; vi++) {
            int faceIdx = visible[vi];
            int[] face = DiceGeometry.FACES[faceIdx];

            // Lighting
            float brightness = Math.max(AMBIENT, Nat20Math.dot(rotatedNormals[faceIdx], LIGHT_DIR));
            int color = shadeColor(COLOR_BODY, brightness);

            // Project 3D -> 2D (orthographic: drop Z, scale + center)
            int[] sx = new int[3];
            int[] sy = new int[3];
            for (int v = 0; v < 3; v++) {
                sx[v] = (int) (rotatedVerts[face[v]][0] * PROJ_SCALE + CENTER_X);
                sy[v] = (int) (-rotatedVerts[face[v]][1] * PROJ_SCALE + CENTER_Y); // flip Y
            }

            // Scanline fill
            fillTriangle(sx, sy, color);

            // Edge draw
            drawLine(sx[0], sy[0], sx[1], sy[1], COLOR_EDGE);
            drawLine(sx[1], sy[1], sx[2], sy[2], COLOR_EDGE);
            drawLine(sx[2], sy[2], sx[0], sy[0], COLOR_EDGE);
        }

        // 5. Draw numbers on front-facing faces
        int colorNumber = 0xFFFFD700; // gold
        for (int vi = 0; vi < count; vi++) {
            int faceIdx = visible[vi];
            float facingAmount = rotatedNormals[faceIdx][2]; // how much face points at camera

            // Only draw numbers on faces that are fairly head-on
            if (facingAmount > 0.3f) {
                int number = DiceFaceLookup.FACE_NUMBERS[faceIdx];
                float[] center3d = rotatedCenters[faceIdx];
                int cx = (int) (center3d[0] * PROJ_SCALE + CENTER_X);
                int cy = (int) (-center3d[1] * PROJ_SCALE + CENTER_Y);
                float scale = facingAmount * 1.5f; // scale with facing angle
                DiceFont.drawNumber(pixels, WIDTH, number, cx, cy, colorNumber, scale);
            }
        }
    }

    public int[] getPixels() {
        return pixels;
    }

    static int shadeColor(int argb, float brightness) {
        int a = (argb >> 24) & 0xFF;
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * brightness));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * brightness));
        int b = Math.min(255, (int) ((argb & 0xFF) * brightness));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // --- Scanline triangle fill ---

    private void fillTriangle(int[] sx, int[] sy, int color) {
        // Sort vertices by Y coordinate (top to bottom)
        int i0 = 0, i1 = 1, i2 = 2;
        if (sy[i1] < sy[i0]) { int tmp = i0; i0 = i1; i1 = tmp; }
        if (sy[i2] < sy[i0]) { int tmp = i0; i0 = i2; i2 = tmp; }
        if (sy[i2] < sy[i1]) { int tmp = i1; i1 = i2; i2 = tmp; }

        int x0 = sx[i0], y0 = sy[i0];
        int x1 = sx[i1], y1 = sy[i1];
        int x2 = sx[i2], y2 = sy[i2];

        if (y0 == y2) return; // degenerate

        // Upper half: y0 to y1
        scanFill(x0, y0, x1, y1, x2, y2, color, true);
        // Lower half: y1 to y2
        scanFill(x0, y0, x1, y1, x2, y2, color, false);
    }

    private void scanFill(int x0, int y0, int x1, int y1, int x2, int y2,
                           int color, boolean upper) {
        int yStart, yEnd;
        if (upper) {
            yStart = Math.max(0, y0);
            yEnd = Math.min(HEIGHT - 1, y1);
        } else {
            yStart = Math.max(0, y1);
            yEnd = Math.min(HEIGHT - 1, y2);
        }

        for (int y = yStart; y <= yEnd; y++) {
            // Interpolate X on the long edge (v0->v2) and the current short edge
            float xLong = (y2 != y0) ? x0 + (float)(x2 - x0) * (y - y0) / (y2 - y0) : x0;
            float xShort;
            if (upper) {
                xShort = (y1 != y0) ? x0 + (float)(x1 - x0) * (y - y0) / (y1 - y0) : x0;
            } else {
                xShort = (y2 != y1) ? x1 + (float)(x2 - x1) * (y - y1) / (y2 - y1) : x1;
            }

            int xMin = Math.max(0, (int) Math.min(xLong, xShort));
            int xMax = Math.min(WIDTH - 1, (int) Math.max(xLong, xShort));

            int rowOffset = y * WIDTH;
            for (int x = xMin; x <= xMax; x++) {
                pixels[rowOffset + x] = color;
            }
        }
    }

    // --- Bresenham line drawing ---

    private void drawLine(int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;

        while (true) {
            if (x0 >= 0 && x0 < WIDTH && y0 >= 0 && y0 < HEIGHT) {
                pixels[y0 * WIDTH + x0] = color;
            }
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }
}
