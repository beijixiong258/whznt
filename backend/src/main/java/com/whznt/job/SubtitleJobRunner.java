package com.whznt.job;

import com.whznt.entity.SubtitleTask;
import com.whznt.exception.WorkspaceException;
import com.whznt.service.SubtitleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubtitleJobRunner {

    private final SubtitleService subtitles;
    private volatile boolean ready;
    private boolean recoverBeforeClaim;

    @EventListener(ApplicationReadyEvent.class)
    void recoverPendingJobs() {
        subtitles.recover();
        ready = true;
    }

    @Scheduled(fixedDelay = 1000)
    void runPendingJob() {
        if (!ready) {
            return;
        }
        if (recoverBeforeClaim) {
            subtitles.recover();
            recoverBeforeClaim = false;
        }
        SubtitleTask task = subtitles.claimNext();
        if (task == null) {
            return;
        }
        try {
            subtitles.perform(task);
        } catch (Exception exception) {
            log.warn("Subtitle task {} failed ({})", task.getId(), exception.getClass().getSimpleName());
            try {
                subtitles.fail(task, exception instanceof WorkspaceException expected
                        ? expected.getMessage() : "任务未完成，视频和已保存的字幕仍可使用，请重试。");
            } catch (RuntimeException persistenceFailure) {
                recoverBeforeClaim = true;
                log.warn("Unable to persist subtitle task status. Recovery will be retried.");
            }
        }
    }
}
