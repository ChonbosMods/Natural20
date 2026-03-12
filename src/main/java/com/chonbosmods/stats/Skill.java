package com.chonbosmods.stats;

public enum Skill {
    PERSUASION(Stat.CHA),
    INTIMIDATION(Stat.CHA),
    DECEPTION(Stat.CHA),
    INSIGHT(Stat.WIS),
    PERCEPTION(Stat.WIS),
    INVESTIGATION(Stat.INT),
    ATHLETICS(Stat.STR),
    STEALTH(Stat.DEX);

    private final Stat associatedStat;

    Skill(Stat associatedStat) {
        this.associatedStat = associatedStat;
    }

    public Stat getAssociatedStat() {
        return associatedStat;
    }
}
