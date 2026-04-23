package com.chonbosmods.dialogue;

import com.chonbosmods.dialogue.model.ActiveFollowUp;
import com.chonbosmods.dialogue.model.DialogueNode;
import com.chonbosmods.dialogue.model.LogEntry;
import com.chonbosmods.dialogue.model.ResolvedTopic;
import com.chonbosmods.dice.RollMode;
import com.chonbosmods.stats.PlayerStats;

import java.util.List;

public interface DialoguePresenter {
    void refreshLog(List<LogEntry> log);
    void refreshFollowUps(List<ActiveFollowUp> followUps);
    void refreshTopics(List<ResolvedTopic> visibleTopics);
    void refreshDisposition(int disposition);
    void showSkillCheck(DialogueNode.SkillCheckNode node, int dc, RollMode mode, PlayerStats stats);
    void flushUpdates();
    void openInitialPage(List<ResolvedTopic> visibleTopics, int disposition);
    void close();
}
