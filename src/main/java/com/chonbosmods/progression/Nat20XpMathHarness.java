package com.chonbosmods.progression;

/**
 * Self-verifying harness for Nat20XpMath. Run via:
 *   ./gradlew compileJava -q && java -cp build/classes/java/main com.chonbosmods.progression.Nat20XpMathHarness
 * (or via /nat20 debug xpmath once Phase 8 lands).
 * Expected values come from the XP spec §4 full level table and ilvl spec §3.2.
 */
public final class Nat20XpMathHarness {

    private static int passed = 0;
    private static int total = 0;

    public static void main(String[] args) {
        // xpToNextLevel: spec §4 table values.
        assertEq("xpToNextLevel(1)",  909,    Nat20XpMath.xpToNextLevel(1));
        assertEq("xpToNextLevel(5)",  1430,   Nat20XpMath.xpToNextLevel(5));
        assertEq("xpToNextLevel(10)", 2520,   Nat20XpMath.xpToNextLevel(10));
        assertEq("xpToNextLevel(20)", 7829,   Nat20XpMath.xpToNextLevel(20));
        assertEq("xpToNextLevel(39)", 67429,  Nat20XpMath.xpToNextLevel(39));
        assertEq("xpToNextLevel(40)", 75520,  Nat20XpMath.xpToNextLevel(40));

        // cumulativeXp: spec §4 "Cumulative XP" column, interpreted as XP to REACH level L.
        assertEq("cumulativeXp(1)",  0L,      Nat20XpMath.cumulativeXp(1));
        assertEq("cumulativeXp(2)",  909L,    Nat20XpMath.cumulativeXp(2));
        assertEq("cumulativeXp(11)", 15948L,  Nat20XpMath.cumulativeXp(11));
        assertEq("cumulativeXp(40)", 621750L, Nat20XpMath.cumulativeXp(40));
        assertEq("cumulativeXp(41)", 697270L, Nat20XpMath.cumulativeXp(41));

        // levelForTotalXp inverse.
        assertEq("levelForTotalXp(0)",      1,  Nat20XpMath.levelForTotalXp(0));
        assertEq("levelForTotalXp(908)",    1,  Nat20XpMath.levelForTotalXp(908));
        assertEq("levelForTotalXp(909)",    2,  Nat20XpMath.levelForTotalXp(909));
        assertEq("levelForTotalXp(15947)",  10, Nat20XpMath.levelForTotalXp(15947));
        assertEq("levelForTotalXp(15948)",  11, Nat20XpMath.levelForTotalXp(15948));
        assertEq("levelForTotalXp(621750)", 40, Nat20XpMath.levelForTotalXp(621750));
        assertEq("levelForTotalXp(1_000_000_000L)", 40, Nat20XpMath.levelForTotalXp(1_000_000_000L));

        // zoneForLevel boundaries.
        assertEq("zoneForLevel(1)",  1, Nat20XpMath.zoneForLevel(1));
        assertEq("zoneForLevel(10)", 1, Nat20XpMath.zoneForLevel(10));
        assertEq("zoneForLevel(11)", 2, Nat20XpMath.zoneForLevel(11));
        assertEq("zoneForLevel(40)", 4, Nat20XpMath.zoneForLevel(40));

        // mobKillXp: spec §4 table, Regular at level 1 should be 15.
        assertEq("mobKillXp(1, 1.0)",  15,  Nat20XpMath.mobKillXp(1, 1.0));
        assertEq("mobKillXp(1, 5.0)",  75,  Nat20XpMath.mobKillXp(1, 5.0));
        assertEq("mobKillXp(11, 1.0)", 46,  Nat20XpMath.mobKillXp(11, 1.0));
        assertEq("mobKillXp(31, 1.0)", 449, Nat20XpMath.mobKillXp(31, 1.0));

        // questPhaseXp: spec §7.2.
        assertEq("questPhaseXp(1)",  105, Nat20XpMath.questPhaseXp(1));
        assertEq("questPhaseXp(20)", 944, Nat20XpMath.questPhaseXp(20));

        // d20SuccessXp: spec §3.1 parity with boss kill (both 5U).
        assertEq("d20SuccessXp(1)",  75, Nat20XpMath.d20SuccessXp(1));
        assertEq("d20SuccessXp(11)", 232, Nat20XpMath.d20SuccessXp(11));

        // ilvlScale: ilvl spec §3.2 table. Legendary at ilvl 40 ~= 2.44.
        assertNear("ilvlScale(1, 1)",   1.0,  Nat20XpMath.ilvlScale(1, 1),  0.001);
        assertNear("ilvlScale(10, 5)",  1.33, Nat20XpMath.ilvlScale(10, 5), 0.01);
        assertNear("ilvlScale(40, 5)",  2.44, Nat20XpMath.ilvlScale(40, 5), 0.01);
        assertNear("ilvlScale(45, 5)",  2.63, Nat20XpMath.ilvlScale(45, 5), 0.01);

        System.out.printf("%n=== Nat20XpMathHarness: %d/%d passed ===%n", passed, total);
        System.exit(passed == total ? 0 : 1);
    }

    private static void assertEq(String label, long expected, long actual) {
        total++;
        if (expected == actual) { passed++; System.out.printf("PASS %s = %d%n", label, actual); }
        else                    { System.out.printf("FAIL %s: expected %d, got %d%n", label, expected, actual); }
    }
    private static void assertEq(String label, int expected, int actual) {
        assertEq(label, (long) expected, (long) actual);
    }
    private static void assertNear(String label, double expected, double actual, double tolerance) {
        total++;
        if (Math.abs(expected - actual) <= tolerance) { passed++; System.out.printf("PASS %s = %.3f%n", label, actual); }
        else { System.out.printf("FAIL %s: expected ~%.3f, got %.3f%n", label, expected, actual); }
    }
}
