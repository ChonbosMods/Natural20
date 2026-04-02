package com.chonbosmods.topic;

import java.util.List;

public record PostureSelection(List<ResolvedPrompt> prompts) {

    public record ResolvedPrompt(String groupName, String text, int dispositionModifier) {}
}
