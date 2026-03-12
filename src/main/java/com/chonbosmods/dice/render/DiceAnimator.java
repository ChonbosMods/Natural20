package com.chonbosmods.dice.render;

/**
 * State machine driving the d20 rotation: IDLE -> TUMBLING -> SETTLING -> HOLDING.
 * Uses quaternions for smooth interpolation. Result is always predetermined;
 * the animation is purely cosmetic.
 */
public class DiceAnimator {

    public enum State {
        /** No active roll. Optional gentle hover. */
        IDLE,
        /** Chaotic spin with decaying angular velocity. */
        TUMBLING,
        /** SLERP from tumble end toward target orientation. */
        SETTLING,
        /** Locked on result face. DialoguePresenter shows result overlay. */
        HOLDING
    }

    private static final java.util.Random RNG = new java.util.Random();

    private State state = State.IDLE;
    private float[] currentQuat = Nat20Math.IDENTITY_QUAT.clone();
    private float[] targetQuat;
    private float[] settleStartQuat; // snapshot of orientation when settling begins
    private float[] angularVelocity;
    private float tumbleTimeRemaining;
    private float settleProgress;
    private int result;

    /**
     * Start a roll animation that will settle on the given result (1-20).
     * Called by Nat20DiceRoller / DiceWidget when a skill check triggers.
     */
    public void startRoll(int result) {
        this.result = result;
        this.targetQuat = DiceFaceLookup.RESULT_ROTATIONS[result];

        // Random initial spin axis and speed
        float[] axis = Nat20Math.normalize(new float[]{
            RNG.nextFloat() - 0.5f,
            RNG.nextFloat() - 0.5f,
            RNG.nextFloat() - 0.5f
        });
        float speed = 8f + RNG.nextFloat() * 6f; // 8-14 rad/s
        this.angularVelocity = new float[]{axis[0] * speed, axis[1] * speed, axis[2] * speed};

        this.tumbleTimeRemaining = 0.6f + RNG.nextFloat() * 0.3f; // 0.6-0.9s
        this.state = State.TUMBLING;
    }

    /**
     * Advance the animation by deltaTime seconds. Call every frame.
     */
    public void update(float deltaTime) {
        switch (state) {
            case TUMBLING -> {
                // Integrate rotation: q_new = q_delta * q_current
                float angSpeed = (float) Math.sqrt(Nat20Math.dot(angularVelocity, angularVelocity));
                if (angSpeed > 1e-6f) {
                    float[] axis = {
                        angularVelocity[0] / angSpeed,
                        angularVelocity[1] / angSpeed,
                        angularVelocity[2] / angSpeed
                    };
                    float[] deltaQuat = Nat20Math.quaternionFromAxisAngle(axis, angSpeed * deltaTime);
                    currentQuat = Nat20Math.quaternionMultiply(deltaQuat, currentQuat);
                }

                // Decelerate
                float decay = (float) Math.pow(0.15, deltaTime); // ~85% decay per second
                angularVelocity[0] *= decay;
                angularVelocity[1] *= decay;
                angularVelocity[2] *= decay;

                tumbleTimeRemaining -= deltaTime;
                if (tumbleTimeRemaining <= 0) {
                    settleProgress = 0f;
                    settleStartQuat = currentQuat.clone();
                    state = State.SETTLING;
                }
            }
            case SETTLING -> {
                settleProgress += deltaTime * 2.5f; // ~0.4s settle
                settleProgress = Math.min(settleProgress, 1.0f);

                float easedT = Nat20Math.easeOutCubic(settleProgress);
                currentQuat = Nat20Math.slerp(settleStartQuat, targetQuat, easedT);

                if (settleProgress >= 1.0f) {
                    currentQuat = targetQuat.clone();
                    state = State.HOLDING;
                }
            }
            case HOLDING, IDLE -> {
                // No-op
            }
        }
    }

    /** Current orientation as a quaternion (w, x, y, z). */
    public float[] getCurrentRotation() {
        return currentQuat;
    }

    public State getState() {
        return state;
    }

    public int getResult() {
        return result;
    }
}
