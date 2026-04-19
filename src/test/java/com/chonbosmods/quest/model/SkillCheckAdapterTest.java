package com.chonbosmods.quest.model;

import com.chonbosmods.dialogue.DifficultyTier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillCheckAdapterTest {

    private Gson gson;

    @BeforeEach
    void setUp() {
        gson = new GsonBuilder()
                .registerTypeAdapter(QuestTemplateV2.SkillCheck.class, new SkillCheckAdapter())
                .create();
    }

    @Test
    void authored_whenTierPresent() {
        String json = "{\"skill\":\"DECEPTION\",\"tier\":\"HARD\",\"passText\":\"P\",\"failText\":\"F\"}";
        QuestTemplateV2.SkillCheck sc = gson.fromJson(json, QuestTemplateV2.SkillCheck.class);
        assertInstanceOf(QuestTemplateV2.SkillCheck.Authored.class, sc);
        QuestTemplateV2.SkillCheck.Authored a = (QuestTemplateV2.SkillCheck.Authored) sc;
        assertEquals(DifficultyTier.HARD, a.tier());
        assertEquals("P", a.passText());
        assertEquals("F", a.failText());
    }

    @Test
    void procedural_whenTierAbsent() {
        String json = "{\"skill\":\"DECEPTION\",\"passText\":\"P\",\"failText\":\"F\"}";
        QuestTemplateV2.SkillCheck sc = gson.fromJson(json, QuestTemplateV2.SkillCheck.class);
        assertInstanceOf(QuestTemplateV2.SkillCheck.Procedural.class, sc);
        assertEquals("P", sc.passText());
        assertEquals("F", sc.failText());
    }

    @Test
    void procedural_whenLegacyDcFieldPresent() {
        // Existing dev/quest_skill_expansion/*.json still have legacy "dc" fields until Phase H
        // strips them. The discriminator is tier-presence only; "dc" is ignored silently.
        String json = "{\"skill\":\"DECEPTION\",\"dc\":12,\"passText\":\"P\",\"failText\":\"F\"}";
        QuestTemplateV2.SkillCheck sc = gson.fromJson(json, QuestTemplateV2.SkillCheck.class);
        assertInstanceOf(QuestTemplateV2.SkillCheck.Procedural.class, sc);
    }
}
