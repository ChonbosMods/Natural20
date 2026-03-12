package com.chonbosmods.dialogue.model;

public enum ExhaustionState {
    GRAYED,  // auto-exhausted: visible in left panel, clickable for recap text
    HIDDEN   // author-exhausted via EXHAUST_TOPIC: not rendered, not clickable
}
