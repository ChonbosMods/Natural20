package com.chonbosmods.stats;

public enum Skill {
    INTIMIDATION(Stat.STR, -1),
    COMMAND(Stat.STR, -2),
    SLEIGHT(Stat.DEX, -2),
    REFLEX(Stat.DEX, -2),
    ENDURANCE(Stat.CON, -2),
    RESISTANCE(Stat.CON, -2),
    DEDUCTION(Stat.INT, -1),
    RECALL(Stat.INT, -1),
    PERCEPTION(Stat.WIS, 0),
    INSIGHT(Stat.WIS, 0),
    PERSUASION(Stat.CHA, 0),
    DECEPTION(Stat.CHA, -1),
    CHARM(Stat.CHA, -1);

    private final Stat associatedStat;
    private final int dcOffset;

    Skill(Stat associatedStat, int dcOffset) {
        this.associatedStat = associatedStat;
        this.dcOffset = dcOffset;
    }

    public Stat getAssociatedStat() {
        return associatedStat;
    }

    public int getDcOffset() {
        return dcOffset;
    }

    public String displayName() {
        String lower = name().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public boolean isValidFor(Stat stat) {
        return this.associatedStat == stat;
    }
}
