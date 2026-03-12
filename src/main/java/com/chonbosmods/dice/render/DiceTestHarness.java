package com.chonbosmods.dice.render;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Standalone test harness for the d20 rasterizer.
 * Renders frames to PNG files for visual verification.
 */
public class DiceTestHarness {

    private static final String OUTPUT_DIR = "devserver/dice-test";
    private static int assertions = 0;
    private static int passed = 0;

    public static void main(String[] args) throws Exception {
        File dir = new File(OUTPUT_DIR);
        dir.mkdirs();

        System.out.println("=== D20 Rasterizer Test Harness ===\n");

        testMath();
        testGeometry();
        testFaceLookup();
        testStaticRenders();
        testFontRender();
        testAnimationSequence();

        System.out.printf("\n=== Results: %d/%d assertions passed ===%n", passed, assertions);
        if (passed < assertions) {
            System.exit(1);
        }
    }

    private static void testMath() {
        System.out.println("--- Nat20Math ---");

        // Identity quaternion should not rotate
        float[] m = Nat20Math.quaternionToMatrix3x3(Nat20Math.IDENTITY_QUAT);
        float[] v = Nat20Math.rotateVector(m, new float[]{1, 2, 3});
        assertClose("identity rotation X", v[0], 1f, 0.001f);
        assertClose("identity rotation Y", v[1], 2f, 0.001f);
        assertClose("identity rotation Z", v[2], 3f, 0.001f);

        // 90 degrees around Z should map (1,0,0) to (0,1,0)
        float[] q90z = Nat20Math.quaternionFromAxisAngle(new float[]{0, 0, 1}, (float)(Math.PI / 2));
        float[] m90 = Nat20Math.quaternionToMatrix3x3(q90z);
        float[] rotated = Nat20Math.rotateVector(m90, new float[]{1, 0, 0});
        assertClose("90deg Z rotation X", rotated[0], 0f, 0.01f);
        assertClose("90deg Z rotation Y", rotated[1], 1f, 0.01f);

        // SLERP at t=0 and t=1
        float[] qa = Nat20Math.IDENTITY_QUAT;
        float[] s0 = Nat20Math.slerp(qa, q90z, 0f);
        assertClose("slerp t=0 w", s0[0], qa[0], 0.001f);
        float[] s1 = Nat20Math.slerp(qa, q90z, 1f);
        assertClose("slerp t=1 w", s1[0], q90z[0], 0.001f);

        // alignVectorToVector
        float[] from = Nat20Math.normalize(new float[]{1, 1, 0});
        float[] to = new float[]{0, 0, 1};
        float[] alignQ = Nat20Math.alignVectorToVector(from, to);
        float[] mAlign = Nat20Math.quaternionToMatrix3x3(alignQ);
        float[] aligned = Nat20Math.rotateVector(mAlign, from);
        assertClose("align to Z axis X", aligned[0], 0f, 0.01f);
        assertClose("align to Z axis Y", aligned[1], 0f, 0.01f);
        assertClose("align to Z axis Z", aligned[2], 1f, 0.01f);
    }

    private static void testGeometry() {
        System.out.println("--- DiceGeometry ---");

        assertEquals("vertex count", DiceGeometry.VERTICES.length, 12);
        assertEquals("face count", DiceGeometry.FACES.length, 20);
        assertEquals("normal count", DiceGeometry.FACE_NORMALS.length, 20);
        assertEquals("center count", DiceGeometry.FACE_CENTERS.length, 20);

        // All normals should be unit length
        for (int i = 0; i < 20; i++) {
            float[] n = DiceGeometry.FACE_NORMALS[i];
            float len = (float) Math.sqrt(n[0]*n[0] + n[1]*n[1] + n[2]*n[2]);
            assertClose("normal " + i + " unit length", len, 1f, 0.01f);
        }

        // All normals should point outward (dot with center > 0)
        for (int i = 0; i < 20; i++) {
            float d = Nat20Math.dot(DiceGeometry.FACE_NORMALS[i], DiceGeometry.FACE_CENTERS[i]);
            assertTrue("normal " + i + " points outward", d > 0);
        }

        // All vertices should be at RADIUS distance from origin
        for (int i = 0; i < 12; i++) {
            float[] vert = DiceGeometry.VERTICES[i];
            float dist = (float) Math.sqrt(vert[0]*vert[0] + vert[1]*vert[1] + vert[2]*vert[2]);
            assertClose("vertex " + i + " at RADIUS", dist, DiceGeometry.RADIUS, 0.01f);
        }
    }

    private static void testFaceLookup() {
        System.out.println("--- DiceFaceLookup ---");

        // Opposite faces sum to 21
        for (int i = 0; i < 20; i++) {
            float[] n1 = DiceGeometry.FACE_NORMALS[i];
            int bestJ = -1;
            float bestDot = 0;
            for (int j = 0; j < 20; j++) {
                if (j == i) continue;
                float d = Nat20Math.dot(n1, DiceGeometry.FACE_NORMALS[j]);
                if (d < bestDot) {
                    bestDot = d;
                    bestJ = j;
                }
            }
            int sum = DiceFaceLookup.FACE_NUMBERS[i] + DiceFaceLookup.FACE_NUMBERS[bestJ];
            assertEquals("opposite faces " + i + "+" + bestJ + " sum", sum, 21);
        }

        // Each number 1-20 appears exactly once
        boolean[] seen = new boolean[21];
        for (int n : DiceFaceLookup.FACE_NUMBERS) {
            assertTrue("number " + n + " in range", n >= 1 && n <= 20);
            assertTrue("number " + n + " unique", !seen[n]);
            seen[n] = true;
        }

        // Result rotations: applying rotation should align face normal with +Z
        for (int result = 1; result <= 20; result++) {
            float[] q = DiceFaceLookup.RESULT_ROTATIONS[result];
            int faceIdx = DiceFaceLookup.faceIndexForResult(result);
            float[] normal = DiceGeometry.FACE_NORMALS[faceIdx];
            float[] mat = Nat20Math.quaternionToMatrix3x3(q);
            float[] rotatedNormal = Nat20Math.rotateVector(mat, normal);
            assertClose("result " + result + " faces +Z (z)", rotatedNormal[2], 1f, 0.05f);
        }
    }

