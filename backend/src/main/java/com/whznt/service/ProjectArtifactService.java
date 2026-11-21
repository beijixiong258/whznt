package com.whznt.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.whznt.dto.ProjectGeneration;
import com.whznt.dto.ProjectRequests;
import com.whznt.entity.DraftVersion;
import com.whznt.entity.PersonProject;
import com.whznt.entity.ProjectArtifact;
import com.whznt.entity.ProjectStep;
import com.whznt.vo.ProjectViews;

import java.util.List;

public interface ProjectArtifactService extends IService<ProjectArtifact> {
    List<ProjectViews.Artifact> artifacts(long projectId);
    List<ProjectViews.Topic> topics(long projectId);
    ProjectArtifact inSlot(long projectId, ProjectStep step, String slot);
    ProjectArtifact require(long projectId, long artifactId);
    DraftVersion current(ProjectArtifact artifact);
    ArtifactVersion requireVersion(long projectId, long versionId);
    ProjectGeneration.Sources references(long projectId, long versionId);
    ProjectViews.Sources readSources(long ownerId, long projectId, long versionId);
    ProjectViews.Artifact importArtifact(long ownerId, long projectId, ProjectRequests.ExistingArtifact request);
    Saved publish(PersonProject project, ProjectGeneration.Plan plan, String content, List<Long> passageIds, String model);

    record ArtifactVersion(ProjectStep step, long conversationId, DraftVersion version) {
    }

    record Saved(long conversationId, long versionId) {
    }
}
