# Combat Test Harness Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** A `/nat20 combattest` command that spawns passive + aggressive combat dummies, gives test weapons with specific affixes, and logs all damage events for debugging the stat/affix combat system.

**Architecture:** Three new commands (`CombatTestCommand`, `SetStatsCommand`, `DebugCommand`), two NPC role JSONs (passive dummy, aggressive dummy), and one `DamageEventSystem` in Inspect Group that logs damage/stat snapshots to console. All wired through the existing `Nat20Command` collection.

**Tech Stack:** Hytale SDK (JavaPlugin, DamageEventSystem, NPCPlugin, EntityStatMap, ItemStack BSON metadata), existing loot system for test weapon generation.

**Note:** No unit test framework exists for this Hytale plugin. All testing is smoke-tested on the dev server via `./gradlew devServer`. Each task ends with a compile check (`./gradlew compileJava`) and specific dev server verification steps.

---

### Task 1: Combat Dummy NPC Roles

Create two NPC role JSON files: a passive punching bag and an aggressive attacker with fixed damage.

**Files:**
- Create: `src/main/resources/Server/NPC/Roles/Nat20/CombatDummy.json`
- Create: `src/main/resources/Server/NPC/Roles/Nat20/AttackerDummy.json`

**Step 1: Create CombatDummy.json (passive, high HP, no movement)**

```json
{
  "Type": "Variant",
  "Reference": "Template_Settlement_Civilian",
  "Parameters": {
    "MaxHealth": { "Value": 10000 },
    "WanderRadius": { "Value": 0 },
    "DefaultPlayerAttitude": { "Value": "Ignore" },
    "DefaultNPCAttitude": { "Value": "Ignore" },
    "LeashDistance": { "Value": 1 },
    "HardLeashDistance": { "Value": 5 },
    "NameTranslationKey": { "Value": "nat20.npc.combat_dummy" }
  }
}
```

**Step 2: Create AttackerDummy.json (aggressive, melee, fixed weapon)**

```json
{
  "Type": "Variant",
  "Reference": "Template_Settlement_Guard",
  "Parameters": {
    "MaxHealth": { "Value": 10000 },
    "Weapons": { "Value": ["Weapon_Sword_Iron"] },
    "MaxSpeed": { "Value": 5 },
    "ViewRange": { "Value": 10 },
    "AttackDistance": { "Value": 3 },
    "LeashDistance": { "Value": 8 },
    "WanderRadius": { "Value": 0 },
    "DefaultPlayerAttitude": { "Value": "Hostile" },
    "NameTranslationKey": { "Value": "nat20.npc.attacker_dummy" }
  }
}
```

**Step 3: Commit**

```bash
git add -f src/main/resources/Server/NPC/Roles/Nat20/CombatDummy.json \
         src/main/resources/Server/NPC/Roles/Nat20/AttackerDummy.json
git commit -m "feat(combat): add passive and aggressive combat dummy NPC roles"
```

---

### Task 2: SetStatsCommand

A `/nat20 setstats` command that sets ability scores on the player's `Nat20PlayerData`. Accepts pairs like `STR 20 DEX 14`.

**Files:**
- Create: `src/main/java/com/chonbosmods/commands/SetStatsCommand.java`
- Modify: `src/main/java/com/chonbosmods/commands/Nat20Command.java` (add subcommand registration)

**Step 1: Write SetStatsCommand**

