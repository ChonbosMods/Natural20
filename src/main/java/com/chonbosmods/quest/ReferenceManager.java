package com.chonbosmods.quest;

import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.quest.model.QuestReferenceTemplate;
import com.chonbosmods.settlement.NpcRecord;
import com.chonbosmods.settlement.SettlementRecord;
import com.chonbosmods.settlement.SettlementRegistry;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class ReferenceManager {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final int MAX_ACTIVE_REFERENCES = 3;
    private static final double PASSIVE_TO_TRIGGER_CHANCE = 0.30;
    private static final double TRIGGER_TO_CATALYST_CHANCE = 0.40;

    private final QuestTemplateRegistry templateRegistry;
    private final SettlementRegistry settlementRegistry;
    private final QuestStateManager stateManager;
    private final AtomicLong refCounter = new AtomicLong(System.currentTimeMillis());

    public ReferenceManager(QuestTemplateRegistry templateRegistry,
                            SettlementRegistry settlementRegistry,
                            QuestStateManager stateManager) {
        this.templateRegistry = templateRegistry;
        this.settlementRegistry = settlementRegistry;
        this.stateManager = stateManager;
    }

    public @Nullable String injectReference(Nat20PlayerData playerData, String situationId,
                                             String referenceId, double npcX, double npcZ) {
        Map<String, ReferenceState> refs = stateManager.getActiveReferences(playerData);
        if (refs.size() >= MAX_ACTIVE_REFERENCES) return null;

        List<QuestReferenceTemplate> templates = templateRegistry.findCompatibleReferences(situationId);
        if (templates.isEmpty()) return null;

        Random random = new Random();
        QuestReferenceTemplate template = templates.get(random.nextInt(templates.size()));

        SettlementRecord targetSettlement = findTargetSettlement(npcX, npcZ, template.targetNpcRoles(), random);
        if (targetSettlement == null) return null;

        NpcRecord targetNpc = findTargetNpc(targetSettlement, template.targetNpcRoles(), random);
        if (targetNpc == null) return null;

        // Roll starting tier: 60% Passive, 30% Trigger, 10% Catalyst
        ReferenceTier tier;
        double tierRoll = random.nextDouble();
        if (tierRoll < 0.60) tier = ReferenceTier.PASSIVE;
        else if (tierRoll < 0.90) tier = ReferenceTier.TRIGGER;
        else tier = ReferenceTier.CATALYST;

        ReferenceState ref = new ReferenceState(
            referenceId, template.id(), tier,
            targetNpc.getGeneratedName(),
            targetSettlement.getCellKey(),
            template.catalystSituations()
        );

        if (tier == ReferenceTier.TRIGGER || tier == ReferenceTier.CATALYST) {
            String topicId = "ref_topic_" + refCounter.incrementAndGet();
            ref.setUnlockedTopicId(topicId);
        }

        refs.put(referenceId, ref);
        stateManager.saveActiveReferences(playerData, refs);

        LOGGER.atInfo().log("Injected reference %s: tier=%s, template=%s, target=%s at %s",
            referenceId, tier, template.id(), targetNpc.getGeneratedName(), targetSettlement.getCellKey());
        return referenceId;
    }

    public void checkPassiveEscalation(Nat20PlayerData playerData, String settlementCellKey) {
        Map<String, ReferenceState> refs = stateManager.getActiveReferences(playerData);
        boolean changed = false;
        Random random = new Random();

        for (ReferenceState ref : refs.values()) {
            if (ref.getTier() != ReferenceTier.PASSIVE) continue;
            if (!ref.getBoundSettlementId().equals(settlementCellKey)) continue;

            if (random.nextDouble() < PASSIVE_TO_TRIGGER_CHANCE) {
                ref.setTier(ReferenceTier.TRIGGER);
                String topicId = "ref_topic_" + refCounter.incrementAndGet();
                ref.setUnlockedTopicId(topicId);
                changed = true;
                LOGGER.atInfo().log("Reference %s escalated: PASSIVE -> TRIGGER", ref.getReferenceId());
            }
        }

        if (changed) {
            stateManager.saveActiveReferences(playerData, refs);
        }
    }

    public boolean checkTriggerEscalation(Nat20PlayerData playerData, String referenceId) {
        Map<String, ReferenceState> refs = stateManager.getActiveReferences(playerData);
        ReferenceState ref = refs.get(referenceId);
        if (ref == null || ref.getTier() != ReferenceTier.TRIGGER) return false;

        Random random = new Random();
        if (random.nextDouble() < TRIGGER_TO_CATALYST_CHANCE) {
            ref.setTier(ReferenceTier.CATALYST);
            stateManager.saveActiveReferences(playerData, refs);
            LOGGER.atInfo().log("Reference %s escalated: TRIGGER -> CATALYST", referenceId);
            return true;
        }
        return false;
    }

    public void cleanupQuestReferences(Nat20PlayerData playerData, QuestInstance quest) {
        Map<String, ReferenceState> refs = stateManager.getActiveReferences(playerData);
        boolean changed = false;
        for (PhaseInstance phase : quest.getPhases()) {
            if (phase.getReferenceId() != null && refs.remove(phase.getReferenceId()) != null) {
                changed = true;
            }
        }
        if (changed) {
            stateManager.saveActiveReferences(playerData, refs);
        }
    }

    private @Nullable SettlementRecord findTargetSettlement(double x, double z,
                                                             List<String> targetRoles, Random random) {
        List<SettlementRecord> candidates = new ArrayList<>();
        for (SettlementRecord record : settlementRegistry.getAll().values()) {
            for (NpcRecord npc : record.getNpcs()) {
                if (targetRoles.isEmpty() || targetRoles.contains(npc.getRole())) {
                    candidates.add(record);
                    break;
                }
            }
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparingDouble(r -> {
            double dx = r.getPosX() - x;
            double dz = r.getPosZ() - z;
            return dx * dx + dz * dz;
        }));
        int poolSize = Math.min(3, candidates.size());
        return candidates.get(random.nextInt(poolSize));
    }

    private @Nullable NpcRecord findTargetNpc(SettlementRecord settlement, List<String> targetRoles, Random random) {
        List<NpcRecord> matches = new ArrayList<>();
        for (NpcRecord npc : settlement.getNpcs()) {
            if (targetRoles.isEmpty() || targetRoles.contains(npc.getRole())) {
                matches.add(npc);
            }
        }
        if (matches.isEmpty()) return null;
        return matches.get(random.nextInt(matches.size()));
    }
}
