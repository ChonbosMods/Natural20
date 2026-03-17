package com.chonbosmods.quest;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DialogueResolver {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{(\\w+)}");

    /**
     * Replace all {variable} tokens in template with values from bindings.
     * Unresolved tokens are stripped and surrounding whitespace cleaned up.
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
                // Strip unresolved token and clean surrounding whitespace
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);

        // Clean up double spaces left by stripped tokens
        return sb.toString().replaceAll("  +", " ").trim();
    }
}
