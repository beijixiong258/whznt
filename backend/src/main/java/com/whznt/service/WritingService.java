package com.whznt.service;

import com.whznt.dto.WorkspaceRequests;
import com.whznt.dto.WritingInput;
import com.whznt.entity.Draft;
import com.whznt.entity.GenerationRun;
import com.whznt.exception.WorkspaceException;
import com.whznt.integration.TextModel;
import com.whznt.vo.WorkspaceViews;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class WritingService {

    private static final Pattern TITLE = Pattern.compile("(?U)#[ \\t].*?[^#\\s]");
    private static final Pattern TALK_SECTION = Pattern.compile(
            "(?m)^ {0,3}##[ \\t]*(开头|正文|收尾)(?:[ \\t]+#+)?[ \\t]*$");
    private static final List<String> TALK_SECTIONS = List.of("开头", "正文", "收尾");
    private static final String FENCE = "\u0060\u0060\u0060";

    private final TextModel model;
    private final PromptCatalog prompts;
    private final GenerationRunService runs;
    private final DraftService drafts;

    public Flux<ServerSentEvent<WorkspaceViews.GenerationEvent>> generate(long ownerId, WorkspaceRequests.Generate request) {
        TextModel.Client client = model.ready();
        GenerationRunService.Prepared work = runs.prepare(ownerId, request);
        GenerationRun run = work.run();
        long conversationId = run.getConversationId();
        if (work.replay()) {
            return Flux.just(event("done", WorkspaceViews.GenerationEvent.done(conversationId, run.getVersionId())));
        }

        return Flux.defer(() -> {
            boolean chat = run.getKind() == GenerationRun.Kind.CHAT;
            List<Message> messages = chat
                    ? prompts.chat(work.history(), work.message()) : prompts.write(work.writingInput());
            ScriptBudget budget = !chat && work.writingInput().format() == WritingInput.Format.TALK
                    ? ScriptBudget.forSeconds(work.writingInput().durationSeconds()) : null;
            int tokenBudget = budget == null ? 4096 : budget.tokenLimit();
            StringBuilder text = new StringBuilder();
            Flux<ServerSentEvent<WorkspaceViews.GenerationEvent>> deltas = client.stream(messages, tokenBudget, 0.65)
                    .map(token -> {
                        GeneratedContent.append(text, token);
                        return event("delta", WorkspaceViews.GenerationEvent.delta(token));
                    });
            Mono<ServerSentEvent<WorkspaceViews.GenerationEvent>> complete = Mono.fromCallable(() -> {
                String answer = usableContent(text.toString());
                if (!chat) {
                    answer = validateDraft(answer, work.writingInput().format());
                }
                if (budget != null) {
                    int words = ScriptBudget.countWords(answer);
                    if (!budget.contains(words)) {
                        answer = validateDraft(collect(client, budget.revision(messages, answer, words), tokenBudget, 0.55),
                                WritingInput.Format.TALK);
                        if (!budget.contains(ScriptBudget.countWords(answer))) {
                            throw WorkspaceException.unavailable("生成稿未达到所选时长，请重试或调整时长。");
                        }
                    }
                }
                return event("done", runs.complete(work, answer, client.model()));
            }).subscribeOn(Schedulers.boundedElastic());
            return Flux.concat(Flux.just(event("start", WorkspaceViews.GenerationEvent.started(conversationId))),
                    deltas, complete);
        }).onErrorResume(error -> Mono.fromCallable(() -> {
            failQuietly(run.getId());
            return event("error", WorkspaceViews.GenerationEvent.failed(conversationId, userMessage(error)));
        }).subscribeOn(Schedulers.boundedElastic())).doFinally(signal -> {
            if (signal == SignalType.CANCEL) {
                Schedulers.boundedElastic().schedule(() -> failQuietly(run.getId()));
            }
        });
    }

    public WorkspaceViews.RewriteResult rewrite(long ownerId, WorkspaceRequests.Rewrite request) {
        TextModel.Client client = model.ready();
        Draft draft = drafts.requireOwned(ownerId, request.draftId());
        drafts.requireCurrent(draft.getConversationId(), request.baseVersionId());
        if (request.action() == WorkspaceRequests.RewriteAction.TONE
                && (request.tone() == null || request.tone().isBlank())) {
            throw WorkspaceException.badRequest("请输入希望调整成的语气。");
        }
        String answer = collect(client, prompts.rewrite(request), 4096, 0.45);
        String selection = request.selection();
        String leading = selection.substring(0, selection.length() - selection.stripLeading().length());
        String trailing = selection.substring(selection.stripTrailing().length());
        return new WorkspaceViews.RewriteResult(leading + answer + trailing);
    }

    private static String collect(TextModel.Client client, List<Message> messages, int maxTokens, double temperature) {
        try {
            String response = GeneratedContent.collect(client.stream(messages, maxTokens, temperature),
                    Duration.ofSeconds(90));
            return usableContent(response);
        } catch (WorkspaceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new WorkspaceException(HttpStatus.BAD_GATEWAY, userMessage(exception));
        }
    }

    private static String validateDraft(String response, WritingInput.Format format) {
        String content = stripFence(response);
        if (!TITLE.matcher(content).lookingAt()) {
            throw new WorkspaceException(HttpStatus.BAD_GATEWAY, "模型返回的稿件缺少标题或正文，请重新生成。");
        }
        if (format == WritingInput.Format.TALK) {
            var sections = TALK_SECTION.matcher(content);
            int start = -1;
            for (String expected : TALK_SECTIONS) {
                if (!sections.find() || !expected.equals(sections.group(1))
                        || (start >= 0 && !hasBody(content.substring(start, sections.start())))) {
                    throw invalidTalk();
                }
                start = sections.end();
            }
            if (sections.find() || !hasBody(content.substring(start))) {
                throw invalidTalk();
            }
        } else if (!hasBody(content)) {
            throw new WorkspaceException(HttpStatus.BAD_GATEWAY, "模型返回的图文稿缺少正文，请重新生成。");
        }
        return content;
    }

    private static boolean hasBody(String text) {
        return ScriptBudget.bodyText(text).codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private static WorkspaceException invalidTalk() {
        return new WorkspaceException(HttpStatus.BAD_GATEWAY, "口播稿须依次包含开头、正文和收尾，每节都要有台词。");
    }

    private static String usableContent(String answer) {
        if (answer.isBlank()) {
            throw new WorkspaceException(HttpStatus.BAD_GATEWAY, "模型没有返回内容，请稍后重试。");
        }
        return answer.strip();
    }

    private static String stripFence(String response) {
        if (response.startsWith(FENCE) && response.endsWith(FENCE)) {
            int newline = response.indexOf('\n');
            if (newline > 0 && newline < response.length() - FENCE.length()) {
                String label = response.substring(FENCE.length(), newline).strip();
                if (label.isEmpty() || "markdown".equalsIgnoreCase(label)) {
                    return response.substring(newline + 1, response.length() - FENCE.length()).strip();
                }
            }
        }
        return response;
    }

    private static ServerSentEvent<WorkspaceViews.GenerationEvent> event(String name,
                                                                      WorkspaceViews.GenerationEvent data) {
        return ServerSentEvent.builder(data).event(name).build();
    }

    private static String userMessage(Throwable error) {
        if (error instanceof WorkspaceException workspaceError) {
            return workspaceError.getMessage();
        }
        log.warn("Model request or result persistence failed ({})", error.getClass().getSimpleName());
        return "生成暂时没有完成，请稍后重试。";
    }

    private void failQuietly(long runId) {
        try {
            runs.fail(runId);
        } catch (RuntimeException error) {
            log.warn("Unable to persist failure for generation {}", runId);
        }
    }
}