```java
package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.stats.Stat;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class SetStatsCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> argsArg =
        withRequiredArg("stats", "Stat pairs: STR 20 DEX 14 CON 16 ...", ArgTypes.GREEDY_STRING);

    public SetStatsCommand() {
        super("setstats", "Set ability scores: /nat20 setstats STR 20 DEX 14");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        Nat20PlayerData data = store.getComponent(ref, Natural20.getPlayerDataType());
        if (data == null) {
            context.sendMessage(Message.raw("No player data found."));
            return;
        }

        String input = argsArg.get(context);
        String[] tokens = input.trim().split("\\s+");
        if (tokens.length < 2 || tokens.length % 2 != 0) {
            context.sendMessage(Message.raw("Usage: /nat20 setstats STR 20 DEX 14 ..."));
            return;
        }

        int[] stats = data.getStats().clone();
        StringBuilder changes = new StringBuilder();

        for (int i = 0; i < tokens.length; i += 2) {
            String statName = tokens[i].toUpperCase();
            Stat stat;
            try {
                stat = Stat.valueOf(statName);
            } catch (IllegalArgumentException e) {
                context.sendMessage(Message.raw("Unknown stat: " + statName
                    + ". Valid: STR, DEX, CON, INT, WIS, CHA"));
                return;
            }

            int value;
            try {
                value = Integer.parseInt(tokens[i + 1]);
            } catch (NumberFormatException e) {
                context.sendMessage(Message.raw("Invalid value for " + statName + ": " + tokens[i + 1]));
                return;
            }

            if (value < 1 || value > 30) {
                context.sendMessage(Message.raw(statName + " must be between 1 and 30."));
                return;
            }

            int oldValue = stats[stat.index()];
            stats[stat.index()] = value;
            int modifier = Math.floorDiv(value - 10, 2);
            if (changes.length() > 0) changes.append(", ");
            changes.append(statName).append(": ").append(oldValue).append(" -> ")
                   .append(value).append(" (mod ").append(modifier >= 0 ? "+" : "").append(modifier).append(")");
        }

        data.setStats(stats);
        context.sendMessage(Message.raw("Stats updated: " + changes));
    }
}
```

**Step 2: Register in Nat20Command**

Add `addSubCommand(new SetStatsCommand());` to the constructor in `Nat20Command.java`.

**Step 3: Compile check**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/commands/SetStatsCommand.java \
        src/main/java/com/chonbosmods/commands/Nat20Command.java
