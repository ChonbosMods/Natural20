package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.cave.CaveVoidRecord;
import com.chonbosmods.cave.CaveVoidRegistry;
import com.chonbosmods.quest.model.*;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class QuestGenerator {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final double CONFLICT_1_CHANCE = 0.40;
    private static final double CONFLICT_2_CHANCE = 0.10;
    private static final double STAKES_EQUALS_FOCUS_CHANCE = 0.15;
    private static final double TALK_NPC_HANDOFF_CHANCE = 0.75;
    private static final Set<String> INVESTIGATION_SITUATIONS = Set.of(
        "DiscoveryOfDishonor", "ErroneousJudgment", "TheEnigma", "RivalryOfSuperiorVsInferior"
    );

    private final QuestTemplateRegistry templateRegistry;
    private final SettlementRegistry settlementRegistry;
    private final QuestPoolRegistry poolRegistry;
    private final AtomicLong questCounter = new AtomicLong(System.currentTimeMillis());

    public QuestGenerator(QuestTemplateRegistry templateRegistry, SettlementRegistry settlementRegistry,
                           QuestPoolRegistry poolRegistry) {
        this.templateRegistry = templateRegistry;
        this.settlementRegistry = settlementRegistry;
        this.poolRegistry = poolRegistry;
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

        // Resolve world bindings (gather items, enemy mobs, target NPC, POI)
        Map<String, String> bindings = resolveWorldBindings(npcX, npcZ, npcSettlementCellKey, npcId, template.situation(), random);
        bindings.put("subject_poi_type", subjectPoiType != null ? subjectPoiType : "unknown");
        bindings.put("subject_name", subjectValue != null ? subjectValue : npcId);

        // Store all template text in bindings for dialogue node construction
        bindings.put("quest_topic_header", template.topicHeader());
        bindings.put("quest_exposition_text", template.expositionText());
        bindings.put("quest_accept_text", template.acceptText());
        bindings.put("quest_decline_text", template.declineText());
        bindings.put("quest_skillcheck_pass_text", template.skillcheckPassText() != null ? template.skillcheckPassText() : "");
        bindings.put("quest_skillcheck_fail_text", template.skillcheckFailText() != null ? template.skillcheckFailText() : "");
        bindings.put("quest_exposition_turnin_text", template.expositionTurnInText());
        bindings.put("quest_conflict1_text", template.conflict1Text() != null ? template.conflict1Text() : "");
        bindings.put("quest_conflict1_turnin_text", template.conflict1TurnInText() != null ? template.conflict1TurnInText() : "");
        bindings.put("quest_conflict2_text", template.conflict2Text() != null ? template.conflict2Text() : "");
        bindings.put("quest_conflict2_turnin_text", template.conflict2TurnInText() != null ? template.conflict2TurnInText() : "");
        bindings.put("quest_resolution_text", template.resolutionText());

        // Pick a skillcheck type from the template's pool
        if (template.skillcheckTypes() != null && !template.skillcheckTypes().isEmpty()) {
            bindings.put("quest_skillcheck_type",
                template.skillcheckTypes().get(random.nextInt(template.skillcheckTypes().size())));
        }

        // Roll conflict count upfront: the quest is fully deterministic from creation
        int maxConflicts = 0;
        if (random.nextDouble() < CONFLICT_1_CHANCE) {
            maxConflicts = 1;
            if (random.nextDouble() < CONFLICT_2_CHANCE) {
                maxConflicts = 2;
            }
        }

        // Build objectives: 1 (exposition) + maxConflicts
        int totalObjectives = 1 + maxConflicts;
        List<ObjectiveInstance> objectives = new ArrayList<>();
        List<ObjectiveConfig> objConfigs = template.objectives();
        if (objConfigs == null || objConfigs.isEmpty()) {
            LOGGER.atWarning().log("V2 template %s has no objective configs", template.situation());
            return null;
        }

        for (int i = 0; i < totalObjectives; i++) {
            ObjectiveConfig config = i < objConfigs.size() ? objConfigs.get(i) : objConfigs.getLast();
            ObjectiveType type = config.type() != null ? config.type() : ObjectiveType.COLLECT_RESOURCES;
            ObjectiveInstance obj = createObjective(type, config, bindings, random);
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

        LOGGER.atInfo().log("Generated v2 quest %s: situation=%s, objectives=%d, for NPC %s",
            questId, template.situation(), objectives.size(), npcId);
        return quest;
    }

    private Map<String, String> resolveWorldBindings(double npcX, double npcZ, String npcCellKey,
                                                      String npcId, String situationId, Random random) {
        Map<String, String> bindings = new HashMap<>();
        bindings.put("quest_giver_name", npcId);
        bindings.put("player_name", "Traveler"); // Placeholder; resolved to actual name at display time if available
        bindings.put("npc_x", String.valueOf(npcX));
        bindings.put("npc_z", String.valueOf(npcZ));
        bindings.put("npc_settlement_key", npcCellKey);

        // v2: always use real gatherable items for collect resources
        QuestPoolRegistry.ItemEntry gatherItem = poolRegistry.randomCollectResource(random);
        bindings.put("quest_item", gatherItem.label());
        bindings.put("gather_item_id", gatherItem.id());
        if (gatherItem.category() != null) {
            bindings.put("gather_category", gatherItem.category());
        }
        if (gatherItem.countMin() > 0 && gatherItem.countMax() > 0) {
            bindings.put("gather_count_min", String.valueOf(gatherItem.countMin()));
            bindings.put("gather_count_max", String.valueOf(gatherItem.countMax()));
        }

        QuestPoolRegistry.ItemEntry enemyMob = poolRegistry.randomHostileMob(random);
        bindings.put("enemy_type", enemyMob.label());
        bindings.put("enemy_type_plural", enemyMob.labelPlural());
        bindings.put("enemy_type_id", enemyMob.id());

        SettlementRecord nearestOther = findNearestOtherSettlement(npcX, npcZ, npcCellKey);
        if (nearestOther != null) {
            bindings.put("location", nearestOther.getCellKey());
            bindings.put("location_hint", DirectionUtil.computeHint(npcX, npcZ,
                nearestOther.getPosX(), nearestOther.getPosZ()));

            if (!nearestOther.getNpcs().isEmpty()) {
                NpcRecord targetNpc = nearestOther.getNpcs().get(random.nextInt(nearestOther.getNpcs().size()));
                bindings.put("target_npc", targetNpc.getGeneratedName());
                bindings.put("target_npc_role", targetNpc.getRole());
                bindings.put("target_npc_settlement", nearestOther.getCellKey());
            }
        } else {
            bindings.put("location_hint", "far away");
        }
        if (!bindings.containsKey("target_npc")) {
            bindings.put("target_npc", "someone who might know more");
        }

        // POI voids are now claimed per-objective in createPOIObjective
        return bindings;
    }

    private void resolveNarrativeBindings(Map<String, String> bindings, QuestVariant expositionVariant,
                                           QuestSituation situation, String npcCellKey,
                                           String npcId, Random random) {
        // Pull narrative variables from pools (article-free, with plural flags)
        bindings.put("quest_action", poolRegistry.randomAction(random));

        QuestPoolRegistry.NarrativeEntry focus = poolRegistry.randomFocus(random);
        bindings.put("quest_focus", focus.value());
        bindings.put("quest_focus_is", focus.plural() ? "are" : "is");
        bindings.put("quest_focus_has", focus.plural() ? "have" : "has");
        bindings.put("quest_focus_was", focus.plural() ? "were" : "was");

        QuestPoolRegistry.NarrativeEntry threat = poolRegistry.randomThreat(random);
        bindings.put("quest_threat", threat.value());
        bindings.put("quest_threat_is", threat.plural() ? "are" : "is");
        bindings.put("quest_threat_has", threat.plural() ? "have" : "has");
        bindings.put("quest_threat_was", threat.plural() ? "were" : "was");

        // 25% chance: quest_stakes = quest_focus
        QuestPoolRegistry.NarrativeEntry stakes;
        if (random.nextDouble() < STAKES_EQUALS_FOCUS_CHANCE) {
            stakes = focus;
        } else {
            stakes = poolRegistry.randomStakes(random);
        }
        bindings.put("quest_stakes", stakes.value());
        bindings.put("quest_stakes_is", stakes.plural() ? "are" : "is");
        bindings.put("quest_stakes_has", stakes.plural() ? "have" : "has");
        bindings.put("quest_stakes_was", stakes.plural() ? "were" : "was");

        // Animate threat: for templates using agent verbs ("is regrouping", "retaliated")
        QuestPoolRegistry.NarrativeEntry animateThreat = poolRegistry.randomAnimateThreat(random);
        bindings.put("quest_threat_animate", animateThreat.value());
        bindings.put("quest_threat_animate_is", animateThreat.plural() ? "are" : "is");
        bindings.put("quest_threat_animate_has", animateThreat.plural() ? "have" : "has");
        bindings.put("quest_threat_animate_the", animateThreat.proper() ? animateThreat.value() : "the " + animateThreat.value());
        bindings.put("quest_threat_animate_The", animateThreat.proper() ? animateThreat.value() : "The " + animateThreat.value());
        bindings.put("quest_threat_animate_was", animateThreat.plural() ? "were" : "was");

        // Human stakes: for templates using human verbs ("can rest", "will sleep soundly")
        QuestPoolRegistry.NarrativeEntry humanStakes = poolRegistry.randomHumanStakes(random);
        bindings.put("quest_stakes_human", humanStakes.value());
        bindings.put("quest_stakes_human_is", humanStakes.plural() ? "are" : "is");
        bindings.put("quest_stakes_human_has", humanStakes.plural() ? "have" : "has");
        bindings.put("quest_stakes_human_was", humanStakes.plural() ? "were" : "was");
        bindings.put("quest_stakes_human_the", humanStakes.proper() ? humanStakes.value() : "the " + humanStakes.value());
        bindings.put("quest_stakes_human_The", humanStakes.proper() ? humanStakes.value() : "The " + humanStakes.value());

        // Article-prefixed variants: "the old watchtower" vs bare proper nouns
        bindings.put("quest_focus_the", focus.proper() ? focus.value() : "the " + focus.value());
        bindings.put("quest_focus_The", focus.proper() ? focus.value() : "The " + focus.value());
        bindings.put("quest_threat_the", threat.proper() ? threat.value() : "the " + threat.value());
        bindings.put("quest_threat_The", threat.proper() ? threat.value() : "The " + threat.value());
        bindings.put("quest_stakes_the", stakes.proper() ? stakes.value() : "the " + stakes.value());
        bindings.put("quest_stakes_The", stakes.proper() ? stakes.value() : "The " + stakes.value());

        // Optional narrative pools (probability-gated, with fallbacks for template safety)
        String origin = poolRegistry.randomOrigin(random);
        if (origin != null && random.nextDouble() < 0.7) {
            bindings.put("quest_origin", origin);
        } else {
            bindings.put("quest_origin", "what happened before");
        }

        String timePressure = poolRegistry.randomTimePressure(random);
        if (timePressure != null && random.nextDouble() < 0.5) {
            bindings.put("quest_time_pressure", timePressure);
        } else {
            bindings.put("quest_time_pressure", "time runs out");
        }

        String rewardHint = poolRegistry.randomRewardHint(random);
        if (rewardHint != null && random.nextDouble() < 0.4) {
            bindings.put("quest_reward_hint", rewardHint);
        } else {
            bindings.put("quest_reward_hint", "something worth your while");
        }

        // Override with any exposition-defined bindings (template author can still force specific values)
        if (expositionVariant.bindings() != null) {
            bindings.putAll(expositionVariant.bindings());
        }

        // Resolve quest_ally from same settlement
        SettlementRecord settlement = settlementRegistry.getByCell(npcCellKey);
        if (settlement != null && settlement.getNpcs().size() > 1) {
            List<NpcRecord> candidates = new ArrayList<>();
            for (NpcRecord npc : settlement.getNpcs()) {
                if (!npc.getGeneratedName().equals(npcId)) {
                    candidates.add(npc);
                }
            }
            if (!candidates.isEmpty()) {
                NpcRecord ally = candidates.get(random.nextInt(candidates.size()));
                bindings.put("quest_ally", ally.getGeneratedName());
                bindings.put("quest_ally_role", ally.getRole());
            }
        }
        if (!bindings.containsKey("quest_ally")) {
            bindings.put("quest_ally", "a trusted friend");
        }

        bindings.put("quest_location_name", bindings.getOrDefault("quest_focus", "the area"));

        // Resolve player responses and NPC counter-responses
        String tone = poolRegistry.getToneForSituation(situation.getId());
        bindings.put("response_accept", poolRegistry.randomAcceptResponse(situation.getId(), tone, random));
        bindings.put("response_decline", poolRegistry.randomDeclineResponse(situation.getId(), tone, random));
        bindings.put("counter_accept", poolRegistry.randomCounterAccept(situation.getId(), tone, random));
        bindings.put("counter_decline", poolRegistry.randomCounterDecline(situation.getId(), tone, random));

        // Target NPC dialogue for TALK_TO_NPC objectives
        bindings.put("send_to_npc_dialogue", poolRegistry.randomSendToNpcDialogue(situation.getId(), tone, random));
        boolean talkHandoff = random.nextDouble() < TALK_NPC_HANDOFF_CHANCE;
        bindings.put("talk_npc_is_handoff", talkHandoff ? "true" : "false");
        if (talkHandoff) {
            bindings.put("target_npc_dialogue", poolRegistry.randomTargetNpcHandoff(situation.getId(), tone, random));
        } else {
            bindings.put("target_npc_dialogue", poolRegistry.randomTargetNpcInfo(situation.getId(), tone, random));
        }

        // Stat-gated response option
        String statType = poolRegistry.randomStatType(random);
        bindings.put("stat_check_type", statType);
        bindings.put("response_stat_check", poolRegistry.randomStatCheckResponse(statType, random));
        bindings.put("counter_stat_pass", poolRegistry.randomCounterStatPass(tone, random));
        bindings.put("counter_stat_fail", poolRegistry.randomCounterStatFail(tone, random));
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
            case FETCH_ITEM -> "hostile".equals(bindings.get("fetch_variant"))
                ? "retrieve " + obj.getTargetLabel() + " from " + bindings.getOrDefault("subject_name", "the area")
                : "recover " + obj.getTargetLabel();
            case TALK_TO_NPC -> "speak with " + obj.getTargetLabel();
        };
        bindings.put("quest_objective_summary", summary);
    }

    private @Nullable ObjectiveInstance createObjective(ObjectiveType type, ObjectiveConfig config,
                                                         Map<String, String> bindings, Random random) {
        String npcId = bindings.getOrDefault("quest_giver_name", "unknown");

        return switch (type) {
            case COLLECT_RESOURCES -> {
                // Use per-item count range from pool if available, otherwise fall back to variant config
                int count;
                String minStr = bindings.get("gather_count_min");
                String maxStr = bindings.get("gather_count_max");
                if (minStr != null && maxStr != null) {
                    int min = Integer.parseInt(minStr);
                    int max = Integer.parseInt(maxStr);
                    count = min + random.nextInt(max - min + 1);
                } else {
                    count = config.rollCount(random);
                }
                bindings.put("gather_count", String.valueOf(count));
                ObjectiveInstance collectObj = new ObjectiveInstance(
                    type, bindings.get("gather_item_id"), bindings.get("quest_item"),
                    count, null, null
                );
                collectObj.setTargetLabelPlural(bindings.get("quest_item"));
                yield collectObj;
            }
            case KILL_MOBS -> {
                // Try to pre-claim a cave void; if unavailable, create objective without POI
                // and let resolveAndPlacePoi handle it at runtime (void discovery or surface fallback)
                ObjectiveInstance killObj = createPOIObjective(type, bindings, config, random);
                if (killObj == null) {
                    LOGGER.atInfo().log("KILL_MOBS for %s: no void at generation, deferring POI to runtime", npcId);
                    int killCount = 2;
                    bindings.put("gather_count", String.valueOf(killCount));
                    killObj = new ObjectiveInstance(type, "deferred_poi", bindings.get("enemy_type"),
                        killCount, null, null);
                    killObj.setTargetLabelPlural(bindings.get("enemy_type_plural"));
                }
                yield killObj;
            }
            case FETCH_ITEM -> {
                // Store quest item base type for chest spawning
                bindings.put("fetch_item_type", QuestPoolRegistry.getBaseItemType(bindings.get("gather_item_id")));
                bindings.put("fetch_item_label", bindings.getOrDefault("quest_item", "a quest item"));

                // Try hostile fetch (cave void) first
                bindings.put("fetch_variant", "hostile");
                ObjectiveInstance fetchObj = createPOIObjective(type, bindings, config, random);
                if (fetchObj != null) yield fetchObj;

                // Peaceful fetch: target a nearby settlement
                bindings.put("fetch_variant", "peaceful");
                String targetSettlement = bindings.get("location");
                if (targetSettlement != null) {
                    SettlementRecord target = settlementRegistry.getByCell(targetSettlement);
                    if (target != null) {
                        bindings.put("poi_type", "settlement");
                    }
                }

                yield new ObjectiveInstance(
                    type, bindings.get("gather_item_id"), bindings.get("quest_item"),
                    1, bindings.get("location_hint"), bindings.get("location")
                );
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
                    bindings.get("location_hint"),
                    bindings.get("target_npc_settlement")
                );
            }
        };
    }

    private ObjectiveInstance createPOIObjective(ObjectiveType type, Map<String, String> bindings,
                                                  ObjectiveConfig config, Random random) {
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

        // Build population spec for this objective
        String populationSpec = "KILL_MOBS:" + bindings.get("enemy_type_id") + ":" + switch (type) {
            case KILL_MOBS -> "4";
            default -> "3"; // FETCH_ITEM
        };

        int requiredCount = switch (type) {
            case KILL_MOBS -> 2;
            default -> 1; // FETCH_ITEM
        };
        bindings.put("gather_count", String.valueOf(requiredCount));

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

        LOGGER.atInfo().log("createPOIObjective: claimed void at (%d,%d,%d) for %s objective",
            cx, cy, cz, type);
        return poiObj;
    }
}
