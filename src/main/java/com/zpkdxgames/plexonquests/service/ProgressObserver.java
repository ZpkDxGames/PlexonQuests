package com.zpkdxgames.plexonquests.service;

import com.zpkdxgames.plexonquests.quest.ProgressResult;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import org.bukkit.entity.Player;

@FunctionalInterface
public interface ProgressObserver {
    void onProgress(Player player, QuestAssignment assignment, ProgressResult result);
}

