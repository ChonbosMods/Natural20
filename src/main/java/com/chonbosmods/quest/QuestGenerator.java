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

    private final QuestTemplateRegistry templateRegistry;
    private final SettlementRegistry settlementRegistry;
    private final AtomicLong questCounter = new AtomicLong(System.currentTimeMillis());

    private static final String[] GATHER_ITEMS = {
        "Hytale:RawMeat", "Hytale:Leather", "Hytale:Bone",
        "Hytale:Feather", "Hytale:WoodLog", "Hytale:Stone",
        "Hytale:IronOre", "Hytale:CottonFiber", "Hytale:Wheat"
    };

    private static final String[] HOSTILE_MOBS = {
        "Hytale:Trork_Grunt", "Hytale:Trork_Brute", "Hytale:Skeleton",
        "Hytale:Zombie", "Hytale:Spider"
    };

    public QuestGenerator(QuestTemplateRegistry templateRegistry, SettlementRegistry settlementRegistry) {
        this.templateRegistry = templateRegistry;
        this.settlementRegistry = settlementRegistry;
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

        Map<String, String> bindings = resolveBindings(npcX, npcZ, npcSettlementCellKey, random);

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

    private List<PhaseType> rollPhaseSequence(Random random) {
        List<PhaseType> sequence = new ArrayList<>();
        sequence.add(PhaseType.EXPOSITION);

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

    private Map<String, String> resolveBindings(double npcX, double npcZ, String npcCellKey, Random random) {
        Map<String, String> bindings = new HashMap<>();

        String gatherItem = GATHER_ITEMS[random.nextInt(GATHER_ITEMS.length)];
        bindings.put("quest_item", gatherItem.substring(gatherItem.indexOf(':') + 1));

        String enemyType = HOSTILE_MOBS[random.nextInt(HOSTILE_MOBS.length)];
        bindings.put("enemy_type", enemyType.substring(enemyType.indexOf(':') + 1).replace("_", " "));
        bindings.put("enemy_type_id", enemyType);

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
            bindings.put("location", "a distant place");
            bindings.put("location_hint", "far away");
        }

        bindings.put("gather_item_id", gatherItem);

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
                type, bindings.get("location"), bindings.get("location"),
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