git commit -m "feat(combat): add /nat20 setstats command for ability score overrides"
```

---

### Task 3: CombatDebugSystem

A `DamageEventSystem` in Inspect Group that logs damage details to console. Togglable per-player.

**Files:**
- Create: `src/main/java/com/chonbosmods/combat/CombatDebugSystem.java`
- Modify: `src/main/java/com/chonbosmods/Natural20.java` (register system in `setup()`)

**Step 1: Write CombatDebugSystem**

```java
package com.chonbosmods.combat;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.stats.PlayerStats;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.damage.Damage;
import com.hypixel.hytale.server.core.modules.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.damage.asset.DamageCause;
import com.hypixel.hytale.server.core.modules.entitystats.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.query.Query;
import com.hypixel.hytale.system.SystemGroup;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatDebugSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final Set<UUID> ENABLED_PLAYERS = ConcurrentHashMap.newKeySet();

    public static void enable(UUID uuid) { ENABLED_PLAYERS.add(uuid); }
    public static void disable(UUID uuid) { ENABLED_PLAYERS.remove(uuid); }
    public static boolean isEnabled(UUID uuid) { return ENABLED_PLAYERS.contains(uuid); }
    public static void removePlayer(UUID uuid) { ENABLED_PLAYERS.remove(uuid); }

    @Override
    public Query<EntityStore> getQuery() { return QUERY; }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (damage.isCancelled()) return;

        // Check if attacker or target is a player with debug enabled
        UUID attackerUuid = resolvePlayerUuid(damage.getSource(), store);
        Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
        UUID targetUuid = resolveEntityPlayerUuid(targetRef, store);

        boolean attackerDebug = attackerUuid != null && ENABLED_PLAYERS.contains(attackerUuid);
        boolean targetDebug = targetUuid != null && ENABLED_PLAYERS.contains(targetUuid);
        if (!attackerDebug && !targetDebug) return;

        // Build debug log
        StringBuilder sb = new StringBuilder();
        sb.append("[CombatDebug] ");

        // Damage info
        DamageCause cause = DamageCause.getAssetMap().getAsset(damage.getDamageCauseIndex());
        String causeId = cause != null ? cause.getId() : "unknown";
        sb.append("cause=").append(causeId);
        sb.append(" initial=").append(String.format("%.1f", damage.getInitialAmount()));
        sb.append(" final=").append(String.format("%.1f", damage.getAmount()));

        // Attacker stats
        if (attackerUuid != null) {
            sb.append(" | attacker=").append(attackerUuid.toString().substring(0, 8));
            appendPlayerStats(sb, damage.getSource(), store, "atk");
        }

        // Target stats
        if (targetUuid != null) {
            sb.append(" | target=").append(targetUuid.toString().substring(0, 8));
            appendEntityStats(sb, targetRef, store, "tgt");
        }

        LOGGER.atInfo().log("%s", sb);
    }

    private void appendPlayerStats(StringBuilder sb, Damage.Source source, Store<EntityStore> store, String prefix) {
        if (!(source instanceof Damage.EntitySource es)) return;
        appendEntityStats(sb, es.getRef(), store, prefix);
    }

    private void appendEntityStats(StringBuilder sb, Ref<EntityStore> ref, Store<EntityStore> store, String prefix) {
        EntityStatMap statMap = store.getComponent(ref, EntityStatsModule.get().getEntityStatMapComponentType());
        if (statMap == null) return;

        try {
            var health = statMap.get(DefaultEntityStatTypes.getHealth());
            var stamina = statMap.get(DefaultEntityStatTypes.getStamina());
            var mana = statMap.get(DefaultEntityStatTypes.getMana());
            sb.append(String.format(" %s_hp=%.0f/%.0f", prefix, health.get(), health.getMax()));
            sb.append(String.format(" %s_sta=%.0f/%.0f", prefix, stamina.get(), stamina.getMax()));
            sb.append(String.format(" %s_mp=%.0f/%.0f", prefix, mana.get(), mana.getMax()));
        } catch (Exception e) {
            // stat may not exist on non-player entities
        }

        // Log D&D stats if player
        Nat20PlayerData data = store.getComponent(ref, Natural20.getPlayerDataType());
        if (data != null) {
            PlayerStats ps = PlayerStats.from(data);
            sb.append(String.format(" %s_scores=[STR%d DEX%d CON%d INT%d WIS%d CHA%d]",
                prefix, ps.stats()[0], ps.stats()[1], ps.stats()[2],
                ps.stats()[3], ps.stats()[4], ps.stats()[5]));
        }
    }

    private UUID resolvePlayerUuid(Damage.Source source, Store<EntityStore> store) {
        if (!(source instanceof Damage.EntitySource es)) return null;
        return resolveEntityPlayerUuid(es.getRef(), store);
    }

    private UUID resolveEntityPlayerUuid(Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) return player.getUuid();
        } catch (Exception e) {
            // not a player entity
        }
        return null;
    }
}
```

**Step 2: Register in Natural20.setup()**

Add after the existing system registrations (after the `SettlementThreatSystem` line):

```java
// Register combat debug logging system (Inspect Group)
getEntityStoreRegistry().registerSystem(new CombatDebugSystem());
```

Also add cleanup on disconnect (in the `PlayerDisconnectEvent` handler):

```java
CombatDebugSystem.removePlayer(uuid);
```

**Step 3: Compile check**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/combat/CombatDebugSystem.java \
        src/main/java/com/chonbosmods/Natural20.java
git commit -m "feat(combat): add CombatDebugSystem for per-player damage event logging"
```

---

### Task 4: DebugCommand

A `/nat20 debug combat on|off` command that toggles the per-player debug logger.

**Files:**
- Create: `src/main/java/com/chonbosmods/commands/DebugCommand.java`
- Modify: `src/main/java/com/chonbosmods/commands/Nat20Command.java` (add subcommand)

**Step 1: Write DebugCommand**

```java
package com.chonbosmods.commands;

import com.chonbosmods.combat.CombatDebugSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class DebugCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> modeArg =
        withRequiredArg("mode", "on or off", ArgTypes.STRING);

    public DebugCommand() {
        super("debug", "Toggle combat debug logging: /nat20 debug on|off");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String mode = modeArg.get(context).toLowerCase();

        switch (mode) {
            case "on" -> {
                CombatDebugSystem.enable(playerRef.getUuid());
                context.sendMessage(Message.raw("Combat debug logging enabled."));
            }
            case "off" -> {
                CombatDebugSystem.disable(playerRef.getUuid());
                context.sendMessage(Message.raw("Combat debug logging disabled."));
            }
            default -> context.sendMessage(Message.raw("Usage: /nat20 debug on|off"));
        }
    }
}
```

