package com.whznt.controller;

import com.whznt.dto.ProjectRequests;
import com.whznt.entity.PersonProject;
import com.whznt.entity.ProjectMaterial;
import com.whznt.service.PersonProjectService;
import com.whznt.service.ProjectArtifactService;
import com.whznt.service.ProjectJobService;
import com.whznt.service.ProjectMaterialService;
import com.whznt.service.ProjectSearchService;
import com.whznt.config.AccountPrincipal;
import com.whznt.vo.ProjectViews;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final PersonProjectService projects;
    private final ProjectMaterialService materials;
    private final ProjectArtifactService artifacts;
    private final ProjectJobService jobs;
    private final ProjectSearchService search;

    @GetMapping
    public List<ProjectViews.Profile> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return projects.profiles(principal.accountId());
    }

    @PostMapping
    public ProjectViews.Profile create(@AuthenticationPrincipal AccountPrincipal principal,
                                       @Valid @RequestBody ProjectRequests.Profile request) {
        return projects.saveProfile(principal.accountId(), null, request);
    }

    @PatchMapping("/{id}")
    public ProjectViews.Profile update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id,
                                       @Valid @RequestBody ProjectRequests.Profile request) {
        return projects.saveProfile(principal.accountId(), id, request);
    }

    @GetMapping("/{id}")
    public ProjectViews.Detail detail(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id) {
        PersonProject project = projects.requireOwned(principal.accountId(), id);
        return new ProjectViews.Detail(ProjectViews.Profile.from(project), materials.materials(id),
                artifacts.artifacts(id), artifacts.topics(id), jobs.recent(id));
    }

    @PostMapping("/{id}/materials/text")
    public ProjectViews.Material paste(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id,
                                       @Valid @RequestBody ProjectRequests.TextMaterial input) {
        return materials.saveText(principal.accountId(), id, input);
    }

    @PostMapping(value = "/{id}/materials/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProjectViews.Material upload(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id,
                                         @RequestParam @Size(max = 120) String name,
                                         @RequestParam ProjectMaterial.Kind kind,
                                         @RequestParam(required = false) @Positive Long materialId,
                                         @RequestParam(required = false) @Positive Long baseVersionId,
                                         @RequestParam MultipartFile file) {
        return materials.saveFile(principal.accountId(), id, materialId, baseVersionId, name, kind, file);
    }

    @GetMapping("/{id}/materials/{materialId}/versions/{versionId}")
    public ProjectViews.MaterialText material(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id,
                                              @PathVariable long materialId, @PathVariable long versionId) {
        return materials.readVersion(principal.accountId(), id, materialId, versionId);
    }

    @GetMapping("/{id}/materials/{materialId}/versions/{versionId}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id,
                                             @PathVariable long materialId, @PathVariable long versionId) {
        ProjectMaterialService.Download file = materials.download(principal.accountId(), id, materialId, versionId);
        String disposition = ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8)
                .build().toString();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition).body(file.resource());
    }

    @GetMapping("/{id}/materials/search")
    public List<ProjectViews.Passage> search(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id,
                                             @RequestParam @Size(max = 120) String q) {
        return search.search(principal.accountId(), id, q);
    }

    @PostMapping("/{id}/artifacts")
    public ProjectViews.Artifact importArtifact(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id,
                                                @Valid @RequestBody ProjectRequests.ExistingArtifact input) {
        return artifacts.importArtifact(principal.accountId(), id, input);
    }

    @GetMapping("/{id}/versions/{versionId}/sources")
    public ProjectViews.Sources sources(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id,
                                        @PathVariable long versionId) {
        return artifacts.readSources(principal.accountId(), id, versionId);
    }

    @PostMapping("/{id}/jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ProjectViews.Job enqueue(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id,
                                    @Valid @RequestBody ProjectRequests.Generate input) {
        return jobs.enqueue(principal.accountId(), id, input);
    }
}
