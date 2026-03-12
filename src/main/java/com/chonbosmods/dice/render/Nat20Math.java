package com.chonbosmods.dice.render;

/**
 * Pure quaternion and vector math for the d20 renderer.
 * All quaternions are float[4] in (w, x, y, z) order.
 * All vectors are float[3].
 * No allocations in hot paths: callers pass output arrays.
 */
public final class Nat20Math {

    public static final float[] IDENTITY_QUAT = {1f, 0f, 0f, 0f};

    private Nat20Math() {}

    /** Create quaternion from axis (unit vector) and angle (radians). */
    public static float[] quaternionFromAxisAngle(float[] axis, float angle) {
        float halfAngle = angle * 0.5f;
        float s = (float) Math.sin(halfAngle);
        return new float[]{
            (float) Math.cos(halfAngle),
            axis[0] * s,
            axis[1] * s,
            axis[2] * s
        };
    }

    /** Multiply two quaternions: result = a * b. */
    public static float[] quaternionMultiply(float[] a, float[] b) {
        return new float[]{
            a[0]*b[0] - a[1]*b[1] - a[2]*b[2] - a[3]*b[3],
            a[0]*b[1] + a[1]*b[0] + a[2]*b[3] - a[3]*b[2],
            a[0]*b[2] - a[1]*b[3] + a[2]*b[0] + a[3]*b[1],
            a[0]*b[3] + a[1]*b[2] - a[2]*b[1] + a[3]*b[0]
        };
    }

    /** Spherical linear interpolation between quaternions a and b at parameter t (0..1). */
    public static float[] slerp(float[] a, float[] b, float t) {
        float d = a[0]*b[0] + a[1]*b[1] + a[2]*b[2] + a[3]*b[3];
        float[] b2 = b;
        if (d < 0f) {
            d = -d;
            b2 = new float[]{-b[0], -b[1], -b[2], -b[3]};
        }
        if (d > 0.9995f) {
            float[] result = new float[4];
            for (int i = 0; i < 4; i++) {
                result[i] = a[i] + t * (b2[i] - a[i]);
            }
            float len = (float) Math.sqrt(result[0]*result[0] + result[1]*result[1]
                    + result[2]*result[2] + result[3]*result[3]);
            for (int i = 0; i < 4; i++) result[i] /= len;
            return result;
        }
        float theta = (float) Math.acos(d);
        float sinTheta = (float) Math.sin(theta);
        float wa = (float) Math.sin((1f - t) * theta) / sinTheta;
        float wb = (float) Math.sin(t * theta) / sinTheta;
        return new float[]{
            wa * a[0] + wb * b2[0],
            wa * a[1] + wb * b2[1],
            wa * a[2] + wb * b2[2],
            wa * a[3] + wb * b2[3]
        };
    }

    /** Convert quaternion to 3x3 rotation matrix (row-major float[9]). */
    public static float[] quaternionToMatrix3x3(float[] q) {
        float w = q[0], x = q[1], y = q[2], z = q[3];
        float x2 = x + x, y2 = y + y, z2 = z + z;
        float xx = x * x2, xy = x * y2, xz = x * z2;
        float yy = y * y2, yz = y * z2, zz = z * z2;
        float wx = w * x2, wy = w * y2, wz = w * z2;
        return new float[]{
            1f - (yy + zz),  xy - wz,          xz + wy,
            xy + wz,          1f - (xx + zz),   yz - wx,
            xz - wy,          yz + wx,          1f - (xx + yy)
        };
    }

    /** Rotate a 3D vector by a 3x3 rotation matrix. */
    public static float[] rotateVector(float[] matrix, float[] vec) {
        return new float[]{
            matrix[0]*vec[0] + matrix[1]*vec[1] + matrix[2]*vec[2],
            matrix[3]*vec[0] + matrix[4]*vec[1] + matrix[5]*vec[2],
            matrix[6]*vec[0] + matrix[7]*vec[1] + matrix[8]*vec[2]
        };
    }

    /** Compute quaternion that rotates unit vector 'from' to align with unit vector 'to'. */
    public static float[] alignVectorToVector(float[] from, float[] to) {
        float d = dot(from, to);
        if (d > 0.9999f) {
            return IDENTITY_QUAT.clone();
        }
        if (d < -0.9999f) {
            float[] perp = cross(from, new float[]{1f, 0f, 0f});
            float len = (float) Math.sqrt(dot(perp, perp));
            if (len < 1e-6f) {
                perp = cross(from, new float[]{0f, 1f, 0f});
            }
            normalize(perp);
            return new float[]{0f, perp[0], perp[1], perp[2]};
        }
        float[] axis = normalize(cross(from, to));
        float angle = (float) Math.acos(d);
        return quaternionFromAxisAngle(axis, angle);
    }

    /** Dot product of two 3D vectors. */
    public static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** Cross product of two 3D vectors. */
    public static float[] cross(float[] a, float[] b) {
        return new float[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    /** Normalize a 3D vector in-place and return it. */
    public static float[] normalize(float[] v) {
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len > 1e-8f) {
            v[0] /= len;
            v[1] /= len;
            v[2] /= len;
        }
        return v;
    }

    /** Ease-out cubic: fast start, gentle finish. */
    public static float easeOutCubic(float t) {
        float t1 = 1f - t;
        return 1f - t1 * t1 * t1;
    }
}
