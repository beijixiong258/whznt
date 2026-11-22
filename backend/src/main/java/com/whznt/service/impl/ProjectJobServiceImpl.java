package com.whznt.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whznt.dto.ProjectGeneration;
import com.whznt.dto.ProjectRequests;
import com.whznt.entity.PersonProject;
import com.whznt.entity.ProjectJob;
import com.whznt.exception.WorkspaceException;
import com.whznt.integration.TextModel;
import com.whznt.mapper.ProjectJobMapper;
import com.whznt.service.PersonProjectService;
import com.whznt.service.ProjectArtifactService;
import com.whznt.service.ProjectJobService;
import com.whznt.service.ProjectPlanService;
import com.whznt.vo.ProjectViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectJobServiceImpl extends ServiceImpl<ProjectJobMapper, ProjectJob> implements ProjectJobService {

    private final ObjectMapper json;
    private final PersonProjectService projects;
    private final ProjectPlanService planner;
    private final ProjectArtifactService artifacts;
    private final TextModel model;

    @Override
    @Transactional
    public ProjectViews.Job enqueue(long ownerId, long projectId, ProjectRequests.Generate request) {
        PersonProject project = projects.lockOwned(ownerId, projectId);
        String key = request.requestId().toString();
        ProjectJob prior = lambdaQuery().eq(ProjectJob::getProjectId, projectId).eq(ProjectJob::getRequestKey, key).one();
        if (prior != null) {
            return ProjectViews.Job.from(prior);
        }
        model.ready();
        ProjectGeneration.Plan plan = planner.plan(project, request);
        if (lambdaQuery().eq(ProjectJob::getProjectId, projectId).eq(ProjectJob::getStep, plan.step())
                .eq(ProjectJob::getSlot, plan.slot()).in(ProjectJob::getState, ProjectJob.State.QUEUED,
                        ProjectJob.State.RUNNING).exists()) {
            throw WorkspaceException.conflict("这个成果正在生成，完成后可在项目中查看。");
        }
        ProjectJob job = new ProjectJob();
        job.setProjectId(projectId);
        job.setRequestKey(key);
        job.setStep(plan.step());
        job.setSlot(plan.slot());
        job.setState(ProjectJob.State.QUEUED);
        job.setCreatedAt(OffsetDateTime.now());
        job.setInputSnapshot(encode(plan));
        save(job);
        return ProjectViews.Job.from(job);
    }

    @Override
    public List<ProjectViews.Job> recent(long projectId) {
        return lambdaQuery().eq(ProjectJob::getProjectId, projectId).orderByDesc(ProjectJob::getId)
                .last("LIMIT 20").list().stream().map(ProjectViews.Job::from).toList();
    }

    @Override
    public ProjectJob claimNext() {
        ProjectJob job = lambdaQuery().eq(ProjectJob::getState, ProjectJob.State.QUEUED)
                .orderByAsc(ProjectJob::getId).last("LIMIT 1").one();
        return job != null && baseMapper.claim(job.getId()) == 1 ? job : null;
    }

    @Override
    public void recover() {
        baseMapper.requeueInterrupted();
    }

    @Override
    public ProjectGeneration.Plan planOf(ProjectJob job) {
        try {
            return json.readValue(job.getInputSnapshot(), ProjectGeneration.Plan.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read project generation plan", exception);
        }
    }

    @Override
    @Transactional
    public void complete(long jobId, ProjectGeneration.Plan plan, String result, List<Long> passageIds, String model) {
        PersonProject project = projects.lockOwned(plan.ownerId(), plan.projectId());
        ProjectJob job = baseMapper.lock(jobId);
        if (job.getState() == ProjectJob.State.COMPLETED) {
            return;
        }
        if (job.getState() != ProjectJob.State.RUNNING) {
            throw WorkspaceException.conflict("这次生成已结束。");
        }
        ProjectArtifactService.Saved saved = artifacts.publish(project, plan, result, passageIds, model);
        job.setConversationId(saved.conversationId());
        job.setResultVersionId(saved.versionId());
        job.setState(ProjectJob.State.COMPLETED);
        updateById(job);
    }

    @Override
    public void fail(long id, String message) {
        lambdaUpdate().eq(ProjectJob::getId, id).eq(ProjectJob::getState, ProjectJob.State.RUNNING)
                .set(ProjectJob::getError, message).set(ProjectJob::getState, ProjectJob.State.FAILED).update();
    }

    private String encode(ProjectGeneration.Plan plan) {
        try {
            return json.writeValueAsString(plan);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to store project generation plan", exception);
        }
    }
}
