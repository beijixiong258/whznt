package com.whznt.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.whznt.dto.ProjectGeneration;
import com.whznt.dto.ProjectRequests;
import com.whznt.entity.ProjectJob;
import com.whznt.vo.ProjectViews;

import java.util.List;

public interface ProjectJobService extends IService<ProjectJob> {
    ProjectViews.Job enqueue(long ownerId, long projectId, ProjectRequests.Generate request);
    List<ProjectViews.Job> recent(long projectId);
    ProjectJob claimNext();
    void recover();
    ProjectGeneration.Plan planOf(ProjectJob job);
    void complete(long jobId, ProjectGeneration.Plan plan, String result, List<Long> passageIds, String model);
    void fail(long id, String message);
    void clearCheckpointIfDeleted(ProjectJob job);
}
