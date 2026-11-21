package com.whznt.controller;

import com.whznt.config.AccountPrincipal;
import com.whznt.dto.SubtitleRequests;
import com.whznt.service.SubtitleService;
import com.whznt.vo.SubtitleViews;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/subtitles")
@RequiredArgsConstructor
public class SubtitleController {

    private static final MediaType MP4 = MediaType.valueOf("video/mp4");
    private final SubtitleService subtitles;

    @GetMapping
    public List<SubtitleViews.Summary> list(@AuthenticationPrincipal AccountPrincipal account) {
        return subtitles.tasks(account.accountId());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubtitleViews.Detail create(@AuthenticationPrincipal AccountPrincipal account,
                                       @Valid @ModelAttribute SubtitleRequests.Create request) {
        return subtitles.create(account.accountId(), request);
    }

    @GetMapping("/{id}")
    public SubtitleViews.Detail detail(@AuthenticationPrincipal AccountPrincipal account, @PathVariable long id) {
        return subtitles.read(account.accountId(), id);
    }

    @PostMapping("/{id}/retry")
    public SubtitleViews.Detail retry(@AuthenticationPrincipal AccountPrincipal account, @PathVariable long id) {
        return subtitles.retry(account.accountId(), id);
    }

    @PutMapping("/{id}/timeline")
    public SubtitleViews.Detail edit(@AuthenticationPrincipal AccountPrincipal account, @PathVariable long id,
                                     @Valid @RequestBody SubtitleRequests.Edit request) {
        return subtitles.edit(account.accountId(), id, request);
    }

    @PostMapping("/{id}/export")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubtitleViews.Detail export(@AuthenticationPrincipal AccountPrincipal account, @PathVariable long id,
                                       @Valid @RequestBody SubtitleRequests.Export request) {
        return subtitles.exportVideo(account.accountId(), id, request.version());
    }

    @GetMapping("/{id}/video")
    public ResponseEntity<Resource> video(@AuthenticationPrincipal AccountPrincipal account, @PathVariable long id) {
        return ResponseEntity.ok().contentType(MP4).cacheControl(CacheControl.noStore())
                .body(subtitles.video(account.accountId(), id));
    }

    @GetMapping("/{id}/subtitle.srt")
    public ResponseEntity<String> srt(@AuthenticationPrincipal AccountPrincipal account, @PathVariable long id,
                                      @RequestParam @Positive int version) {
        var file = subtitles.downloadSrt(account.accountId(), id, version);
        return ResponseEntity.ok().contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION, attachment(file.filename()))
                .body(file.content());
    }

    @GetMapping("/{id}/export.mp4")
    public ResponseEntity<Resource> mp4(@AuthenticationPrincipal AccountPrincipal account, @PathVariable long id,
                                        @RequestParam @Positive int version) {
        var file = subtitles.downloadVideo(account.accountId(), id, version);
        return ResponseEntity.ok().contentType(MP4).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(file.filename())).body(file.resource());
    }

    private static String attachment(String name) {
        return ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString();
    }
}
