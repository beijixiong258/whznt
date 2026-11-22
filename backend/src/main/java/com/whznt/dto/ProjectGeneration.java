package com.whznt.dto;

import com.whznt.entity.ProjectStep;

import java.util.List;

public final class ProjectGeneration {

    private ProjectGeneration() {
    }

    public record Plan(long ownerId, long projectId, String person, String summary, ProjectStep step, String slot,
                       Long artifactId, Long baseVersionId, String topic, int durationSeconds, String instructions,
                       List<Long> upstreamVersionIds, List<Long> materialVersionIds) {
    }

    public record Sources(List<Long> upstreamVersionIds, List<Long> passageIds) {
        public static Sources empty() {
            return new Sources(List.of(), List.of());
        }
    }

    public record Result(String content, String model) {
    }
}
