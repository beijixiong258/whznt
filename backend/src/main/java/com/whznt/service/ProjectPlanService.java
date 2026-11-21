package com.whznt.service;

import com.whznt.dto.ProjectGeneration;
import com.whznt.dto.ProjectRequests;
import com.whznt.entity.DraftVersion;
import com.whznt.entity.PersonProject;
import com.whznt.entity.ProjectArtifact;
import com.whznt.entity.ProjectStep;
import com.whznt.exception.WorkspaceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProjectPlanService {

    private final ProjectArtifactService artifacts;
    private final ProjectMaterialService materials;

    public ProjectGeneration.Plan plan(PersonProject project, ProjectRequests.Generate request) {
        long projectId = project.getId();
        ProjectStep step = request.step();
        ProjectArtifact selected = request.artifactId() == null ? null : artifacts.require(projectId, request.artifactId());
        if (selected != null && selected.getStep() != step) {
            throw WorkspaceException.badRequest("请选择与当前步骤对应的成果。");
        }
        Target target = step == ProjectStep.SCRIPT ? scriptTarget(projectId, request, selected)
                : new Target("primary", "", null);
        ProjectArtifact existing = artifacts.inSlot(projectId, step, target.slot());
        DraftVersion current = existing == null ? null : artifacts.current(existing);
        if (selected == null && existing != null && step == ProjectStep.SCRIPT) {
            throw WorkspaceException.conflict("这个选题已经生成了口播稿，可在已有口播稿中打开或重做。");
        }
        Long currentId = current == null ? null : current.getId();
        if (!Objects.equals(currentId, request.baseVersionId())) {
            throw WorkspaceException.conflict("成果版本已变更，请刷新后再生成。");
        }

        List<Long> upstream = new ArrayList<>();
        for (ProjectStep source : step.upstream()) {
            if (source == ProjectStep.TOPICS) {
                if (target.topicVersionId() != null) {
                    upstream.add(target.topicVersionId());
                }
            } else {
                ProjectArtifact latest = artifacts.inSlot(projectId, source, "primary");
                if (latest != null) {
                    upstream.add(artifacts.current(latest).getId());
                }
            }
        }
        List<Long> materialVersions = materials.currentVersionIds(projectId);
        if (project.getSummary().isBlank() && upstream.isEmpty()
                && materials.materials(projectId).stream().noneMatch(material -> material.kind() != com.whznt.entity.ProjectMaterial.Kind.STYLE)) {
            throw WorkspaceException.badRequest("请补充人物简介、采访资料或已有成果。");
        }
        return new ProjectGeneration.Plan(project.getOwnerId(), projectId, project.getName(), project.getSummary(),
                step, target.slot(), existing == null ? null : existing.getId(), currentId, target.title(),
                request.durationSeconds() == null ? 150 : request.durationSeconds(),
                Objects.toString(request.instructions(), "").strip(), upstream, materialVersions);
    }

    private Target scriptTarget(long projectId, ProjectRequests.Generate request, ProjectArtifact selected) {
        if (selected != null) {
            Long originalTopic = artifacts.references(projectId, artifacts.current(selected).getId()).upstreamVersionIds()
                    .stream().filter(id -> artifacts.describeVersion(projectId, id).step() == ProjectStep.TOPICS)
                    .findFirst().orElse(null);
            return new Target(selected.getSlot(), selected.getTopic(), originalTopic);
        }
        if (request.topicVersionId() == null || request.topicIndex() == null
                || artifacts.describeVersion(projectId, request.topicVersionId()).step() != ProjectStep.TOPICS) {
            throw WorkspaceException.badRequest("请选择一条项目选题。");
        }
        DraftVersion version = artifacts.requireVersion(projectId, request.topicVersionId());
        List<String> titles = TopicTitles.parse(version.getContent());
        if (request.topicIndex() > titles.size()) {
            throw WorkspaceException.badRequest("这个选题已找不到，请刷新后重新选择。");
        }
        String slot = "topic-" + version.getId() + "-" + request.topicIndex();
        return new Target(slot, titles.get(request.topicIndex() - 1), version.getId());
    }

    private record Target(String slot, String title, Long topicVersionId) {
    }
}
