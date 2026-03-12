package com.chonbosmods.dice.render;

/**
 * Bridges the software renderer's pixel buffer to the Hytale UI system.
 * Owns both the renderer and animator, ticked each frame by DiceWidget.
 */
public class DiceRenderTarget {

    private final DiceRenderer renderer = new DiceRenderer();
    private final DiceAnimator animator = new DiceAnimator();

    /**
     * Advance animation and re-render. Call every UI tick.
     *
     * @param deltaTime seconds since last tick
     */
    public void tick(float deltaTime) {
        animator.update(deltaTime);
        renderer.render(animator.getCurrentRotation());
        // TODO: push renderer.getPixels() to SDK UI texture
    }

    public DiceRenderer getRenderer() {
        return renderer;
    }

    public DiceAnimator getAnimator() {
        return animator;
    }

    /** Raw ARGB pixels for the current frame. */
    public int[] getPixels() {
        return renderer.getPixels();
    }
}
