package com.chonbosmods.background;

import com.chonbosmods.stats.Stat;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BackgroundTest {

    @Test
    void hasFifteenBackgrounds() {
        assertEquals(15, Background.values().length);
    }

    @Test
    void everyBackgroundHasUniqueUnorderedStatPair() {
        Set<String> pairs = new HashSet<>();
        for (Background bg : Background.values()) {
            // Normalize to ordered pair to detect (STR,CON) == (CON,STR) duplicates.
            Stat a = bg.primary();
            Stat b = bg.secondary();
            String ordered = a.compareTo(b) <= 0
                    ? a.name() + "+" + b.name()
                    : b.name() + "+" + a.name();
            assertTrue(pairs.add(ordered),
                    "Duplicate unordered stat pair on background " + bg.name());
        }
    }

    @Test
    void everyBackgroundHasAtLeastOneKitItem() {
        for (Background bg : Background.values()) {
            assertFalse(bg.kit().isEmpty(),
                    bg.name() + " has no kit items");
        }
    }

    @Test
    void everyBackgroundHasNonBlankDisplayName() {
        for (Background bg : Background.values()) {
            assertNotNull(bg.displayName(), bg.name() + " displayName is null");
            assertFalse(bg.displayName().isBlank(), bg.name() + " displayName is blank");
        }
    }

    @Test
    void everyKitItemHasPositiveQuantityAndNonBlankItemId() {
        for (Background bg : Background.values()) {
            for (KitItem item : bg.kit()) {
                assertTrue(item.quantity() > 0,
                        bg.name() + " kit item " + item.itemId() + " has non-positive quantity");
                assertNotNull(item.itemId(), bg.name() + " kit item itemId is null");
                assertFalse(item.itemId().isBlank(), bg.name() + " kit item itemId is blank");
            }
        }
    }

    @Test
    void primaryAndSecondaryStatsAreDistinctOnEveryBackground() {
        for (Background bg : Background.values()) {
            assertNotEquals(bg.primary(), bg.secondary(),
                    bg.name() + " has identical primary and secondary stats");
        }
    }
}
