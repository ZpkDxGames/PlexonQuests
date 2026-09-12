package com.zpkdxgames.plexonquests.integration.skills;

import java.util.List;
import java.util.Optional;

public record PlayerSkillsSnapshot(boolean available, boolean ready, int totalLevel, List<SkillSnapshot> skills) {
    public PlayerSkillsSnapshot { skills = List.copyOf(skills); }

    public static PlayerSkillsSnapshot unavailable() {
        return new PlayerSkillsSnapshot(false, false, 0, List.of());
    }

    public static PlayerSkillsSnapshot loading() {
        return new PlayerSkillsSnapshot(true, false, 0, List.of());
    }

    public Optional<SkillSnapshot> skill(String id) {
        if (id == null) return Optional.empty();
        return skills.stream().filter(skill -> skill.id().equalsIgnoreCase(id)).findFirst();
    }
}
