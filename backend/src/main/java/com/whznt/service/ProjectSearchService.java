package com.whznt.service;

import com.whznt.dto.ProjectGeneration;
import com.whznt.entity.ProjectMaterial;
import com.whznt.exception.WorkspaceException;
import com.whznt.integration.QmdIndex;
import com.whznt.vo.ProjectViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectSearchService {

    private final PersonProjectService projects;
    private final ProjectMaterialService materials;
    private final QmdIndex qmd;

    public List<ProjectViews.Passage> search(long ownerId, long projectId, String query) {
        projects.requireOwned(ownerId, projectId);
        String phrase = query.strip();
        if (phrase.isBlank()) {
            throw WorkspaceException.badRequest("请输入要查找的关键词。");
        }
        List<ProjectViews.Passage> passages = materials.passages(projectId, materials.currentVersionIds(projectId));
        Set<Long> ranked = new LinkedHashSet<>(qmd.search(projectId, passages, phrase, 16));
        String lowered = phrase.toLowerCase(Locale.ROOT);
        passages.stream()
                .filter(part -> part.content().toLowerCase(Locale.ROOT).contains(lowered)
                        || part.material().toLowerCase(Locale.ROOT).contains(lowered))
                .map(ProjectViews.Passage::id).forEach(ranked::add);
        return materials.resolve(projectId, ranked.stream().limit(16).toList());
    }

    public List<Long> select(ProjectGeneration.Plan plan) {
        List<ProjectViews.Passage> passages = materials.passages(plan.projectId(), plan.materialVersionIds());
        List<ProjectViews.Passage> facts = passages.stream().filter(part -> part.kind() != ProjectMaterial.Kind.STYLE)
                .toList();
        String query = plan.topic().isBlank() ? plan.person() + " " + plan.step().label() : plan.topic();
        if (!plan.instructions().isBlank()) {
            query += " " + plan.instructions().lines().findFirst().orElse("");
        }
        Set<Long> selected = new LinkedHashSet<>(qmd.search(plan.projectId(), facts, query, 8));
        List<Long> versions = facts.stream().map(ProjectViews.Passage::versionId).distinct().toList();
        for (long version : versions) {
            facts.stream().filter(part -> part.versionId() == version).findFirst()
                    .map(ProjectViews.Passage::id).ifPresent(selected::add);
        }
        facts.stream().map(ProjectViews.Passage::id).forEach(selected::add);
        List<Long> result = new ArrayList<>(selected.stream().limit(12).toList());
        if (plan.step().usesWritingStyle()) {
            passages.stream().filter(part -> part.kind() == ProjectMaterial.Kind.STYLE)
                    .map(ProjectViews.Passage::id).limit(2).forEach(result::add);
        }
        return result;
    }
}
