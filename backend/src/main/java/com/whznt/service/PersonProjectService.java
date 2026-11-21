package com.whznt.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.whznt.dto.ProjectRequests;
import com.whznt.entity.PersonProject;
import com.whznt.vo.ProjectViews;

import java.util.List;

public interface PersonProjectService extends IService<PersonProject> {
    PersonProject requireOwned(long ownerId, long projectId);
    PersonProject lockOwned(long ownerId, long projectId);
    List<ProjectViews.Profile> profiles(long ownerId);
    ProjectViews.Profile saveProfile(long ownerId, Long projectId, ProjectRequests.Profile request);
    void touch(PersonProject project);
}
