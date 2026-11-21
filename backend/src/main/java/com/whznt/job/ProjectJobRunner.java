package com.whznt.job;

import com.whznt.entity.ProjectJob;
import com.whznt.exception.WorkspaceException;
import com.whznt.service.ProjectJobService;
import com.whznt.workflow.PersonCreationWorkflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectJobRunner {

    private final ProjectJobService jobs;
    private final PersonCreationWorkflow workflow;
    private volatile boolean ready;

    @EventListener(ApplicationReadyEvent.class)
    void recoverPendingJobs() {
        jobs.recover();
        ready = true;
    }

    @Scheduled(fixedDelay = 1000)
    void runPendingJob() {
        if (!ready) {
            return;
        }
        ProjectJob job = jobs.claimNext();
        if (job == null) {
            return;
        }
        try {
            workflow.run(job);
        } catch (Exception exception) {
            log.warn("Project generation {} failed ({})", job.getId(), exception.getClass().getSimpleName());
            jobs.fail(job.getId(), userMessage(exception));
        }
    }

    private static String userMessage(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof WorkspaceException workspace) {
                return workspace.getMessage();
            }
        }
        return "生成没有完成，输入及已保存成果均已保留，可以重试。";
    }
}
