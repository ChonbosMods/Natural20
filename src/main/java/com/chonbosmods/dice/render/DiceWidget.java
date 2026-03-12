package com.chonbosmods.dice.render;

import com.chonbosmods.dice.SkillCheckResult;

/**
 * Integration point between the dice renderer and DialoguePresenter.
 * Manages visibility, triggers rolls, and controls result overlay timing.
 */
public class DiceWidget {

    private final DiceRenderTarget renderTarget = new DiceRenderTarget();
    private boolean visible = false;
    private boolean showResultOverlay = false;
    private int lastResult = -1;

    /**
     * Trigger a dice roll animation for a skill check result.
     * Called by DialoguePresenter when a skill check node is reached.
     */
    public void triggerRoll(SkillCheckResult result) {
        this.lastResult = result.naturalRoll();
        this.visible = true;
        this.showResultOverlay = false;
        renderTarget.getAnimator().startRoll(result.naturalRoll());
    }

    /**
     * Update animation state. Call every UI tick while visible.
     */
    public void update(float deltaTime) {
        if (!visible) return;

        renderTarget.tick(deltaTime);

        if (renderTarget.getAnimator().getState() == DiceAnimator.State.HOLDING) {
            showResultOverlay = true;
        }
    }

    /** Dismiss the dice widget. */
    public void hide() {
        visible = false;
        showResultOverlay = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isShowResultOverlay() {
        return showResultOverlay;
    }

    public int getLastResult() {
        return lastResult;
    }

    public DiceRenderTarget getRenderTarget() {
        return renderTarget;
    }
}
