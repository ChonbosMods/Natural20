package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.cave.CaveVoidRecord;
import com.chonbosmods.cave.CaveVoidRegistry;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.quest.model.*;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.google.common.flogger.FluentLogger;
import com.google.gson.Gson;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class QuestGenerator {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Gson REWARD_DATA_GSON = new Gson();
    /** Vanilla Hytale rarity tier order, ascending. {@link #rollTierInRange} uses this to validate
     *  a difficulty's [rewardTierMin, rewardTierMax] interval and to roll uniformly within it. */
    private static final List<String> TIER_ORDER = List.of("Common", "Uncommon", "Rare", "Epic", "Legendary");
    /** Extra mobs spawned at a KILL_MOBS POI beyond the required kill count, so the
     *  player always finds enough live targets even after wandering wildlife eats them. */
    private static final int KILL_MOB_SPAWN_BUFFER = 2;
    /** Default KILL_MOBS bounds when an ObjectiveConfig omits countMin/countMax. */
    private static final int KILL_MOB_DEFAULT_MIN = 2;
    private static final int KILL_MOB_DEFAULT_MAX = 4;
    private final QuestTemplateRegistry templateRegistry;
    private final SettlementRegistry settlementRegistry;
    private final QuestPoolRegistry poolRegistry;
    private final QuestDifficultyRegistry difficultyRegistry;
    private final AtomicLong questCounter = new AtomicLong(System.currentTimeMillis());

    public QuestGenerator(QuestTemplateRegistry templateRegistry, SettlementRegistry settlementRegistry,
                           QuestPoolRegistry poolRegistry, QuestDifficultyRegistry difficultyRegistry) {
        this.templateRegistry = templateRegistry;
        this.settlementRegistry = settlementRegistry;
        this.poolRegistry = poolRegistry;
        this.difficultyRegistry = difficultyRegistry;
    }

    public @Nullable QuestInstance generate(String npcRole, String npcId,
                                             String npcSettlementCellKey,
                                             double npcX, double npcZ,
                                             Set<String> completedIds,
                                             List<String> subjectAffinities,
                                             String subjectPoiType,
                                             String subjectValue) {
        Random random = new Random();

        QuestTemplateV2 template = templateRegistry.selectV2ForRole(npcRole, random);
        if (template == null) {
            LOGGER.atWarning().log("No v2 quest templates available for role: %s", npcRole);
            return null;
        }

        // Roll difficulty up front: drives mob/gather count multipliers below,
        // reward tier/ilvl/XP, mob iLvl, and boss flag.
        // If the registry is empty, random() throws: a misconfigured registry
        // should block quest generation, not silently degrade.
        DifficultyConfig difficulty = difficultyRegistry.random(random);

        // Roll the actual reward item from the affix loot system. The display name
        // overrides whatever {reward_item} the template hand-pinned, since the
        // difficulty-driven roll is now the source of truth for what the player
        // gets at turn-in. The full Nat20LootData is captured below for dispense.
        // Exceptions propagate: a misconfigured loot system should fail loud.
        String rolledTier = rollTierInRange(difficulty.rewardTierMin(), difficulty.rewardTierMax(), random);
        ItemStack rewardStack = AffixRewardRoller.roll(rolledTier, difficulty.rewardIlvl(), random);
        Nat20LootData rewardLootData = rewardStack.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (rewardLootData == null) {
            throw new IllegalStateException(
                "AffixRewardRoller produced a stack without Nat20LootData metadata "
                    + "(itemId=" + rewardStack.getItemId() + ", tier=" + rolledTier
                    + ", ilvl=" + difficulty.rewardIlvl() + ")");
        }
        String rewardItemId = rewardStack.getItemId();
        int rewardItemCount = rewardStack.getQuantity();
        String rewardDisplayName = rewardLootData.getGeneratedName();

        // Resolve world bindings (settlement, target NPC, gather items, enemy mobs, POI)
        Map<String, String> bindings = resolveWorldBindings(npcRole, npcX, npcZ, npcSettlementCellKey, npcId, random);

        // Structured reward bindings. {reward_item} is the rolled affix item's
        // display name so dialogue lines like "Take this {reward_item}" highlight
        // the real reward; {reward_flavor} stays template-driven.
        bindings.put("reward_item", rewardDisplayName != null ? rewardDisplayName : "");
        bindings.put("reward_flavor",
            template.rewardFlavor() != null ? template.rewardFlavor() : "");

        // Store all template text in bindings for dialogue node construction
        bindings.put("quest_topic_header", template.topicHeader());
        bindings.put("quest_exposition_text", template.expositionText());
        bindings.put("quest_accept_text", template.acceptText());
        bindings.put("quest_decline_text", template.declineText());
        bindings.put("quest_exposition_turnin_text", template.expositionTurnInText());
        bindings.put("quest_conflict1_text", template.conflict1Text() != null ? template.conflict1Text() : "");
        bindings.put("quest_conflict1_turnin_text", template.conflict1TurnInText() != null ? template.conflict1TurnInText() : "");
        bindings.put("quest_conflict2_text", template.conflict2Text() != null ? template.conflict2Text() : "");
        bindings.put("quest_conflict2_turnin_text", template.conflict2TurnInText() != null ? template.conflict2TurnInText() : "");
        bindings.put("quest_conflict3_text", template.conflict3Text() != null ? template.conflict3Text() : "");
        bindings.put("quest_conflict3_turnin_text", template.conflict3TurnInText() != null ? template.conflict3TurnInText() : "");
        bindings.put("quest_conflict4_text", template.conflict4Text() != null ? template.conflict4Text() : "");
        bindings.put("quest_conflict4_turnin_text", template.conflict4TurnInText() != null ? template.conflict4TurnInText() : "");
        bindings.put("quest_resolution_text", template.resolutionText());

        // Target NPC dialogue for TALK_TO_NPC phases
        if (template.targetNpcOpener() != null) {
            bindings.put("target_npc_opener", template.targetNpcOpener());
        }
        if (template.targetNpcCloser() != null) {
            bindings.put("target_npc_closer", template.targetNpcCloser());
        }
        if (template.targetNpcOpener2() != null) {
            bindings.put("target_npc_opener_2", template.targetNpcOpener2());
        }
        if (template.targetNpcCloser2() != null) {
            bindings.put("target_npc_closer_2", template.targetNpcCloser2());
        }

        // Skill check (optional). When present, DialogueManager builds a SkillCheckNode
        // and a third response option on the entry node. The pass branch fires
        // MARK_SKILLCHECK_PASSED before the accept node so TURN_IN_V2 picks up the bonus.
        if (template.skillCheck() != null) {
            QuestTemplateV2.SkillCheck sc = template.skillCheck();
            bindings.put("quest_skillcheck_skill", sc.skill().name());
            bindings.put("quest_skillcheck_dc", String.valueOf(sc.dc()));
            bindings.put("quest_skillcheck_pass_text", sc.passText());
            bindings.put("quest_skillcheck_fail_text", sc.failText());
        }

        // Conflict count is template-driven: a 2-objective template has 1 conflict, a
        // 5-objective template has 4 conflicts. The roll-vs-cap model from v1 is gone.
        List<ObjectiveConfig> objConfigs = template.objectives();
        if (objConfigs == null || objConfigs.isEmpty()) {
            LOGGER.atWarning().log("V2 template %s has no objective configs", template.id());
            return null;
        }
        int maxConflicts = objConfigs.size() - 1;
        int totalObjectives = objConfigs.size();

        List<ObjectiveInstance> objectives = new ArrayList<>();
        for (int i = 0; i < totalObjectives; i++) {
            ObjectiveConfig config = objConfigs.get(i);
            ObjectiveType type = config.type() != null ? config.type() : ObjectiveType.COLLECT_RESOURCES;
            ObjectiveInstance obj = createObjective(type, config, bindings, random, difficulty);
            if (obj != null) objectives.add(obj);
        }

        if (objectives.isEmpty()) {
            LOGGER.atWarning().log("Failed to create any objectives for v2 template %s, npc=%s",
                template.situation(), npcId);
            return null;
        }

        // Build objective summary for exposition objective
        buildObjectiveSummary(objectives.getFirst(), bindings);

        String questId = "quest_" + template.situation().toLowerCase() + "_" + Long.toHexString(questCounter.incrementAndGet());

        QuestInstance quest = new QuestInstance(
            questId, template.situation(), npcId, npcSettlementCellKey, objectives, bindings
        );
        quest.setMaxConflicts(maxConflicts);
        quest.setDifficultyId(difficulty.id());

        // Persist the rolled reward on the QuestInstance so TURN_IN_V2 can dispense it
        // verbatim. The Nat20LootData JSON round-trips cleanly because every field on the
        // class is a primitive (affixes/gems serialize to '=' delimited strings).
        quest.setRewardXp(difficulty.xpAmount());
        quest.setRewardItemId(rewardItemId);
        quest.setRewardItemCount(rewardItemCount);
        quest.setRewardItemDisplayName(rewardDisplayName);
        quest.setRewardItemDataJson(REWARD_DATA_GSON.toJson(rewardLootData));

        LOGGER.atInfo().log(
            "Generated v2 quest %s: situation=%s, difficulty=%s, objectives=%d, "
                + "rewardTier=%s, rewardItem=%s (id=%s), rewardXp=%d, for NPC %s",
            questId, template.situation(), difficulty.id(), objectives.size(),
            rolledTier, rewardDisplayName, rewardItemId, difficulty.xpAmount(), npcId);
        return quest;
    }

    /**
     * Roll a tier uniformly within {@code [min, max]} (inclusive), respecting
     * {@link #TIER_ORDER}. Throws {@link IllegalArgumentException} when either
     * bound is unrecognized or when {@code max} sorts before {@code min}: a
     * misconfigured {@link DifficultyConfig} should fail loud at generation
     * time, not silently collapse to a single tier.
     */
    private static String rollTierInRange(String min, String max, Random random) {
        int minIdx = TIER_ORDER.indexOf(min);
        int maxIdx = TIER_ORDER.indexOf(max);
        if (minIdx < 0) {
            throw new IllegalArgumentException(
                "rewardTierMin '" + min + "' is not a recognized tier; expected one of " + TIER_ORDER);
        }
        if (maxIdx < 0) {
            throw new IllegalArgumentException(
                "rewardTierMax '" + max + "' is not a recognized tier; expected one of " + TIER_ORDER);
        }
        if (maxIdx < minIdx) {
            throw new IllegalArgumentException(
                "rewardTierMax '" + max + "' sorts before rewardTierMin '" + min + "' in " + TIER_ORDER);
        }
        return TIER_ORDER.get(minIdx + random.nextInt(maxIdx - minIdx + 1));
    }

    /**
     * Build the binding map a quest needs at generation time. Two classes of values:
     *
     * <ol>
     *   <li><b>World context</b> tied to the quest giver and their settlement:
     *       {@code settlement_name}, {@code settlement_type}, {@code self_role},
     *       {@code settlement_npc}, {@code settlement_npc_role}.
     *   <li><b>Cross-settlement context</b> for TALK_TO_NPC and references to elsewhere:
     *       {@code other_settlement}, {@code target_npc}, {@code target_npc_role},
     *       {@code target_npc_settlement} (the trio always describes the same NPC).
     * </ol>
     *
     * <p>Plus the gather/enemy item bindings used by per-objective overlay in
     * {@link DialogueResolver#resolveQuestText}.
     */
    private Map<String, String> resolveWorldBindings(String npcRole, double npcX, double npcZ,
                                                      String npcCellKey, String npcId, Random random) {
        Map<String, String> bindings = new HashMap<>();
        bindings.put("quest_giver_name", npcId);
        bindings.put("npc_x", String.valueOf(npcX));
        bindings.put("npc_z", String.valueOf(npcZ));
        bindings.put("npc_settlement_key", npcCellKey);

        // ----- Speaker / settlement -----
        bindings.put("self_role", NpcRecord.displayRole(npcRole));

        SettlementRecord settlement = settlementRegistry.getByCell(npcCellKey);
        if (settlement != null) {
            bindings.put("settlement_name", settlement.deriveName());
            bindings.put("settlement_type", settlement.getSettlementType().getDisplayLabel());

            // Pick one settlement-mate (not the speaker) for {settlement_npc} flavor reference
            List<NpcRecord> mates = new ArrayList<>();
            for (NpcRecord npc : settlement.getNpcs()) {
                if (!npc.getGeneratedName().equals(npcId)) mates.add(npc);
            }
            if (!mates.isEmpty()) {
                NpcRecord mate = mates.get(random.nextInt(mates.size()));
                bindings.put("settlement_npc", mate.getGeneratedName());
                bindings.put("settlement_npc_role", mate.getDisplayRole());
            }
        }

        // ----- Per-objective fallbacks (overlaid by DialogueResolver per phase) -----
        // These globals exist so smalltalk-side quest commentary still has *something*
        // to read. The per-objective overlay in resolveQuestText wins for actual quest text.
        QuestPoolRegistry.ItemEntry gatherItem = poolRegistry.randomCollectResource(random);
        bindings.put("quest_item", gatherItem.noun());
        bindings.put("quest_item_full", gatherItem.fullForm());
        if (gatherItem.category() != null) {
            bindings.put("gather_category", gatherItem.category());
        }

        QuestPoolRegistry.ItemEntry enemyMob = poolRegistry.randomHostileMob(random);
        bindings.put("enemy_type", enemyMob.label());
        bindings.put("enemy_type_plural", enemyMob.labelPlural());
        bindings.put("enemy_type_id", enemyMob.id());

        // ----- Cross-settlement context: target_npc trio + other_settlement -----
        // The three target_npc bindings always describe the same NPC: an author can
        // safely write "{target_npc}, the {target_npc_role} from {target_npc_settlement}"
        // and it will resolve consistently.
        SettlementRecord nearestOther = findNearestOtherSettlement(npcX, npcZ, npcCellKey);
        if (nearestOther != null) {
            bindings.put("other_settlement", nearestOther.deriveName());
            // Internal binding (NOT a template variable): used by createObjective for waypoint
            // and locationId, kept separate from the display-name {target_npc_settlement}.
            bindings.put("target_npc_settlement_key", nearestOther.getCellKey());

            if (!nearestOther.getNpcs().isEmpty()) {
                NpcRecord targetNpc = nearestOther.getNpcs().get(
                    random.nextInt(nearestOther.getNpcs().size()));
                bindings.put("target_npc", targetNpc.getGeneratedName());
                bindings.put("target_npc_role", targetNpc.getDisplayRole());
                bindings.put("target_npc_settlement", nearestOther.deriveName());
            }
        }
        if (!bindings.containsKey("target_npc")) {
            bindings.put("target_npc", "someone who might know more");
        }

        return bindings;
    }

    private @Nullable SettlementRecord findNearestOtherSettlement(double x, double z, String excludeCellKey) {
        SettlementRecord nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (SettlementRecord record : settlementRegistry.getAll().values()) {
            if (record.getCellKey().equals(excludeCellKey)) continue;
            double dx = record.getPosX() - x;
            double dz = record.getPosZ() - z;
            double dist = dx * dx + dz * dz;
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = record;
            }
        }
        return nearest;
    }

    private void buildObjectiveSummary(ObjectiveInstance obj, Map<String, String> bindings) {
        if (obj == null) return;
        String summary = switch (obj.getType()) {
            case KILL_MOBS -> "kill " + obj.getRequiredCount() + " " + obj.getEffectiveLabel();
            case COLLECT_RESOURCES -> "collect " + obj.getRequiredCount() + " " + obj.getEffectiveLabel();
            case FETCH_ITEM -> "retrieve " + obj.getTargetLabel();
            case PEACEFUL_FETCH -> "pick up " + obj.getTargetLabel();
            case TALK_TO_NPC -> "speak with " + obj.getTargetLabel();
        };
        bindings.put("quest_objective_summary", summary);
    }

    private @Nullable ObjectiveInstance createObjective(ObjectiveType type, ObjectiveConfig config,
                                                         Map<String, String> bindings, Random random,
                                                         DifficultyConfig difficulty) {
        String npcId = bindings.getOrDefault("quest_giver_name", "unknown");

        return switch (type) {
            case COLLECT_RESOURCES -> {
                // Each COLLECT objective draws its OWN gather item so a multi-collect quest
                // doesn't show the same item label across phases.
                QuestPoolRegistry.ItemEntry collectItem = poolRegistry.randomCollectResource(random);

                // Use per-item count range from the pool if available, otherwise the variant config.
                int count;
                if (collectItem.countMin() > 0 && collectItem.countMax() > 0) {
                    int min = collectItem.countMin();
                    int max = collectItem.countMax();
                    count = min + random.nextInt(max - min + 1);
                } else {
                    count = config.rollCount(random);
                }

                // Difficulty multiplier: clamp to 1 so easy + small base count never zeros out.
                count = Math.max(1, (int) Math.round(count * difficulty.gatherCountMultiplier()));

                bindings.put("quest_item", collectItem.noun());
                bindings.put("quest_item_full", collectItem.fullForm());
                bindings.put("gather_count", String.valueOf(count));

                ObjectiveInstance collectObj = new ObjectiveInstance(
                    type, collectItem.id(), collectItem.noun(),
                    count, null, null
                );
                collectObj.setTargetLabelPlural(collectItem.nounPlural());
                collectObj.setTargetEpithet(collectItem.epithet());
                yield collectObj;
            }
            case KILL_MOBS -> {
                // Try to pre-claim a cave void; if unavailable, create objective without POI
                // and let resolveAndPlacePoi handle it at runtime (void discovery or surface fallback).
                // The difficulty mob multiplier is applied inside rollKillCount() so both the
                // POI path and the deferred fallback path scale identically, and the
                // populationSpec spawn-buffer invariant in createPOIObjective is preserved.
                ObjectiveInstance killObj = createPOIObjective(type, bindings, config, random, difficulty);
                if (killObj == null) {
                    LOGGER.atInfo().log("KILL_MOBS for %s: no void at generation, deferring POI to runtime", npcId);
                    int killCount = rollKillCount(config, random, difficulty.mobCountMultiplier());
                    bindings.put("kill_count", String.valueOf(killCount));
                    killObj = new ObjectiveInstance(type, "deferred_poi", bindings.get("enemy_type"),
                        killCount, null, null);
                    killObj.setTargetLabelPlural(bindings.get("enemy_type_plural"));
                }
                yield killObj;
            }
            case FETCH_ITEM -> {
                // Draw a keepsake or evidence item. Template may pin a specific item type
                // (e.g. "quest_vial"); otherwise the drawn pool entry's fetchItemType wins.
                QuestPoolRegistry.ItemEntry fetchEntry = random.nextBoolean()
                    ? poolRegistry.randomKeepsakeItem(random)
                    : poolRegistry.randomEvidenceItem(random);

                String fetchItemType = config.fetchItem() != null
                    ? QuestPoolRegistry.capitalize(config.fetchItem())
                    : QuestPoolRegistry.getBaseItemType(fetchEntry);

                bindings.put("quest_item", fetchEntry.noun());
                bindings.put("quest_item_full", fetchEntry.fullForm());
                bindings.put("fetch_item_type", fetchItemType);
                bindings.put("fetch_item_label", fetchEntry.noun());

                ObjectiveInstance fetchObj = createPOIObjective(type, bindings, config, random, difficulty);
                if (fetchObj != null) {
                    fetchObj.setTargetEpithet(fetchEntry.epithet());
                    yield fetchObj;
                }

                ObjectiveInstance fallback = new ObjectiveInstance(
                    type, fetchEntry.id(), fetchEntry.noun(),
                    1, null, null
                );
                fallback.setTargetEpithet(fetchEntry.epithet());
                yield fallback;
            }
            case PEACEFUL_FETCH -> {
                QuestPoolRegistry.ItemEntry fetchEntry = poolRegistry.randomKeepsakeItem(random);
                bindings.put("quest_item", fetchEntry.noun());
                bindings.put("quest_item_full", fetchEntry.fullForm());
                String fetchItemType = QuestPoolRegistry.getBaseItemType(fetchEntry);
                bindings.put("fetch_item_type", fetchItemType);
                bindings.put("fetch_item_label", fetchEntry.noun());
                bindings.put("fetch_variant", "peaceful");

                String targetSettlementKey = bindings.get("target_npc_settlement_key");

                ObjectiveInstance peacefulObj = new ObjectiveInstance(
                    type, fetchEntry.id(), fetchEntry.noun(),
                    1, null, targetSettlementKey
                );
                peacefulObj.setTargetEpithet(fetchEntry.epithet());
                yield peacefulObj;
            }
            case TALK_TO_NPC -> {
                String targetNpc = bindings.get("target_npc");
                boolean hasTarget = targetNpc != null && !"someone who might know more".equals(targetNpc);
                if (!hasTarget) {
                    LOGGER.atInfo().log("TALK_TO_NPC for %s: no target at generation, deferring to runtime", npcId);
                }
                yield new ObjectiveInstance(
                    type,
                    hasTarget ? targetNpc : "deferred_npc",
                    hasTarget ? targetNpc : "someone nearby",
                    1,
                    null,
                    bindings.get("target_npc_settlement_key")
                );
            }
        };
    }

    private ObjectiveInstance createPOIObjective(ObjectiveType type, Map<String, String> bindings,
                                                  ObjectiveConfig config, Random random,
                                                  DifficultyConfig difficulty) {
        // Find and claim a unique void for this objective
        double npcX = 0, npcZ = 0;
        try {
            npcX = Double.parseDouble(bindings.getOrDefault("npc_x", "0"));
            npcZ = Double.parseDouble(bindings.getOrDefault("npc_z", "0"));
        } catch (NumberFormatException ignored) {}

        CaveVoidRegistry voidRegistry = Natural20.getInstance().getCaveVoidRegistry();
        if (voidRegistry == null) return null;

        CaveVoidRecord void_ = voidRegistry.findNearbyVoid(npcX, npcZ, 200, 600);
        if (void_ == null) {
            LOGGER.atInfo().log("createPOIObjective: no unclaimed void in range for %s objective", type);
            return null;
        }

        // Claim immediately so subsequent objectives get different voids
        String settlementKey = bindings.getOrDefault("npc_settlement_key", "unknown");
        voidRegistry.claimVoid(void_, settlementKey);

        int cx = void_.getCenterX(), cy = void_.getCenterY(), cz = void_.getCenterZ();
        String poiLocationId = "poi:" + cx + "," + cy + "," + cz;
        String hint = DirectionUtil.computeHint(npcX, npcZ, cx, cz);

        // Required count: KILL_MOBS reads countMin/countMax from the template (with a
        // default if omitted); FETCH_ITEM is always 1 because the chest contains the item.
        int requiredCount = switch (type) {
            case KILL_MOBS -> rollKillCount(config, random, difficulty.mobCountMultiplier());
            default -> 1; // FETCH_ITEM
        };

        // Spawn count: KILL_MOBS spawns required + buffer so the player always finds
        // enough live targets at the POI even after wandering wildlife eats them.
        int spawnCount = switch (type) {
            case KILL_MOBS -> requiredCount + KILL_MOB_SPAWN_BUFFER;
            default -> 3; // FETCH_ITEM guards
        };
        // populationSpec format: KILL_MOBS:<enemyId>:<spawnCount>:<mobIlvl>:<mobBoss>:<bossIlvlOffset>
        // The trailing three fields propagate difficulty so the spawn-side can apply
        // iLvl scaling and boss promotion when the player arrives at the POI.
        // Downstream spawn code does not yet read those fields (Task D-future).
        String populationSpec = "KILL_MOBS:" + bindings.get("enemy_type_id") + ":" + spawnCount
            + ":" + difficulty.mobIlvl()
            + ":" + difficulty.mobBoss()
            + ":" + difficulty.bossIlvlOffset();

        if (type == ObjectiveType.KILL_MOBS) {
            bindings.put("kill_count", String.valueOf(requiredCount));
        } else {
            bindings.put("gather_count", String.valueOf(requiredCount));
        }

        String targetLabel = switch (type) {
            case KILL_MOBS -> bindings.get("enemy_type");
            case FETCH_ITEM -> bindings.get("quest_item");
            default -> "a cave dungeon";
        };

        ObjectiveInstance poiObj = new ObjectiveInstance(type, poiLocationId, targetLabel,
                requiredCount, hint, poiLocationId);
        poiObj.setPoi(cx, cy, cz, populationSpec);
        if (type == ObjectiveType.KILL_MOBS) {
            poiObj.setTargetLabelPlural(bindings.get("enemy_type_plural"));
        } else if (type == ObjectiveType.FETCH_ITEM) {
            poiObj.setTargetLabelPlural(bindings.get("quest_item"));
        }

        LOGGER.atInfo().log("createPOIObjective: claimed void at (%d,%d,%d) for %s objective (required=%d spawn=%d)",
            cx, cy, cz, type, requiredCount, spawnCount);
        return poiObj;
    }

    /** Roll the required kill count for a KILL_MOBS objective, respecting countMin/countMax
     *  from the template config and falling back to default bounds when omitted.
     *  The difficulty multiplier is applied here (clamped to a minimum of 1) so every
     *  KILL_MOBS code path scales identically and the spawn-buffer invariant in
     *  createPOIObjective ({@code spawnCount = requiredCount + KILL_MOB_SPAWN_BUFFER})
     *  remains correct without a second multiplication. */
    private int rollKillCount(ObjectiveConfig config, Random random, double difficultyMultiplier) {
        int min = (config != null && config.countMin() != null) ? config.countMin() : KILL_MOB_DEFAULT_MIN;
        int max = (config != null && config.countMax() != null) ? config.countMax() : KILL_MOB_DEFAULT_MAX;
        if (max < min) max = min;
        int rolled = min + random.nextInt(max - min + 1);
        return Math.max(1, (int) Math.round(rolled * difficultyMultiplier));
    }

}
