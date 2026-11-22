package com.whznt.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.whznt.dto.ProjectRequests;
import com.whznt.entity.PersonProject;
import com.whznt.exception.WorkspaceException;
import com.whznt.mapper.PersonProjectMapper;
import com.whznt.mapper.ProjectJobMapper;
import com.whznt.service.PersonProjectService;
import com.whznt.vo.ProjectViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PersonProjectServiceImpl extends ServiceImpl<PersonProjectMapper, PersonProject>
        implements PersonProjectService {

    private final ProjectJobMapper jobs;

    @Override
    public PersonProject requireOwned(long ownerId, long projectId) {
        return require(lambdaQuery().eq(PersonProject::getId, projectId)
                .eq(PersonProject::getOwnerId, ownerId).isNull(PersonProject::getDeletedAt).one());
    }

    @Override
    public PersonProject lockOwned(long ownerId, long projectId) {
        return require(baseMapper.lockOwned(projectId, ownerId));
    }

    @Override
    public List<ProjectViews.Profile> profiles(long ownerId) {
        return lambdaQuery().eq(PersonProject::getOwnerId, ownerId).isNull(PersonProject::getDeletedAt)
                .orderByDesc(PersonProject::getUpdatedAt, PersonProject::getId)
                .list().stream().map(ProjectViews.Profile::from).toList();
    }

    @Override
    @Transactional
    public ProjectViews.Profile saveProfile(long ownerId, Long projectId, ProjectRequests.Profile request) {
        PersonProject project = projectId == null ? new PersonProject() : lockOwned(ownerId, projectId);
        project.setOwnerId(ownerId);
        project.setName(request.name().strip());
        project.setSummary(Objects.toString(request.summary(), "").strip());
        project.setUpdatedAt(OffsetDateTime.now());
        saveOrUpdate(project);
        return ProjectViews.Profile.from(project);
    }

    @Override
    @Transactional
    public void deleteProfile(long ownerId, long projectId) {
        if (baseMapper.markDeleted(projectId, ownerId) == 0) {
            throw WorkspaceException.notFound("找不到这个人物项目。");
        }
        jobs.cancelForDeletedProject(projectId);
    }

    @Override
    public void touch(PersonProject project) {
        project.setUpdatedAt(OffsetDateTime.now());
        updateById(project);
    }

    private static PersonProject require(PersonProject project) {
        if (project == null) {
            throw WorkspaceException.notFound("找不到这个人物项目。");
        }
        return project;
    }
}
