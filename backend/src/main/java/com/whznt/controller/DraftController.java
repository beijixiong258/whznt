package com.whznt.controller;

import com.whznt.dto.WorkspaceRequests;
import com.whznt.service.DraftService;
import com.whznt.config.AccountPrincipal;
import com.whznt.vo.WorkspaceViews;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/drafts")
@RequiredArgsConstructor
public class DraftController {

    private final DraftService drafts;

    @PostMapping
    public WorkspaceViews.Manuscript save(@AuthenticationPrincipal AccountPrincipal principal,
                                          @RequestParam(required = false) Long conversationId,
                                          @Valid @RequestBody WorkspaceRequests.SaveDraft request) {
        return drafts.saveManual(principal.accountId(), conversationId, request);
    }

    @GetMapping("/{draftId}/versions/{versionId}")
    public WorkspaceViews.Version version(@AuthenticationPrincipal AccountPrincipal principal,
                                          @PathVariable long draftId, @PathVariable long versionId) {
        return drafts.readVersion(principal.accountId(), draftId, versionId);
    }

    @GetMapping("/{draftId}/versions/{versionId}/export")
    public ResponseEntity<String> export(@AuthenticationPrincipal AccountPrincipal principal,
                                         @PathVariable long draftId, @PathVariable long versionId) {
        WorkspaceViews.Version version = drafts.readVersion(principal.accountId(), draftId, versionId);
        String filename = version.version().title().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename + ".md", StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(version.content());
    }
}
