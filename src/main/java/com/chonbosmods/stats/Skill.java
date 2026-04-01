package com.chonbosmods.stats;

public enum Skill {
    // CHA
    PERSUASION(Stat.CHA, "Persuasion"),
    DECEPTION(Stat.CHA, "Deception"),
    INTIMIDATION(Stat.CHA, "Intimidation"),
    PERFORMANCE(Stat.CHA, "Performance"),
    // WIS
    INSIGHT(Stat.WIS, "Insight"),
    PERCEPTION(Stat.WIS, "Perception"),
    // INT
    INVESTIGATION(Stat.INT, "Investigation"),
    ARCANA(Stat.INT, "Arcana"),
    RELIGION(Stat.INT, "Religion"),
    HISTORY(Stat.INT, "History"),
    NATURE(Stat.INT, "Nature"),
    // STR
    ATHLETICS(Stat.STR, "Athletics"),
    // DEX
    STEALTH(Stat.DEX, "Stealth"),
    SLEIGHT_OF_HAND(Stat.DEX, "Sleight of Hand"),
    ACROBATICS(Stat.DEX, "Acrobatics");

    private final Stat stat;
    private final String displayName;

    Skill(Stat stat, String displayName) {
        this.stat = stat;
        this.displayName = displayName;
    }

    public Stat getStat() { return stat; }
    public String displayName() { return displayName; }
    public String buttonText() { return "[" + stat.name() + "] " + displayName; }

    @Deprecated public Stat getAssociatedStat() { return stat; }
    @Deprecated public int getDcOffset() { return 0; }
    @Deprecated public boolean isValidFor(Stat stat) { return this.stat == stat; }
}