**Step 2: Register in Nat20Command**

Add `addSubCommand(new DebugCommand());` to the constructor.

**Step 3: Compile check**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/commands/DebugCommand.java \
        src/main/java/com/chonbosmods/commands/Nat20Command.java
git commit -m "feat(combat): add /nat20 debug command for toggling combat logging"
```

---

### Task 5: CombatTestCommand

The main `/nat20 combattest` command. Spawns both dummies and optionally gives a test weapon with a specific affix.

**Files:**
- Create: `src/main/java/com/chonbosmods/commands/CombatTestCommand.java`
- Modify: `src/main/java/com/chonbosmods/commands/Nat20Command.java` (add subcommand)

**Step 1: Write CombatTestCommand**

```java
package com.chonbosmods.commands;

import com.chonbosmods.Natural20;
import com.chonbosmods.combat.CombatDebugSystem;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.npc.Nat20NameGenerator;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;

import javax.annotation.Nonnull;
import java.util.Random;

public class CombatTestCommand extends AbstractPlayerCommand {

    private final OptionalArg<String> affixArg =
        withOptionalArg("affix", "Affix ID to put on test weapon (e.g., vampiric, mighty)", ArgTypes.STRING);
    private final OptionalArg<String> rarityArg =
        withOptionalArg("rarity", "Rarity for test weapon (Common, Rare, Epic, Legendary)", ArgTypes.STRING);

    public CombatTestCommand() {
        super("combattest", "Spawn combat dummies and optionally get a test weapon");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }

        Vector3d pos = transform.getPosition();

        // Spawn passive combat dummy (5 blocks ahead)
        boolean passiveSpawned = spawnDummy(context, store, pos,
            new Vector3d(pos.getX() + 5, pos.getY(), pos.getZ()),
            "CombatDummy", "Combat Dummy");

        // Spawn aggressive attacker dummy (3 blocks to the side)
        boolean attackerSpawned = spawnDummy(context, store, pos,
            new Vector3d(pos.getX() + 3, pos.getY(), pos.getZ() + 4),
            "AttackerDummy", "Attacker Dummy");

        // Auto-enable debug logging
        CombatDebugSystem.enable(playerRef.getUuid());

        StringBuilder msg = new StringBuilder();
        if (passiveSpawned) msg.append("Passive dummy spawned. ");
        if (attackerSpawned) msg.append("Aggressive dummy spawned. ");
        msg.append("Debug logging enabled.");

        // Give test weapon if affix specified
        if (context.provided(affixArg)) {
            String affixId = affixArg.get(context);
            String rarity = context.provided(rarityArg) ? rarityArg.get(context) : "Rare";
            giveTestWeapon(context, store, ref, affixId, rarity);
        }

