package com.whznt.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.whznt.dto.ProjectRequests;
import com.whznt.entity.MaterialPassage;
import com.whznt.entity.MaterialVersion;
import com.whznt.entity.PersonProject;
import com.whznt.entity.ProjectMaterial;
import com.whznt.exception.WorkspaceException;
import com.whznt.integration.DocumentImport;
import com.whznt.integration.PrivateFiles;
import com.whznt.mapper.MaterialPassageMapper;
import com.whznt.mapper.MaterialVersionMapper;
import com.whznt.mapper.ProjectMaterialMapper;
import com.whznt.service.PersonProjectService;
import com.whznt.service.ProjectMaterialService;
import com.whznt.vo.ProjectViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectMaterialServiceImpl extends ServiceImpl<ProjectMaterialMapper, ProjectMaterial>
        implements ProjectMaterialService {

    private final PersonProjectService projects;
    private final DocumentImport documents;
    private final PrivateFiles files;
    private final MaterialVersionMapper versions;
    private final MaterialPassageMapper passages;

    @Override
    public List<ProjectViews.Material> materials(long projectId) {
        return listForProject(projectId).stream().map(this::view).toList();
    }

    @Override
    public List<ProjectMaterial> listForProject(long projectId) {
        return lambdaQuery().eq(ProjectMaterial::getProjectId, projectId).orderByAsc(ProjectMaterial::getId).list();
    }

    @Override
    @Transactional
    public ProjectViews.Material saveText(long ownerId, long projectId, ProjectRequests.TextMaterial request) {
        PersonProject project = projects.lockOwned(ownerId, projectId);
        return saveVersion(project, request.materialId(), request.baseVersionId(), request.name(), request.kind(),
                documents.fromText(request.name(), request.text()));
    }

    @Override
    @Transactional
    public ProjectViews.Material saveFile(long ownerId, long projectId, Long materialId, Long baseVersionId,
                                          String name, ProjectMaterial.Kind kind, MultipartFile file) {
        PersonProject project = projects.lockOwned(ownerId, projectId);
        return saveVersion(project, materialId, baseVersionId, name, kind, documents.fromFile(file));
    }

    @Override
    public ProjectViews.MaterialText readVersion(long ownerId, long projectId, long materialId, long versionId) {
        projects.requireOwned(ownerId, projectId);
        MaterialVersion version = requireVersion(projectId, materialId, versionId);
        return new ProjectViews.MaterialText(versionView(version), passages(projectId, List.of(versionId)));
    }

    @Override
    public Download download(long ownerId, long projectId, long materialId, long versionId) {
        projects.requireOwned(ownerId, projectId);
        MaterialVersion version = requireVersion(projectId, materialId, versionId);
        return new Download(version.getFilename(), version.getContentType(), files.download(version.getStorageKey()));
    }

    @Override
    public List<ProjectViews.Passage> passages(long projectId, Collection<Long> versionIds) {
        if (versionIds.isEmpty()) {
            return List.of();
        }
        Map<Long, ProjectMaterial> byVersion = versionMaterials(projectId, versionIds);
        return passages.selectList(Wrappers.<MaterialPassage>lambdaQuery()
                        .in(MaterialPassage::getVersionId, byVersion.keySet())
                        .orderByAsc(MaterialPassage::getVersionId, MaterialPassage::getOrdinal))
                .stream().map(passage -> passageView(passage, byVersion.get(passage.getVersionId()))).toList();
    }

    @Override
    public List<ProjectViews.Passage> resolve(long projectId, List<Long> passageIds) {
        if (passageIds.isEmpty()) {
            return List.of();
        }
        Map<Long, MaterialPassage> rows = passages.selectByIds(passageIds).stream()
                .collect(Collectors.toMap(MaterialPassage::getId, Function.identity()));
        if (rows.size() != passageIds.size()) {
            throw WorkspaceException.notFound("找不到对应的资料片段。");
        }
        Map<Long, ProjectMaterial> byVersion = versionMaterials(projectId,
                rows.values().stream().map(MaterialPassage::getVersionId).distinct().toList());
        return passageIds.stream().map(rows::get)
                .map(passage -> passageView(passage, byVersion.get(passage.getVersionId()))).toList();
    }

    private ProjectViews.Material saveVersion(PersonProject project, Long materialId, Long expectedVersionId,
                                               String name, ProjectMaterial.Kind kind, DocumentImport.Imported data) {
        ProjectMaterial material;
        if (materialId == null) {
            if (expectedVersionId != null || name == null || name.isBlank() || name.length() > 120 || kind == null) {
                throw WorkspaceException.badRequest("请填写资料名称和类型。");
            }
            material = new ProjectMaterial();
            material.setProjectId(project.getId());
            material.setName(name.strip());
            material.setKind(kind);
            save(material);
        } else {
            material = requireMaterial(project.getId(), materialId);
            if (!Objects.equals(material.getCurrentVersionId(), expectedVersionId)) {
                throw WorkspaceException.conflict("资料已有更新，请刷新后新增版本。");
            }
            if (kind != null && kind != material.getKind()) {
                throw WorkspaceException.badRequest("更新原文请沿用资料类型。");
            }
        }

        MaterialVersion version = new MaterialVersion();
        version.setMaterialId(material.getId());
        version.setFilename(data.filename());
        version.setContentType(data.contentType());
        version.setStorageKey(files.saveOriginal(data.original()));
        version.setCreatedAt(OffsetDateTime.now());
        versions.insert(version);
        int ordinal = 0;
        for (DocumentImport.Segment segment : data.segments()) {
            MaterialPassage row = new MaterialPassage();
            row.setVersionId(version.getId());
            row.setOrdinal(++ordinal);
            row.setLocation(segment.location());
            row.setContent(segment.content());
            passages.insert(row);
        }
        material.setCurrentVersionId(version.getId());
        updateById(material);
        projects.touch(project);
        return view(material);
    }

    private Map<Long, ProjectMaterial> versionMaterials(long projectId, Collection<Long> ids) {
        Map<Long, ProjectMaterial> available = listForProject(projectId).stream()
                .collect(Collectors.toMap(ProjectMaterial::getId, Function.identity()));
        Map<Long, ProjectMaterial> result = versions.selectByIds(ids).stream()
                .filter(version -> available.containsKey(version.getMaterialId()))
                .collect(Collectors.toMap(MaterialVersion::getId, version -> available.get(version.getMaterialId())));
        if (result.size() != ids.size()) {
            throw WorkspaceException.notFound("找不到对应的资料版本。");
        }
        return result;
    }

    private ProjectMaterial requireMaterial(long projectId, long materialId) {
        ProjectMaterial material = lambdaQuery().eq(ProjectMaterial::getId, materialId)
                .eq(ProjectMaterial::getProjectId, projectId).one();
        if (material == null) {
            throw WorkspaceException.notFound("找不到这份资料。");
        }
        return material;
    }

    private MaterialVersion requireVersion(long projectId, long materialId, long versionId) {
        requireMaterial(projectId, materialId);
        MaterialVersion version = versions.selectById(versionId);
        if (version == null || version.getMaterialId() != materialId) {
            throw WorkspaceException.notFound("找不到这个资料版本。");
        }
        return version;
    }

    private ProjectViews.Material view(ProjectMaterial material) {
        List<ProjectViews.MaterialVersion> history = versions.selectList(Wrappers.<MaterialVersion>lambdaQuery()
                        .eq(MaterialVersion::getMaterialId, material.getId()).orderByDesc(MaterialVersion::getId))
                .stream().map(ProjectMaterialServiceImpl::versionView).toList();
        return new ProjectViews.Material(material.getId(), material.getName(), material.getKind(),
                material.getCurrentVersionId(), history);
    }

    private static ProjectViews.MaterialVersion versionView(MaterialVersion version) {
        return new ProjectViews.MaterialVersion(version.getId(), version.getFilename(), version.getCreatedAt());
    }

    private static ProjectViews.Passage passageView(MaterialPassage passage, ProjectMaterial material) {
        return new ProjectViews.Passage(passage.getId(), passage.getVersionId(), material.getName(),
                material.getKind(), passage.getLocation(), passage.getContent());
    }
}
