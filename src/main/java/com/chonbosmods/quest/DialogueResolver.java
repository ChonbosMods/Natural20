package com.chonbosmods.quest;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DialogueResolver {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{(\\w+)}");
    private static final Pattern DOUBLE_ARTICLE = Pattern.compile("\\b(the|a|an) \\1\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DANGLING_PUNCT = Pattern.compile("\\s+([,.:;!?])");
    private static final Pattern DOUBLE_SPACE = Pattern.compile("  +");
    private static final Pattern EMPTY_PARENS = Pattern.compile("\\(\\s*\\)");

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

        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = bindings.get(key);
            if (value != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            } else {
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);

        String result = sb.toString();
        // Collapse double articles: "the the old watchtower" -> "the old watchtower"
        result = DOUBLE_ARTICLE.matcher(result).replaceAll("$1");
        // Clean dangling punctuation: "from ," -> "from,"
        result = DANGLING_PUNCT.matcher(result).replaceAll("$1");
        // Clean empty parentheses
        result = EMPTY_PARENS.matcher(result).replaceAll("");
        // Clean double spaces
        result = DOUBLE_SPACE.matcher(result).replaceAll(" ");
        return result.trim();
    }
}
