package com.chonbosmods.prefab;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Nat20PrefabConstantsTest {

    @Test
    void stripKeysMatchesExactFourteenKeySet() {
        Set<String> expected = Set.of(
                "Nat20_Anchor",
                "Nat20_Direction",
                "Nat20_Npc_Spawn",
                "Nat20_Mob_Group_Spawn",
                "Nat20_Chest_Spawn",
                "Nat20_Force_Empty",
                "Editor_Anchor",
                "Editor_Block",
                "Editor_Empty",
                "Prefab_Spawner_Block",
                "Spawner_Rat",
                "Block_Spawner_Block",
                "Block_Spawner_Block_Large",
                "Geyzer_Spawner1"
        );
        assertEquals(expected, Nat20PrefabConstants.STRIP_KEYS);
    }
}
