package com.chonbosmods.dialogue;

import com.chonbosmods.dice.RollMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DispositionBracketTest {

    @Test
    void rollMode_hostileBand() {
        assertEquals(RollMode.DISADVANTAGE, DispositionBracket.rollMode(0));
        assertEquals(RollMode.DISADVANTAGE, DispositionBracket.rollMode(24));
    }

    @Test
    void rollMode_neutralBand() {
        assertEquals(RollMode.NORMAL, DispositionBracket.rollMode(25));
        assertEquals(RollMode.NORMAL, DispositionBracket.rollMode(50));
        assertEquals(RollMode.NORMAL, DispositionBracket.rollMode(74));
    }

    @Test
    void rollMode_friendlyBand() {
        assertEquals(RollMode.ADVANTAGE, DispositionBracket.rollMode(75));
        assertEquals(RollMode.ADVANTAGE, DispositionBracket.rollMode(100));
    }

    @Test
    void rollMode_clampsOutOfRange() {
        assertEquals(RollMode.DISADVANTAGE, DispositionBracket.rollMode(-5));
        assertEquals(RollMode.ADVANTAGE, DispositionBracket.rollMode(150));
    }

    @Test
    void textPoolFromDisposition_unchanged() {
        assertEquals("hostile", DispositionBracket.textPoolFromDisposition(0));
        assertEquals("unfriendly", DispositionBracket.textPoolFromDisposition(20));
        assertEquals("neutral", DispositionBracket.textPoolFromDisposition(40));
        assertEquals("friendly", DispositionBracket.textPoolFromDisposition(60));
        assertEquals("loyal", DispositionBracket.textPoolFromDisposition(80));
        assertEquals("loyal", DispositionBracket.textPoolFromDisposition(100));
    }
}
