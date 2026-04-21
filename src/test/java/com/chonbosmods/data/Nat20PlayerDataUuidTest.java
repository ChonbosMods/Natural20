package com.chonbosmods.data;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Nat20PlayerDataUuidTest {

    @Test
    void newInstanceHasNullPlayerUuid() {
        assertNull(new Nat20PlayerData().getPlayerUuid());
    }

    @Test
    void setPlayerUuidPersistsTheRuntimeReference() {
        Nat20PlayerData data = new Nat20PlayerData();
        UUID u = UUID.randomUUID();
        data.setPlayerUuid(u);
        assertEquals(u, data.getPlayerUuid());
    }
}
