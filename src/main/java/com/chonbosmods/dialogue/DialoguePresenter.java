package com.chonbosmods.dialogue;

import com.chonbosmods.dialogue.model.DialogueNode;
import com.chonbosmods.dialogue.model.LogEntry;
import com.chonbosmods.dialogue.model.ResolvedTopic;
import com.chonbosmods.stats.PlayerStats;

import java.util.List;

public interface DialoguePresenter {
    void refreshLog(List<LogEntry> log);
    void refreshTopics(List<ResolvedTopic> visibleTopics);
    void refreshDisposition(int disposition);
    void showSkillCheck(DialogueNode.SkillCheckNode node, int effectiveDC, PlayerStats stats);
    void close();
}
