package com.chonbosmods.quest;

public enum QuestState {
    AVAILABLE,
    OBJECTIVE_PENDING,  // Objective assigned but dialogue session still open
    ACTIVE_OBJECTIVE,
    READY_FOR_TURN_IN,
    COMPLETED
}
