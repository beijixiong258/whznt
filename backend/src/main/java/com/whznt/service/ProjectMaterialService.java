package com.whznt.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.whznt.dto.ProjectRequests;
import com.whznt.entity.ProjectMaterial;
import com.whznt.vo.ProjectViews;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;

public interface ProjectMaterialService extends IService<ProjectMaterial> {
    List<ProjectViews.Material> materials(long projectId);
    List<Long> currentVersionIds(long projectId);
    List<ProjectViews.Passage> passages(long projectId, Collection<Long> versionIds);
    List<ProjectViews.Passage> resolve(long projectId, List<Long> passageIds);
    ProjectViews.Material saveText(long ownerId, long projectId, ProjectRequests.TextMaterial request);
    ProjectViews.Material saveFile(long ownerId, long projectId, Long materialId, Long baseVersionId,
                                   String name, ProjectMaterial.Kind kind, MultipartFile file);
    ProjectViews.MaterialText readVersion(long ownerId, long projectId, long materialId, long versionId);
    Download download(long ownerId, long projectId, long materialId, long versionId);

    record Download(String filename, String contentType, Resource resource) {
    }
}
