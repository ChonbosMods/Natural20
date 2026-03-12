package com.chonbosmods.dice.render;

/**
 * Maps die results (1-20) to face indices and pre-computed rotations.
 * Opposite faces sum to 21 (standard d20 convention).
 */
public final class DiceFaceLookup {

    /**
     * FACE_NUMBERS[i] = the number displayed on DiceGeometry.FACES[i].
     * Assigned such that opposite faces (by normal) sum to 21.
     */
    public static final int[] FACE_NUMBERS = {
            20, 19, 18, 17, 16, 15, 14, 13, 12, 11,
             6,  7,  8,  9, 10,  3,  4,  5,  1,  2
    };

    /**
     * RESULT_ROTATIONS[n] = quaternion (w,x,y,z) that orients the face
     * displaying number n toward the camera (positive Z axis).
     * Index 0 is unused; indices 1-20 are valid.
     */
    public static final float[][] RESULT_ROTATIONS = computeAllResultRotations();

    private DiceFaceLookup() {}

    /** Find which face index displays the given result number (1-20). */
    public static int faceIndexForResult(int result) {
        for (int i = 0; i < FACE_NUMBERS.length; i++) {
            if (FACE_NUMBERS[i] == result) return i;
        }
        throw new IllegalArgumentException("Invalid result: " + result);
    }

    private static float[][] computeAllResultRotations() {
        float[][] rotations = new float[21][]; // index 0 unused, 1-20 valid

        float[] cameraDir = {0f, 0f, 1f}; // face toward positive Z

        for (int result = 1; result <= 20; result++) {
            int faceIdx = faceIndexForResult(result);
            float[] normal = DiceGeometry.FACE_NORMALS[faceIdx];
            rotations[result] = Nat20Math.alignVectorToVector(normal, cameraDir);
        }

        return rotations;
    }
}
