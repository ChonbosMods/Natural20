package com.chonbosmods.quest.model;

import com.chonbosmods.dialogue.DifficultyTier;
import com.chonbosmods.stats.Skill;
import com.google.gson.*;

import java.lang.reflect.Type;

/**
 * Gson codec for {@link QuestTemplateV2.SkillCheck}. Tier-presence is the discriminator:
 * {@code "tier": "<enum>"} present -> {@link QuestTemplateV2.SkillCheck.Authored},
 * absent -> {@link QuestTemplateV2.SkillCheck.Procedural}. Any other fields (e.g. legacy
 * "dc") are silently ignored.
 */
public class SkillCheckAdapter implements JsonDeserializer<QuestTemplateV2.SkillCheck>,
        JsonSerializer<QuestTemplateV2.SkillCheck> {

    @Override
    public QuestTemplateV2.SkillCheck deserialize(JsonElement el, Type t, JsonDeserializationContext ctx) {
        JsonObject o = el.getAsJsonObject();
        Skill skill = Skill.valueOf(requireString(o, "skill"));
        String pass = requireString(o, "passText");
        String fail = requireString(o, "failText");
        if (o.has("tier")) {
            DifficultyTier tier = DifficultyTier.valueOf(o.get("tier").getAsString());
            return new QuestTemplateV2.SkillCheck.Authored(skill, tier, pass, fail);
        }
        return new QuestTemplateV2.SkillCheck.Procedural(skill, pass, fail);
    }

    private static String requireString(JsonObject o, String field) {
        if (!o.has(field) || o.get(field).isJsonNull()) {
            throw new JsonParseException("skillCheck missing required field: " + field);
        }
        return o.get(field).getAsString();
    }

    @Override
    public JsonElement serialize(QuestTemplateV2.SkillCheck src, Type t, JsonSerializationContext ctx) {
        JsonObject o = new JsonObject();
        o.addProperty("skill", src.skill().name());
        if (src instanceof QuestTemplateV2.SkillCheck.Authored a) {
            o.addProperty("tier", a.tier().name());
        }
        o.addProperty("passText", src.passText());
        o.addProperty("failText", src.failText());
        return o;
    }
}
