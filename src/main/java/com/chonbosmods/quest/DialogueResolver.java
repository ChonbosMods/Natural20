package com.chonbosmods.quest;

import com.chonbosmods.ui.EntityHighlight;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DialogueResolver {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{(\\w+)}");
    private static final Pattern DOUBLE_ARTICLE = Pattern.compile("\\b(the|a|an) \\1\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DANGLING_PUNCT = Pattern.compile("\\s+([,.:;!?])");
    private static final Pattern DOUBLE_SPACE = Pattern.compile("  +");
    private static final Pattern EMPTY_PARENS = Pattern.compile("\\(\\s*\\)");
    // Article correction is marker-aware: an EntityHighlight MARK_START char between
    // the article and the vowel still triggers a/an conversion.
    private static final String MARK_OPT = "[\u0001]?";
    private static final Pattern A_VOWEL = Pattern.compile("\\ba (" + MARK_OPT + "[aeiouAEIOU])");
    private static final Pattern A_VOWEL_CAP = Pattern.compile("\\bA (" + MARK_OPT + "[aeiouAEIOU])");

    /**
     * Quest variable keys whose substituted values should be wrapped in
     * EntityHighlight markers when rendered in quest dialogue. The renderer
     * (EntityHighlight.toMessage) converts the marked spans to the entity
     * highlight color so quest items, target names, settlement names, and
     * numeric counts pop visually the same way settlement names and other
     * NPC names already do in the smalltalk pipeline.
     *
     * <p>Roles ({@code self_role}, {@code settlement_npc_role}, {@code target_npc_role})
     * and the {@code settlement_type} label are common nouns, NOT entity names,
     * so they are NOT highlighted: highlighting them would be visual noise.
     */
    private static final Set<String> HIGHLIGHTED_QUEST_VARS = Set.of(
        // Items / counts (per-objective)
        "quest_item",
        "gather_count",
        "kill_count",
        // Combat target
        "enemy_type",
        "enemy_type_plural",
        // NPCs
        "target_npc",
        "settlement_npc",
        // Settlements
        "settlement_name",
        "other_settlement",
        "target_npc_settlement",
        // Reward flavor
        "quest_reward"
    );

    /**
     * Replace all {variable} tokens in template with values from bindings.
     * Handles:
     * - Variable substitution
     * - Verb conjugation: {quest_stakes_is} resolves to "is" or "are" based on plurality
     * - Double article collapse: "the the" -> "the"
     * - Dangling punctuation cleanup
     * - Double space cleanup
     */
    public static String resolve(String template, Map<String, String> bindings) {
        if (template == null || template.isEmpty()) return template;
        return substituteAndClean(template, bindings, false);
    }

    /**
     * Resolve quest dialogue text with optional per-objective overlay.
     *
     * <p>If {@code objective} is non-null, its current values (count, item label,
     * enemy label, target NPC) are layered on top of the global bindings before
     * substitution. This guarantees each phase of a quest renders the count and
     * subject of its OWN objective, even though the global binding map only holds
     * the last-written values from quest generation.
     *
     * <p>Recognized entity-like variables ({@code quest_item}, {@code enemy_type},
     * {@code target_npc}, {@code gather_count}, {@code kill_count}, etc.) are wrapped
     * in {@link EntityHighlight} markers so the dialogue renderer paints them in
     * the entity highlight color.
     */
    public static String resolveQuestText(String template, Map<String, String> bindings,
                                          @Nullable ObjectiveInstance objective) {
        if (template == null || template.isEmpty()) return template;

        Map<String, String> effective = bindings;
        if (objective != null) {
            effective = new HashMap<>(bindings);
            overlayObjective(effective, objective);
        }
        return substituteAndClean(template, effective, true);
    }

    /**
     * Layer per-objective values onto a binding map. Each objective type sets
     * the variables that ITS phase of the quest text expects to read.
     */
    private static void overlayObjective(Map<String, String> bindings, ObjectiveInstance objective) {
        switch (objective.getType()) {
            case COLLECT_RESOURCES -> {
                bindings.put("gather_count", String.valueOf(objective.getRequiredCount()));
                if (objective.getEffectiveLabel() != null) {
                    bindings.put("quest_item", objective.getEffectiveLabel());
                }
            }
            case KILL_MOBS -> {
                bindings.put("kill_count", String.valueOf(objective.getRequiredCount()));
                if (objective.getTargetLabel() != null) {
                    bindings.put("enemy_type", objective.getTargetLabel());
                }
                if (objective.getTargetLabelPlural() != null) {
                    bindings.put("enemy_type_plural", objective.getTargetLabelPlural());
                }
            }
            case FETCH_ITEM -> {
                if (objective.getEffectiveLabel() != null) {
                    bindings.put("quest_item", objective.getEffectiveLabel());
                }
            }
            case TALK_TO_NPC -> {
                if (objective.getTargetLabel() != null) {
                    bindings.put("target_npc", objective.getTargetLabel());
                }
            }
        }
    }

    private static String substituteAndClean(String template, Map<String, String> bindings, boolean wrapEntities) {
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = bindings.get(key);
            if (value != null) {
                if (wrapEntities && HIGHLIGHTED_QUEST_VARS.contains(key) && !value.isEmpty()) {
                    value = EntityHighlight.wrap(value);
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            } else {
                // Preserve unresolved tokens: late binding resolves them at display time
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);

        String result = sb.toString();
        // Collapse double articles: "the the old watchtower" -> "the old watchtower"
        result = DOUBLE_ARTICLE.matcher(result).replaceAll("$1");
        // a/an correction: "a apple" -> "an apple", "A owl" -> "An owl"
        // Marker-aware so a wrapped entity name starting with a vowel still triggers conversion.
        result = A_VOWEL.matcher(result).replaceAll("an $1");
        result = A_VOWEL_CAP.matcher(result).replaceAll("An $1");
        // Clean dangling punctuation: "from ," -> "from,"
        result = DANGLING_PUNCT.matcher(result).replaceAll("$1");
        // Clean empty parentheses
        result = EMPTY_PARENS.matcher(result).replaceAll("");
        // Clean double spaces
        result = DOUBLE_SPACE.matcher(result).replaceAll(" ");
        return result.trim();
    }

}
