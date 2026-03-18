package com.chonbosmods.quest;

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
    private static final int MAX_PHASES = 6;
    private static final double CONFLICT_EXTEND_CHANCE = 0.40;
    private static final double RESOLUTION_EXTEND_CHANCE = 0.25;
    private static final double REFERENCE_INJECT_CHANCE = 0.20;
    private static final double QUEST_ALLY_TOPIC_CHANCE = 0.35;
    private static final double STAKES_EQUALS_FOCUS_CHANCE = 0.25;
    private static final double SHORT_QUEST_CHANCE = 0.10;

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
                                             Set<String> completedIds) {
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
        Map<String, String> bindings = resolveWorldBindings(npcX, npcZ, npcSettlementCellKey, npcId, random);

        // Step 4b: Resolve narrative bindings from exposition variant
        QuestVariant expositionVariant = selectedVariants.getFirst();
        resolveNarrativeBindings(bindings, expositionVariant, situation, npcSettlementCellKey, npcId, random);

        // Step 5: Build phase instances with objectives
        List<PhaseInstance> phases = new ArrayList<>();
        ObjectiveType lastObjectiveType = null;
        for (int i = 0; i < phaseSequence.size(); i++) {
            QuestVariant variant = selectedVariants.get(i);
            PhaseType phaseType = phaseSequence.get(i);

            int objectiveCount = 1 + (random.nextDouble() < 0.4 ? 1 : 0);
            List<ObjectiveInstance> objectives = new ArrayList<>();
            for (int j = 0; j < objectiveCount && !variant.objectivePool().isEmpty(); j++) {
                ObjectiveType objType = pickObjectiveType(variant.objectivePool(), lastObjectiveType, random);
                ObjectiveConfig config = variant.objectiveConfig().getOrDefault(objType, new ObjectiveConfig(null, null, null));
                ObjectiveInstance obj = createObjective(objType, config, bindings, random);
                objectives.add(obj);
                lastObjectiveType = objType;
            }

            String referenceId = null;
            if ((phaseType == PhaseType.CONFLICT || phaseType == PhaseType.RESOLUTION)
                    && random.nextDouble() < REFERENCE_INJECT_CHANCE) {
                referenceId = "ref_" + questCounter.incrementAndGet();
            }

            phases.add(new PhaseInstance(phaseType, variant.id(), objectives, referenceId));
        }

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
                                                      String npcId, Random random) {
        Map<String, String> bindings = new HashMap<>();

        QuestPoolRegistry.ItemEntry gatherItem = poolRegistry.randomGatherItem(random);
        bindings.put("quest_item", gatherItem.label());
        bindings.put("gather_item_id", gatherItem.id());

        QuestPoolRegistry.ItemEntry enemyMob = poolRegistry.randomHostileMob(random);
        bindings.put("enemy_type", enemyMob.label());
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

        // Optional narrative pools (70% chance each to be included)
        String origin = poolRegistry.randomOrigin(random);
        if (origin != null && random.nextDouble() < 0.7) {
            bindings.put("quest_origin", origin);
        }

        String timePressure = poolRegistry.randomTimePressure(random);
        if (timePressure != null && random.nextDouble() < 0.5) {
            bindings.put("quest_time_pressure", timePressure);
        }

        String rewardHint = poolRegistry.randomRewardHint(random);
        if (rewardHint != null && random.nextDouble() < 0.4) {
            bindings.put("quest_reward_hint", rewardHint);
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

        bindings.put("quest_location_name", bindings.getOrDefault("quest_focus", "the area"));

        // Resolve tone-based player responses
        String tone = poolRegistry.getToneForSituation(situation.getId());
        bindings.put("response_accept", poolRegistry.randomAcceptResponse(tone, random));
        bindings.put("response_decline", poolRegistry.randomDeclineResponse(tone, random));
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

    private ObjectiveType pickObjectiveType(List<ObjectiveType> pool, @Nullable ObjectiveType lastType, Random random) {
        if (pool.size() == 1) return pool.getFirst();
        List<ObjectiveType> filtered = new ArrayList<>(pool);
        if (lastType != null) filtered.remove(lastType);
        if (filtered.isEmpty()) filtered = pool;
        return filtered.get(random.nextInt(filtered.size()));
    }

    private ObjectiveInstance createObjective(ObjectiveType type, ObjectiveConfig config,
                                              Map<String, String> bindings, Random random) {
        return switch (type) {
            case GATHER_ITEMS -> new ObjectiveInstance(
                type, bindings.get("gather_item_id"), bindings.get("quest_item"),
                config.rollCount(random), null, null
            );
            case KILL_MOBS -> new ObjectiveInstance(
                type, bindings.get("enemy_type_id"), bindings.get("enemy_type"),
                config.rollCount(random), null, null
            );
            case DELIVER_ITEM -> new ObjectiveInstance(
                type, bindings.get("gather_item_id"), bindings.get("quest_item"),
                1, bindings.get("location_hint"), bindings.get("target_npc_settlement")
            );
            case EXPLORE_LOCATION -> new ObjectiveInstance(
                type, bindings.get("location"), bindings.getOrDefault("quest_location_name", "the area"),
                1, bindings.get("location_hint"), bindings.get("location")
            );
            case FETCH_ITEM -> new ObjectiveInstance(
                type, bindings.get("gather_item_id"), bindings.get("quest_item"),
                1, bindings.get("location_hint"), bindings.get("location")
            );
            case TALK_TO_NPC -> new ObjectiveInstance(
                type, bindings.getOrDefault("target_npc", "an NPC"),
                bindings.getOrDefault("target_npc", "an NPC"),
                1, bindings.get("location_hint"), bindings.get("target_npc_settlement")
            );
            case KILL_NPC -> new ObjectiveInstance(
                type, "bandit_" + Long.toHexString(random.nextLong()),
                "a dangerous outlaw",
                1, bindings.get("location_hint"), bindings.get("location")
            );
        };
    }
}
