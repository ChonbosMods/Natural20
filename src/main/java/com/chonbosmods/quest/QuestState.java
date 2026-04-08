package com.chonbosmods.quest;

public enum QuestState {
    AVAILABLE,
    OBJECTIVE_PENDING,  // Objective assigned but dialogue session still open
    ACTIVE_OBJECTIVE,
    READY_FOR_TURN_IN,
    /** Items consumed and reward claimed, but the player has not yet selected
     *  [CONTINUE] in the turn-in dialog. The next objective is NOT yet set up.
     *  If the player closes the dialog in this state, the turn-in topic re-displays
     *  next session and TURN_IN_V2 no-ops on re-fire so rewards don't double-claim. */
    AWAITING_CONTINUATION,
    COMPLETED
}
