package com.whznt.controller;

import com.whznt.dto.WorkspaceRequests;
import com.whznt.dto.WritingInput;
import com.whznt.service.WritingService;
import com.whznt.config.AccountPrincipal;
import com.whznt.vo.WorkspaceViews;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/writing")
@RequiredArgsConstructor
public class WritingController {

    private final WritingService writing;

    @GetMapping("/model-status")
    public WorkspaceViews.ModelStatus modelStatus() {
        return writing.status();
    }

    @PostMapping("/ideas")
    public WorkspaceViews.TopicIdeas ideas(@Valid @RequestBody WritingInput input) {
        return writing.ideas(input);
    }

    @PostMapping("/rewrite")
    public WorkspaceViews.RewriteResult rewrite(@AuthenticationPrincipal AccountPrincipal principal,
                                                 @Valid @RequestBody WorkspaceRequests.Rewrite request) {
        return writing.rewrite(principal.accountId(), request);
    }

    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<WorkspaceViews.GenerationEvent>> generate(
            @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody WorkspaceRequests.Generate request) {
        return writing.generate(principal.accountId(), request);
    }
}
