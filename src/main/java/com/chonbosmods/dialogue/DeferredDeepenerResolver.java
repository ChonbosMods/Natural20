package com.chonbosmods.dialogue;

import com.chonbosmods.topic.TopicPoolRegistry;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves deferred deepener variable tokens at display time so each deepened
 * branch gets a unique NPC response drawn from the pool.
 *
 * <p>Tokens are stored as {@code {name:bracket}} where the bracket is the
 * reaction bracket for bracket-filtered pools (e.g. {@code {local_opinion:intense}}).
 * The colon format is invisible to {@link com.chonbosmods.quest.DialogueResolver}
 * since its pattern matches only {@code \w+}.
 *
 * <p>Uses a non-deterministic {@link Random} so the same NPC can produce
 * different deepener responses across conversation sessions.
 */
public final class DeferredDeepenerResolver {

    /** Pattern matching deferred deepener tokens: {name:bracket} */
    private static final Pattern DEFERRED_TOKEN = Pattern.compile(
            "\\{(local_opinion|personal_reaction|danger_assessment):([^}]*)\\}");

    private final TopicPoolRegistry topicPool;
    private final Random random;

    public DeferredDeepenerResolver(TopicPoolRegistry topicPool) {
        this.topicPool = topicPool;
        this.random = new Random(); // non-deterministic: fresh draws each session
    }

    /**
     * If {@code text} contains any deferred deepener tokens ({@code {name:bracket}}),
     * resolve them with fresh pool draws and return the resolved text. Otherwise
     * return the original text unchanged.
     *
     * @param text the dialogue text, possibly containing deferred tokens
     * @return resolved text with all deepener tokens replaced
     */
    public String resolve(String text) {
        if (text == null || !text.contains("{")) return text;

        Matcher matcher = DEFERRED_TOKEN.matcher(text);
        if (!matcher.find()) return text;

        StringBuilder sb = new StringBuilder();
        matcher.reset();
        while (matcher.find()) {
            String name = matcher.group(1);
            String bracket = matcher.group(2);
            if (bracket.isEmpty()) bracket = "mild";

            String replacement = switch (name) {
                case "local_opinion" -> topicPool.randomLocalOpinion(bracket, random);
                case "personal_reaction" -> topicPool.randomPersonalReaction(bracket, random);
                case "danger_assessment" -> topicPool.randomDangerAssessment(random);
                default -> matcher.group(0); // shouldn't happen, preserve token
            };
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Returns true if the text contains any deferred deepener tokens.
     */
    public static boolean hasDeferredTokens(String text) {
        if (text == null || !text.contains("{")) return false;
        return DEFERRED_TOKEN.matcher(text).find();
    }
}
