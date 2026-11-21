package com.whznt.entity;

import java.util.List;

public enum ProjectStep {
    TRAITS, STORY, POSITION, TOPICS, SCRIPT;

    public boolean usesWritingStyle() {
        return this == STORY || this == SCRIPT;
    }

    public List<ProjectStep> upstream() {
        return switch (this) {
            case TRAITS -> List.of();
            case STORY -> List.of(TRAITS);
            case POSITION -> List.of(STORY);
            case TOPICS -> List.of(STORY, POSITION);
            case SCRIPT -> List.of(STORY, POSITION, TOPICS);
        };
    }

    public String label() {
        return switch (this) {
            case TRAITS -> "提炼个人特质";
            case STORY -> "人物故事";
            case POSITION -> "IP 定位";
            case TOPICS -> "短视频选题";
            case SCRIPT -> "口播稿";
        };
    }
}
