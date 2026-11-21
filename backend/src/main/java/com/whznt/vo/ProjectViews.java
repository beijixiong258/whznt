package com.whznt.vo;

import com.whznt.entity.PersonProject;
import com.whznt.entity.ProjectJob;
import com.whznt.entity.ProjectMaterial;
import com.whznt.entity.ProjectStep;

import java.time.OffsetDateTime;
import java.util.List;

public final class ProjectViews {

    private ProjectViews() {
    }

    public record Profile(long id, String name, String summary, OffsetDateTime updatedAt) {
        public static Profile from(PersonProject project) {
            return new Profile(project.getId(), project.getName(), project.getSummary(), project.getUpdatedAt());
        }
    }

    public record Material(long id, String name, ProjectMaterial.Kind kind, long currentVersionId,
                           List<MaterialVersion> versions) {
    }

    public record MaterialVersion(long id, String filename, OffsetDateTime createdAt) {
    }

    public record Passage(long id, long versionId, String material, ProjectMaterial.Kind kind,
                          String location, String content) {
    }

    public record MaterialText(MaterialVersion version, List<Passage> passages) {
    }

    public record Artifact(long id, ProjectStep step, String topic,
                           long conversationId, WorkspaceViews.VersionRow currentVersion) {
    }

    public record Topic(int index, long versionId, String title) {
    }

    public record Job(long id, ProjectStep step, ProjectJob.State state, String error,
                      Long conversationId, Long versionId) {
        public static Job from(ProjectJob job) {
            return new Job(job.getId(), job.getStep(), job.getState(), job.getError(),
                    job.getConversationId(), job.getResultVersionId());
        }
    }

    public record Detail(Profile project, List<Material> materials, List<Artifact> artifacts,
                         List<Topic> topics, List<Job> jobs) {
    }

    public record Upstream(ProjectStep step, long conversationId, long versionId, String title) {
    }

    public record Sources(List<Upstream> upstream, List<Passage> passages) {
    }
}
