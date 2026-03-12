package com.chonbosmods.action;

import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.data.Nat20NpcData;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.function.Consumer;

public record ActionContext(
    Player player,
    Ref<EntityStore> playerRef,
    Ref<EntityStore> npcRef,
    Store<EntityStore> store,
    String npcId,
    Nat20PlayerData playerData,
    Nat20NpcData npcData,
    Consumer<Integer> dispositionUpdater,
    Consumer<String> globalTopicUnlocker,
    Consumer<String> topicExhauster,
    Consumer<String> topicReactivator
) {}