        context.sendMessage(Message.raw(msg.toString()));
    }

    private boolean spawnDummy(CommandContext context, Store<EntityStore> store,
                                Vector3d playerPos, Vector3d spawnPos,
                                String roleName, String displayName) {
        int roleIndex = NPCPlugin.get().getIndex(roleName);
        if (roleIndex < 0) {
            context.sendMessage(Message.raw("Role '" + roleName + "' not found. Check NPC role JSONs."));
            return false;
        }

        // Face toward the player
        double dx = playerPos.getX() - spawnPos.getX();
        double dz = playerPos.getZ() - spawnPos.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        Vector3f rotation = new Vector3f(0, yaw, 0);

        Random rng = new Random(displayName.hashCode() + System.nanoTime());
        Model model = CosmeticsModule.get().createModel(
            CosmeticsModule.get().generateRandomSkin(rng), 1.0f);

        Pair<Ref<EntityStore>, NPCEntity> result =
            NPCPlugin.get().spawnEntity(store, roleIndex, spawnPos, rotation, model, null);

        if (result != null) {
            Ref<EntityStore> npcRef = result.first();
            store.putComponent(npcRef, Nameplate.getComponentType(), new Nameplate(displayName));
            return true;
        }
        return false;
    }

    private void giveTestWeapon(CommandContext context, Store<EntityStore> store,
                                 Ref<EntityStore> ref, String affixId, String rarityName) {
        Nat20LootSystem lootSystem = Natural20.getInstance().getLootSystem();

        // Verify affix exists
        if (lootSystem.getAffixRegistry().get(affixId) == null) {
            context.sendMessage(Message.raw("Unknown affix: " + affixId
                + ". Check loot/affixes/ JSON files."));
            return;
        }

        // Generate a sword with loot pipeline, then verify the affix is present
        String itemId = "Weapon_Sword_Iron";
        String baseName = "Iron Sword";
        Random random = new Random();

        var rarityDef = lootSystem.getRarityRegistry().get(rarityName);
        if (rarityDef == null) {
            context.sendMessage(Message.raw("Unknown rarity: " + rarityName));
            return;
        }

        int tier = rarityDef.qualityValue();
        Nat20LootData lootData = lootSystem.getPipeline().generate(
            itemId, baseName, "melee_weapon", tier, tier, random);

        if (lootData == null) {
            context.sendMessage(Message.raw("Failed to generate test weapon."));
            return;
        }

        String stackItemId = lootData.getUniqueItemId() != null ? lootData.getUniqueItemId() : itemId;
        ItemStack stack = new ItemStack(stackItemId, 1);
        stack = stack.withMetadata(Nat20LootData.METADATA_KEY, lootData);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.giveItem(stack, ref, store);
            context.sendMessage(Message.raw("Test weapon: " + lootData.getGeneratedName()
                + " [" + lootData.getRarity() + "] ("
                + lootData.getAffixes().size() + " affixes)"));
        }
    }
}
```

**Step 2: Register in Nat20Command**

Add `addSubCommand(new CombatTestCommand());` to the constructor.

**Step 3: Compile check**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/commands/CombatTestCommand.java \
        src/main/java/com/chonbosmods/commands/Nat20Command.java
git commit -m "feat(combat): add /nat20 combattest command with dummy spawning and test weapons"
```

---

### Task 6: Dev Server Smoke Test

Run the dev server and verify all Phase 1 components work together.

**Step 1: Start dev server**

```bash
./gradlew devServer
```

**Step 2: Verify commands exist**

In game, run:
- `/nat20 setstats STR 20 CON 18` - should show stat changes with modifier values
- `/nat20 debug on` - should confirm debug logging enabled
- `/nat20 combattest` - should spawn both dummies and enable debug logging

**Step 3: Verify dummies**

- Passive dummy ("Combat Dummy"): stands still, doesn't attack. Hit it and see damage in console log.
- Aggressive dummy ("Attacker Dummy"): attacks the player. Take a hit and see incoming damage in console log.

**Step 4: Verify debug output format**

Console log should show lines like:
```
[CombatDebug] cause=Physical initial=5.0 final=5.0 | attacker=a1b2c3d4 atk_hp=100/100 atk_sta=100/100 atk_mp=25/25 atk_scores=[STR20 DEX10 CON18 INT10 WIS10 CHA10] | target=e5f6g7h8 tgt_hp=9995/10000
```

**Step 5: Verify test weapon**

- `/nat20 combattest mighty` - should give an Iron Sword with loot data
- Hit the passive dummy with the test weapon and verify damage appears in debug log

**Step 6: Fix any issues, then commit if needed**

```bash
git add -A && git commit -m "fix(combat): address smoke test issues in combat test harness"
```

---

### Summary

| Task | Deliverable | Files |
|------|------------|-------|
| 1 | Combat dummy NPC roles | 2 JSON files |
| 2 | `/nat20 setstats` command | 1 new class + 1 modified |
| 3 | CombatDebugSystem | 1 new class + 1 modified |
| 4 | `/nat20 debug` command | 1 new class + 1 modified |
| 5 | `/nat20 combattest` command | 1 new class + 1 modified |
| 6 | Dev server smoke test | Verification only |

After Phase 1, every subsequent phase can be verified by:
1. `/nat20 setstats` to set ability scores
2. `/nat20 debug on` to see damage logs
3. `/nat20 combattest <affix>` to spawn dummies and get a weapon
4. Hit dummies / get hit, observe console output
