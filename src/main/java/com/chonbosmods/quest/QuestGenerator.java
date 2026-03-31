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
    private static final int MAX_PHASES = 4;
    private static final double CONFLICT_EXTEND_CHANCE = 0.25;
    private static final double RESOLUTION_EXTEND_CHANCE = 0.10;
    private static final double REFERENCE_INJECT_CHANCE = 0.20;
    private static final double QUEST_ALLY_TOPIC_CHANCE = 0.35;
    private static final double STAKES_EQUALS_FOCUS_CHANCE = 0.15;
    private static final double SHORT_QUEST_CHANCE = 0.25;
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

        QuestSituation situation = templateRegistry.selectForRole(npcRole, random);
        if (situation == null) {
            LOGGER.atWarning().log("No quest situations available for role: %s", npcRole);
            return null;
        }

        List<PhaseType> phaseSequence = rollPhaseSequence(random);

        // Step 3: Pick variants per phase
        List<QuestVariant> selectedVariants = new ArrayList<>();
        for (PhaseType phase : phaseSequence) {
            List<QuestVariant> pool = switch (phase) {
                case EXPOSITION -> situation.getExpositionVariants();
                case CONFLICT -> situation.getConflictVariants();
                case RESOLUTION -> situation.getResolutionVariants();
            };
            if (pool.isEmpty()) {
                LOGGER.atWarning().log("No variants for phase %s in situation %s", phase, situation.getId());
                return null;
            }
            selectedVariants.add(pool.get(random.nextInt(pool.size())));
        }

        // Step 4a: Resolve world bindings
        Map<String, String> bindings = resolveWorldBindings(npcX, npcZ, npcSettlementCellKey, npcId, situation.getId(), random);
        bindings.put("subject_poi_type", subjectPoiType != null ? subjectPoiType : "unknown");
        bindings.put("subject_name", subjectValue != null ? subjectValue : npcId);

        // Step 4b: Resolve narrative bindings from exposition variant
        QuestVariant expositionVariant = selectedVariants.getFirst();
        resolveNarrativeBindings(bindings, expositionVariant, situation, npcSettlementCellKey, npcId, random);

        // Step 5: Build phase instances with objectives
        List<PhaseInstance> phases = new ArrayList<>();
        for (int i = 0; i < phaseSequence.size(); i++) {
            QuestVariant variant = selectedVariants.get(i);
            PhaseType phaseType = phaseSequence.get(i);

            // Select objective type from variant's pool, filtered by subject affinities
            List<ObjectiveType> variantPool = variant.objectivePool();
            List<ObjectiveType> validTypes;
            if (subjectAffinities != null && !subjectAffinities.isEmpty()) {
                Set<String> affinitySet = new HashSet<>(subjectAffinities);
                validTypes = variantPool.stream()
                    .filter(t -> affinitySet.contains(t.name()))
                    .toList();
                if (validTypes.isEmpty()) {
                    // Fallback: use any subject affinity that has a config
                    validTypes = subjectAffinities.stream()
                        .map(a -> { try { return ObjectiveType.valueOf(a); } catch (Exception e) { return null; } })
                        .filter(Objects::nonNull)
                        .toList();
                }
            } else {
                validTypes = variantPool;
            }
            if (validTypes.isEmpty()) validTypes = List.of(ObjectiveType.KILL_MOBS); // ultimate fallback

            ObjectiveType selectedType = validTypes.size() == 1
                ? validTypes.getFirst()
                : validTypes.get(random.nextInt(validTypes.size()));

            ObjectiveConfig config = variant.objectiveConfig().getOrDefault(
                selectedType, new ObjectiveConfig(null, null, null));
            ObjectiveInstance obj = createObjective(selectedType, config, bindings, random);
            if (obj == null) {
                LOGGER.atInfo().log("Skipping quest: objective type %s not yet fully implemented for subject %s (poiType: %s)",
                    selectedType, npcId, bindings.getOrDefault("subject_poi_type", "unknown"));
                return null;
            }
            List<ObjectiveInstance> objectives = List.of(obj);

            String referenceId = null;
            if ((phaseType == PhaseType.CONFLICT || phaseType == PhaseType.RESOLUTION)
                    && random.nextDouble() < REFERENCE_INJECT_CHANCE) {
                referenceId = "ref_" + questCounter.incrementAndGet();
            }

            phases.add(new PhaseInstance(phaseType, variant.id(), objectives, referenceId));
        }

        // Build objective summary from exposition phase for dialogue templates
        buildObjectiveSummary(phases.getFirst(), bindings);

        String questId = "quest_" + situation.getId().toLowerCase() + "_" + Long.toHexString(questCounter.incrementAndGet());

        QuestInstance quest = new QuestInstance(
            questId, situation.getId(), npcId, npcSettlementCellKey, phases, bindings
        );

        LOGGER.atInfo().log("Generated quest %s: situation=%s, phases=%d, for NPC %s",
            questId, situation.getId(), phases.size(), npcId);
        return quest;
    }

    /**
     * Check if quest ally should get a topic unlocked (35% chance).
     * Called after quest completion. Returns the ally NPC name or null.
     */
    public @Nullable String rollAllyTopic(QuestInstance quest, Random random) {
        String ally = quest.getVariableBindings().get("quest_ally");
        if (ally != null && !ally.isEmpty() && random.nextDouble() < QUEST_ALLY_TOPIC_CHANCE) {
            return ally;
        }
        return null;
    }

    private List<PhaseType> rollPhaseSequence(Random random) {
        List<PhaseType> sequence = new ArrayList<>();
        sequence.add(PhaseType.EXPOSITION);

        // 10% chance: short quest (exposition -> resolution, no conflict)
        if (random.nextDouble() < SHORT_QUEST_CHANCE) {
            sequence.add(PhaseType.RESOLUTION);
            return sequence;
        }

        PhaseType current = PhaseType.EXPOSITION;
        while (sequence.size() < MAX_PHASES) {
            if (current == PhaseType.EXPOSITION || current == PhaseType.CONFLICT) {
                sequence.add(PhaseType.CONFLICT);
                current = PhaseType.CONFLICT;

                if (sequence.size() < MAX_PHASES && random.nextDouble() < CONFLICT_EXTEND_CHANCE) {
                    continue;
                }
                sequence.add(PhaseType.RESOLUTION);
                current = PhaseType.RESOLUTION;
            } else {
                if (random.nextDouble() < RESOLUTION_EXTEND_CHANCE) {
                    current = PhaseType.RESOLUTION;
                    continue;
                }
                break;
            }

            if (current == PhaseType.RESOLUTION) {
                break;
            }
        }

        if (sequence.getLast() != PhaseType.RESOLUTION) {
            if (sequence.size() >= MAX_PHASES) {
                sequence.set(sequence.size() - 1, PhaseType.RESOLUTION);
            } else {
                sequence.add(PhaseType.RESOLUTION);
            }
        }

        return sequence;
    }

    private Map<String, String> resolveWorldBindings(double npcX, double npcZ, String npcCellKey,
                                                      String npcId, String situationId, Random random) {
        Map<String, String> bindings = new HashMap<>();
        bindings.put("quest_giver_name", npcId);
        bindings.put("player_name", "Traveler"); // Placeholder; resolved to actual name at display time if available
        bindings.put("npc_x", String.valueOf(npcX));
        bindings.put("npc_z", String.valueOf(npcZ));

        // Item pool selection: investigation → evidence, loss of loved ones → keepsakes, default → resources
        QuestPoolRegistry.ItemEntry gatherItem;
        if (INVESTIGATION_SITUATIONS.contains(situationId)) {
            gatherItem = poolRegistry.randomEvidenceItem(random);
        } else if ("LossOfLovedOnes".equals(situationId)) {
            gatherItem = poolRegistry.randomKeepsakeItem(random);
        } else {
            gatherItem = poolRegistry.randomCollectResource(random);
        }
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

        // Resolve POI: find nearby cave void for hostile location quests
        CaveVoidRegistry voidRegistry = Natural20.getInstance().getCaveVoidRegistry();
        if (voidRegistry != null) {
            CaveVoidRecord poi = voidRegistry.findNearbyVoid(npcX, npcZ, 200, 600);
            if (poi != null) {
                bindings.put("poi_available", "true");
                bindings.put("poi_center_x", String.valueOf(poi.getCenterX()));
                bindings.put("poi_center_y", String.valueOf(poi.getCenterY()));
                bindings.put("poi_center_z", String.valueOf(poi.getCenterZ()));
            }
        }
        if (!bindings.containsKey("poi_available")) {
            bindings.put("poi_available", "false");
        }

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

    /**
     * Build a human-readable objective summary from the first phase's objectives.
     * Stored as {quest_objective_summary} for use in dialogue templates.
     * Examples: "kill 5 Trorks"
     *           "collect 10 Wood Logs"
     *           "speak with Elara"
     */
    private void buildObjectiveSummary(PhaseInstance phase, Map<String, String> bindings) {
        if (phase.getObjectives().isEmpty()) return;
        ObjectiveInstance obj = phase.getObjectives().getFirst();

        String summary = switch (obj.getType()) {
            case KILL_MOBS -> "kill " + obj.getRequiredCount() + " " + obj.getTargetLabel();
            case COLLECT_RESOURCES -> "collect " + obj.getRequiredCount() + " " + obj.getTargetLabel();
            case FETCH_ITEM -> "hostile".equals(bindings.get("fetch_variant"))
                ? "retrieve " + obj.getTargetLabel() + " from " + bindings.getOrDefault("subject_name", "the area")
                : "recover " + obj.getTargetLabel();
            case TALK_TO_NPC -> "speak with " + obj.getTargetLabel();
        };

        bindings.put("quest_objective_summary", summary);
    }

    private ObjectiveType pickObjectiveType(List<ObjectiveType> pool, @Nullable ObjectiveType lastType, Random random) {
        if (pool.size() == 1) return pool.getFirst();
        List<ObjectiveType> filtered = new ArrayList<>(pool);
        if (lastType != null) filtered.remove(lastType);
        if (filtered.isEmpty()) filtered = pool;
        return filtered.get(random.nextInt(filtered.size()));
    }

    private @Nullable ObjectiveInstance createObjective(ObjectiveType type, ObjectiveConfig config,
                                                         Map<String, String> bindings, Random random) {
        boolean poiAvailable = "true".equals(bindings.get("poi_available"));
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
                yield new ObjectiveInstance(
                    type, bindings.get("gather_item_id"), bindings.get("quest_item"),
                    count, null, null
                );
            }
            case KILL_MOBS -> {
                if (poiAvailable) {
                    yield createPOIObjective(type, bindings, config, random);
                }
                // KILL_MOBS requires a POI for spawning hostile mobs
                LOGGER.atInfo().log("Skipping KILL_MOBS objective for %s: no POI available (poiType: %s)",
                    npcId, bindings.getOrDefault("subject_poi_type", "unknown"));
                yield null;
            }
            case FETCH_ITEM -> {
                // Store quest item base type for chest spawning
                bindings.put("fetch_item_type", QuestPoolRegistry.getBaseItemType(bindings.get("gather_item_id")));
                bindings.put("fetch_item_label", bindings.getOrDefault("quest_item", "a quest item"));

                if (poiAvailable) {
                    bindings.put("fetch_variant", "hostile");
                    yield createPOIObjective(type, bindings, config, random);
                }

                // Peaceful fetch: target a nearby settlement
                bindings.put("fetch_variant", "peaceful");
                String targetSettlement = bindings.get("location");
                if (targetSettlement != null) {
                    SettlementRecord target = settlementRegistry.getByCell(targetSettlement);
                    if (target != null) {
                        bindings.put("poi_available", "true");
                        bindings.put("poi_center_x", String.valueOf(target.getPosX()));
                        bindings.put("poi_center_z", String.valueOf(target.getPosZ()));
                        bindings.put("marker_offset_x", "0");
                        bindings.put("marker_offset_z", "0");
                        bindings.put("poi_x", String.valueOf(target.getPosX()));
                        bindings.put("poi_y", String.valueOf(target.getPosY()));
                        bindings.put("poi_z", String.valueOf(target.getPosZ()));
                        bindings.put("poi_mob_state", "PENDING");
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
                if (targetNpc == null || "someone who might know more".equals(targetNpc)) {
                    LOGGER.atInfo().log("Skipping TALK_TO_NPC objective for %s: no valid target NPC found",
                        npcId);
                    yield null;
                }
                yield new ObjectiveInstance(
                    type, targetNpc, targetNpc,
                    1, bindings.get("location_hint"), bindings.get("target_npc_settlement")
                );
            }
        };
    }

    private ObjectiveInstance createPOIObjective(ObjectiveType type, Map<String, String> bindings,
                                                  ObjectiveConfig config, Random random) {
        String poiX = bindings.get("poi_center_x");
        String poiY = bindings.get("poi_center_y");
        String poiZ = bindings.get("poi_center_z");
        String poiLocationId = "poi:" + poiX + "," + poiY + "," + poiZ;

        // Compute direction hint from NPC to POI
        double npcX = 0, npcZ = 0;
        try {
            npcX = Double.parseDouble(bindings.getOrDefault("npc_x", "0"));
            npcZ = Double.parseDouble(bindings.getOrDefault("npc_z", "0"));
        } catch (NumberFormatException ignored) {}
        String hint = DirectionUtil.computeHint(npcX, npcZ,
                Double.parseDouble(poiX), Double.parseDouble(poiZ));

        // Store POI metadata in quest bindings for population later
        bindings.put("poi_type", "hostile_location");
        bindings.put("poi_populated", "false");
        bindings.put("poi_x", poiX);
        bindings.put("poi_y", poiY);
        bindings.put("poi_z", poiZ);

        // All POI quests spawn hostile mobs in the dungeon
        String populationSpec = "KILL_MOBS:" + bindings.get("enemy_type_id") + ":" + switch (type) {
            case KILL_MOBS -> "4";
            default -> "3"; // FETCH_ITEM
        };
        bindings.put("poi_population_spec", populationSpec);

        int requiredCount = switch (type) {
            case KILL_MOBS -> 2;
            default -> 1; // FETCH_ITEM
        };

        String targetLabel = switch (type) {
            case KILL_MOBS -> bindings.get("enemy_type");
            case FETCH_ITEM -> bindings.get("quest_item");
            default -> "a cave dungeon";
        };

        return new ObjectiveInstance(type, poiLocationId, targetLabel,
                requiredCount, hint, poiLocationId);
    }
}
