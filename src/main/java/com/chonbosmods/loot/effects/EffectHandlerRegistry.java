package com.chonbosmods.loot.effects;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class EffectHandlerRegistry {
    private final Map<String, EffectHandler> handlers = new HashMap<>();

    public void register(String affixId, EffectHandler handler) {
        handlers.put(affixId, handler);
    }

    @Nullable
    public EffectHandler get(String affixId) {
        return handlers.get(affixId);
    }

    public int getRegisteredCount() {
        return handlers.size();
    }
}
