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
        try {
            if (recoverBeforeClaim) {
                subtitles.recover();
            }
            SubtitleTask task = subtitles.claimNext();
            if (task != null) {
                run(task);
            }
            recoverBeforeClaim = false;
        } catch (RuntimeException exception) {
            recoverBeforeClaim = true;
            log.warn("Unable to update the subtitle queue ({}). Recovery will be retried.",
                    exception.getClass().getSimpleName());
        }
    }

    private void run(SubtitleTask task) {
        try {
            subtitles.perform(task);
        } catch (Exception exception) {
            log.warn("Subtitle task {} failed ({})", task.getId(), exception.getClass().getSimpleName());
            subtitles.fail(task, exception instanceof WorkspaceException expected
                    ? expected.getMessage() : "任务未完成，视频和已保存的字幕仍可使用，请重试。");
        }
    }
}