    private static void testStaticRenders() throws Exception {
        System.out.println("--- Static Renders ---");

        DiceRenderer renderer = new DiceRenderer();

        // Render at identity rotation
        renderer.render(Nat20Math.IDENTITY_QUAT);
        savePng(renderer.getPixels(), OUTPUT_DIR + "/identity.png");
        int filledPixels = countNonTransparent(renderer.getPixels());
        assertTrue("identity render has pixels", filledPixels > 100);
        System.out.println("  identity.png: " + filledPixels + " filled pixels");

        // Render each result face (1-20) for visual verification
        for (int result = 1; result <= 20; result++) {
            float[] q = DiceFaceLookup.RESULT_ROTATIONS[result];
            renderer.render(q);
            savePng(renderer.getPixels(), OUTPUT_DIR + "/face_" + result + ".png");
        }
        System.out.println("  Rendered face_1.png through face_20.png");
    }

    private static void testFontRender() throws Exception {
        System.out.println("--- Font Render ---");

        int[] buf = new int[128 * 128];
        for (int n = 1; n <= 20; n++) {
            int col = (n - 1) % 5;
            int row = (n - 1) / 5;
            int cx = 12 + col * 25;
            int cy = 12 + row * 25;
            DiceFont.drawNumber(buf, 128, n, cx, cy, 0xFFFFD700, 2.0f);
        }
        savePng(buf, OUTPUT_DIR + "/font_test.png");
        int filledPixels = countNonTransparent(buf);
        assertTrue("font test has pixels", filledPixels > 50);
        System.out.println("  font_test.png: " + filledPixels + " filled pixels");
    }

    private static void testAnimationSequence() throws Exception {
        System.out.println("--- Animation Sequence (M2) ---");

        DiceAnimator animator = new DiceAnimator();
        DiceRenderer renderer = new DiceRenderer();

        int testResult = 20;
        animator.startRoll(testResult);
        assertEquals("state after startRoll", animator.getState().name(), "TUMBLING");

        float dt = 1f / 30f; // 30fps
        int frameCount = 0;
        int maxFrames = 120; // 4 seconds max

        while (animator.getState() != DiceAnimator.State.HOLDING && frameCount < maxFrames) {
            animator.update(dt);
            renderer.render(animator.getCurrentRotation());

            // Save every 3rd frame for inspection
            if (frameCount % 3 == 0) {
                savePng(renderer.getPixels(), String.format("%s/anim_%03d.png", OUTPUT_DIR, frameCount));
            }
            frameCount++;
        }

        assertEquals("animation reached HOLDING", animator.getState().name(), "HOLDING");
        assertTrue("animation completed in reasonable time", frameCount < maxFrames);
        System.out.println("  Animation: " + frameCount + " frames to settle on result " + testResult);

        // Verify final orientation: face 20 normal should point at camera (+Z)
        float[] finalQ = animator.getCurrentRotation();
        int faceIdx = DiceFaceLookup.faceIndexForResult(testResult);
        float[] normal = DiceGeometry.FACE_NORMALS[faceIdx];
        float[] mat = Nat20Math.quaternionToMatrix3x3(finalQ);
        float[] rotatedNormal = Nat20Math.rotateVector(mat, normal);
        assertClose("final face " + testResult + " faces +Z", rotatedNormal[2], 1f, 0.05f);

        // Save final frame
        renderer.render(finalQ);
        savePng(renderer.getPixels(), OUTPUT_DIR + "/anim_final.png");
        System.out.println("  Saved " + (frameCount / 3) + " animation frames + final");
    }

    // --- Helpers ---

    private static void savePng(int[] pixels, String path) throws Exception {
        BufferedImage img = new BufferedImage(DiceRenderer.WIDTH, DiceRenderer.HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, DiceRenderer.WIDTH, DiceRenderer.HEIGHT, pixels, 0, DiceRenderer.WIDTH);
        ImageIO.write(img, "PNG", new File(path));
    }

    private static int countNonTransparent(int[] pixels) {
        int count = 0;
        for (int p : pixels) {
            if ((p & 0xFF000000) != 0) count++;
        }
        return count;
    }

    private static void assertClose(String name, float actual, float expected, float epsilon) {
        assertions++;
        if (Math.abs(actual - expected) <= epsilon) {
            passed++;
        } else {
            System.out.printf("  FAIL: %s: expected %.4f, got %.4f%n", name, expected, actual);
        }
    }

    private static void assertEquals(String name, Object actual, Object expected) {
        assertions++;
        if (actual.equals(expected)) {
            passed++;
        } else {
            System.out.printf("  FAIL: %s: expected %s, got %s%n", name, expected, actual);
        }
    }

    private static void assertTrue(String name, boolean condition) {
        assertions++;
        if (condition) {
            passed++;
        } else {
            System.out.printf("  FAIL: %s%n", name);
        }
    }
}
