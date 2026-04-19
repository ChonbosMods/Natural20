package com.chonbosmods.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for the STR/INT attacker-bonus rule in
 * {@link Nat20ScoreDamageSystem}.
 *
 * <p>History: elemental damage used to receive BOTH {@code STR_mod x 10} from
 * the melee handler AND {@code INT_mod x 10} from the elemental handler, so a
 * player with both stats invested would see the two bonuses stack on every
 * fire/ice/void/poison hit (and on every elemental DoT tick, since DoT tick
 * damage was not guarded either).
 *
 * <p>The rule is now: pick exactly one attacker bonus based on damage type,
 * never both. DoT ticks receive none.
 */
class Nat20ScoreDamageSystemTest {

    @Test
    void elementalDamage_appliesIntOnly_doesNotStackStr() {
        // STR mod 5, INT mod 3 on an elemental hit: the old code added both
        // (50 + 30 = 80). New rule picks INT only.
        float bonus = Nat20ScoreDamageSystem.attackerBonus(5, 3, true);
        assertEquals(30f, bonus, 1e-4f);
    }

    @Test
    void physicalDamage_appliesStrOnly() {
        float bonus = Nat20ScoreDamageSystem.attackerBonus(5, 3, false);
        assertEquals(50f, bonus, 1e-4f);
    }

    @Test
    void elementalDamage_withZeroInt_addsNothingEvenIfStrPresent() {
        // Even if STR is high, elemental hits route to INT only.
        float bonus = Nat20ScoreDamageSystem.attackerBonus(8, 0, true);
        assertEquals(0f, bonus, 1e-4f);
    }

    @Test
    void physicalDamage_withZeroStr_addsNothingEvenIfIntPresent() {
        float bonus = Nat20ScoreDamageSystem.attackerBonus(0, 8, false);
        assertEquals(0f, bonus, 1e-4f);
    }

    @Test
    void zeroMods_noBonus() {
        assertEquals(0f, Nat20ScoreDamageSystem.attackerBonus(0, 0, true), 1e-4f);
        assertEquals(0f, Nat20ScoreDamageSystem.attackerBonus(0, 0, false), 1e-4f);
    }
}
