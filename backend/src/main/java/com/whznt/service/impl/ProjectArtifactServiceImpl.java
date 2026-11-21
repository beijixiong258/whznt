package com.whznt.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whznt.dto.ProjectGeneration;
import com.whznt.dto.ProjectRequests;
import com.whznt.entity.Conversation;
import com.whznt.entity.Draft;
import com.whznt.entity.DraftVersion;
import com.whznt.entity.PersonProject;
import com.whznt.entity.ProjectArtifact;
import com.whznt.entity.ProjectStep;
import com.whznt.entity.ProjectVersionContext;
import com.whznt.exception.WorkspaceException;
import com.whznt.mapper.ProjectArtifactMapper;
import com.whznt.mapper.ProjectVersionContextMapper;
import com.whznt.service.ConversationService;
import com.whznt.service.DraftService;
import com.whznt.service.DraftVersionService;
import com.whznt.service.PersonProjectService;
import com.whznt.service.ProjectArtifactService;
import com.whznt.service.ProjectMaterialService;
import com.whznt.service.TopicTitles;
import com.whznt.vo.ProjectViews;
import com.whznt.vo.WorkspaceViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ProjectArtifactServiceImpl extends ServiceImpl<ProjectArtifactMapper, ProjectArtifact>
        implements ProjectArtifactService {

    private final PersonProjectService projects;
    private final ProjectMaterialService materials;
    private final ConversationService conversations;
    private final DraftService drafts;
    private final DraftVersionService versions;
    private final ProjectVersionContextMapper contexts;
    private final ObjectMapper json;

    @Override
    public List<ProjectViews.Artifact> artifacts(long projectId) {
        return lambdaQuery().eq(ProjectArtifact::getProjectId, projectId).orderByAsc(ProjectArtifact::getId)
                .list().stream().map(row -> view(row, current(row))).toList();
    }

    @Override
    public List<ProjectViews.Topic> topics(long projectId) {
        ProjectArtifact artifact = inSlot(projectId, ProjectStep.TOPICS, "primary");
        if (artifact == null) {
            return List.of();
        }
        DraftVersion version = current(artifact);
        List<String> titles = TopicTitles.parse(version.getContent());
        return IntStream.range(0, titles.size())
                .mapToObj(index -> new ProjectViews.Topic(index + 1, version.getId(), titles.get(index))).toList();
    }

    @Override
    public ProjectArtifact inSlot(long projectId, ProjectStep step, String slot) {
        return lambdaQuery().eq(ProjectArtifact::getProjectId, projectId).eq(ProjectArtifact::getStep, step)
                .eq(ProjectArtifact::getSlot, slot).one();
    }

    @Override
    public ProjectArtifact require(long projectId, long artifactId) {
        ProjectArtifact artifact = lambdaQuery().eq(ProjectArtifact::getProjectId, projectId)
                .eq(ProjectArtifact::getId, artifactId).one();
        if (artifact == null) {
            throw WorkspaceException.notFound("找不到这个项目成果。");
        }
        return artifact;
    }

    @Override
    public DraftVersion current(ProjectArtifact artifact) {
        return Objects.requireNonNull(drafts.currentVersion(artifact.getConversationId()), "Missing project draft");
    }

    @Override
    public ArtifactVersion requireVersion(long projectId, long versionId) {
        DraftVersion version = versions.getById(versionId);
        if (version == null) {
            throw WorkspaceException.notFound("找不到这个成果版本。");
        }
        Draft draft = drafts.getById(version.getDraftId());
        ProjectArtifact artifact = lambdaQuery().eq(ProjectArtifact::getProjectId, projectId)
                .eq(ProjectArtifact::getConversationId, draft.getConversationId()).one();
        if (artifact == null) {
            throw WorkspaceException.notFound("找不到这个项目的成果版本。");
        }
        return new ArtifactVersion(artifact.getStep(), artifact.getConversationId(), version);
    }

    @Override
    public ProjectGeneration.Sources references(long projectId, long versionId) {
        ProjectVersionContext saved = contexts.selectById(versionId);
        if (saved == null || saved.getProjectId() != projectId) {
            return ProjectGeneration.Sources.empty();
        }
        try {
            return json.readValue(saved.getSnapshot(), ProjectGeneration.Sources.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read project version sources", exception);
        }
    }

    @Override
    public ProjectViews.Sources readSources(long ownerId, long projectId, long versionId) {
        projects.requireOwned(ownerId, projectId);
        requireVersion(projectId, versionId);
        ProjectGeneration.Sources sources = references(projectId, versionId);
        List<ProjectViews.Upstream> upstream = sources.upstreamVersionIds().stream().map(id -> {
            ArtifactVersion source = requireVersion(projectId, id);
            return new ProjectViews.Upstream(source.step(), source.conversationId(), id, source.version().getTitle());
        }).toList();
        return new ProjectViews.Sources(upstream, materials.resolve(projectId, sources.passageIds()));
    }

    @Override
    @Transactional
    public ProjectViews.Artifact importArtifact(long ownerId, long projectId, ProjectRequests.ExistingArtifact request) {
        PersonProject project = projects.lockOwned(ownerId, projectId);
        if (request.step() == ProjectStep.SCRIPT) {
            throw WorkspaceException.badRequest("请先录入选题，再从选题编辑口播稿。");
        }
        if (request.step() == ProjectStep.TOPICS && TopicTitles.parse(request.content()).isEmpty()) {
            throw WorkspaceException.badRequest("请按每行一个标题粘贴选题。");
        }
        if (inSlot(projectId, request.step(), "primary") != null) {
            throw WorkspaceException.conflict("已有这个成果。打开编辑器即可保存新版。");
        }
        ProjectArtifact artifact = createArtifact(project, request.step(), "primary", "");
        Conversation conversation = conversations.lockOwned(ownerId, artifact.getConversationId());
        DraftVersion result = drafts.saveProjectVersion(conversation, null, request.content(),
                DraftVersion.Source.EDITED, null);
        projects.touch(project);
        return view(artifact, result);
    }

    @Override
    public Saved publish(PersonProject project, ProjectGeneration.Plan plan, String content, List<Long> passageIds,
                         String model) {
        ProjectArtifact artifact = inSlot(project.getId(), plan.step(), plan.slot());
        Long actualId = artifact == null ? null : artifact.getId();
        if (!Objects.equals(actualId, plan.artifactId())) {
            throw WorkspaceException.conflict("这个成果已有新版本，请重新选择后生成。");
        }
        if (artifact == null) {
            artifact = createArtifact(project, plan.step(), plan.slot(), plan.topic());
        }
        Conversation conversation = conversations.lockOwned(project.getOwnerId(), artifact.getConversationId());
        DraftVersion version = drafts.saveProjectVersion(conversation, plan.baseVersionId(), content,
                DraftVersion.Source.GENERATED, model);
        saveContext(project.getId(), version.getId(), new ProjectGeneration.Sources(plan.upstreamVersionIds(), passageIds));
        projects.touch(project);
        return new Saved(conversation.getId(), version.getId());
    }

    private ProjectArtifact createArtifact(PersonProject project, ProjectStep step, String slot, String topic) {
        String title = step == ProjectStep.SCRIPT ? topic : project.getName() + " · " + step.label();
        Conversation conversation = conversations.createForProject(project.getOwnerId(), project.getId(), title);
        ProjectArtifact artifact = new ProjectArtifact();
        artifact.setProjectId(project.getId());
        artifact.setConversationId(conversation.getId());
        artifact.setStep(step);
        artifact.setSlot(slot);
        artifact.setTopic(topic);
        save(artifact);
        return artifact;
    }

    private void saveContext(long projectId, long versionId, ProjectGeneration.Sources sources) {
        ProjectVersionContext context = new ProjectVersionContext();
        context.setId(versionId);
        context.setProjectId(projectId);
        try {
            context.setSnapshot(json.writeValueAsString(sources));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to store project version sources", exception);
        }
        contexts.insert(context);
    }

    private static ProjectViews.Artifact view(ProjectArtifact artifact, DraftVersion version) {
        return new ProjectViews.Artifact(artifact.getId(), artifact.getStep(), artifact.getTopic(),
                artifact.getConversationId(), WorkspaceViews.VersionRow.from(version));
    }
}
