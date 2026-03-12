package com.chonbosmods.dice.render;

/**
 * Static icosahedron geometry for a d20 die.
 * 12 vertices, 20 triangular faces, all derived from the golden ratio.
 * Vertices are on a unit sphere scaled by RADIUS.
 */
public final class DiceGeometry {

    /** Scale factor for the rendered die size. */
    public static final float RADIUS = 5.0f;

    /** 12 vertices of the icosahedron, each float[3]. */
    public static final float[][] VERTICES = generateVertices();

    /** 20 faces, each int[3] of vertex indices (CCW winding when viewed from outside). */
    public static final int[][] FACES = generateFaces();

    /** 20 outward-pointing unit normals, one per face. */
    public static final float[][] FACE_NORMALS = computeNormals();

    /** 20 face centers (centroid of each triangle's 3 vertices). */
    public static final float[][] FACE_CENTERS = computeCenters();

    private DiceGeometry() {}

    private static float[][] generateVertices() {
        float phi = (1f + (float) Math.sqrt(5.0)) / 2f;
        float[][] raw = {
            { 0,  1,  phi},  // v0
            { 0, -1,  phi},  // v1
            { 0,  1, -phi},  // v2
            { 0, -1, -phi},  // v3
            { 1,  phi, 0},   // v4
            {-1,  phi, 0},   // v5
            { 1, -phi, 0},   // v6
            {-1, -phi, 0},   // v7
            { phi, 0,  1},   // v8
            { phi, 0, -1},   // v9
            {-phi, 0,  1},   // v10
            {-phi, 0, -1},   // v11
        };
        for (float[] v : raw) {
            float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            v[0] = v[0] / len * RADIUS;
            v[1] = v[1] / len * RADIUS;
            v[2] = v[2] / len * RADIUS;
        }
        return raw;
    }

    private static int[][] generateFaces() {
        return new int[][]{
            {0, 5, 4},   {0, 4, 8},   {0, 8, 1},   {0, 1, 10},  {0, 10, 5},
            {5, 11, 2},  {4, 5, 2},   {4, 2, 9},   {8, 4, 9},   {8, 9, 6},
            {1, 8, 6},   {1, 6, 7},   {10, 1, 7},  {10, 7, 11}, {5, 10, 11},
            {3, 2, 11},  {3, 9, 2},   {3, 6, 9},   {3, 7, 6},   {3, 11, 7},
        };
    }

    private static float[][] computeNormals() {
        float[][] normals = new float[20][];
        for (int i = 0; i < 20; i++) {
            int[] face = FACES[i];
            float[] v0 = VERTICES[face[0]];
            float[] v1 = VERTICES[face[1]];
            float[] v2 = VERTICES[face[2]];
            float[] e1 = {v1[0] - v0[0], v1[1] - v0[1], v1[2] - v0[2]};
            float[] e2 = {v2[0] - v0[0], v2[1] - v0[1], v2[2] - v0[2]};
            float[] n = Nat20Math.cross(e1, e2);
            Nat20Math.normalize(n);
            float[] center = {
                (v0[0] + v1[0] + v2[0]) / 3f,
                (v0[1] + v1[1] + v2[1]) / 3f,
                (v0[2] + v1[2] + v2[2]) / 3f
            };
            if (Nat20Math.dot(n, center) < 0) {
                n[0] = -n[0]; n[1] = -n[1]; n[2] = -n[2];
            }
            normals[i] = n;
        }
        return normals;
    }

    private static float[][] computeCenters() {
        float[][] centers = new float[20][];
        for (int i = 0; i < 20; i++) {
            int[] face = FACES[i];
            float[] v0 = VERTICES[face[0]];
            float[] v1 = VERTICES[face[1]];
            float[] v2 = VERTICES[face[2]];
            centers[i] = new float[]{
                (v0[0] + v1[0] + v2[0]) / 3f,
                (v0[1] + v1[1] + v2[1]) / 3f,
                (v0[2] + v1[2] + v2[2]) / 3f
            };
        }
        return centers;
    }
}
