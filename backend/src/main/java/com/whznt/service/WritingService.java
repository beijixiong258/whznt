package com.whznt.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whznt.dto.WorkspaceRequests;
import com.whznt.dto.WritingInput;
import com.whznt.entity.Draft;
import com.whznt.entity.GenerationRun;
import com.whznt.exception.WorkspaceException;
import com.whznt.integration.DeepSeekModel;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class WritingService {

    private static final int MAX_CONTENT = 30000;
    private static final Pattern TITLE = Pattern.compile("^#\\s+\\S");
    private static final Pattern TALK_SECTIONS = Pattern.compile(
            "(?ms)^##[ \\t]*开头[ \\t]*\\R\\s*\\S.*?^##[ \\t]*正文[ \\t]*\\R\\s*\\S.*?^##[ \\t]*收尾[ \\t]*\\R\\s*\\S");
    private static final String FENCE = "\u0060\u0060\u0060";

    private final DeepSeekModel model;
    private final PromptCatalog prompts;
    private final GenerationRunService runs;
    private final DraftService drafts;
    private final ObjectMapper json;

    public WorkspaceViews.ModelStatus status() {
        return model.status();
    }

    public Flux<ServerSentEvent<WorkspaceViews.GenerationEvent>> generate(long ownerId, WorkspaceRequests.Generate request) {
        model.requireAvailable();
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
            Flux<ServerSentEvent<WorkspaceViews.GenerationEvent>> deltas = model.stream(messages, tokenBudget, 0.65)
                    .map(token -> {
                        append(text, token);
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
                        answer = validateDraft(collect(budget.revision(messages, answer, words), tokenBudget, 0.55),
                                WritingInput.Format.TALK);
                        if (!budget.contains(ScriptBudget.countWords(answer))) {
                            throw WorkspaceException.unavailable("生成稿未达到所选时长，请重试或调整时长。");
                        }
                    }
                }
                return event("done", runs.complete(work, answer, model.model()));
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

    public WorkspaceViews.TopicIdeas ideas(WritingInput input) {
        String answer = collect(prompts.ideas(WritingInput.normalized(input)), 1800, 0.8);
        try {
            WorkspaceViews.TopicIdeas payload = json.readValue(stripFence(answer, "json"), WorkspaceViews.TopicIdeas.class);
            return validateIdeas(payload);
        } catch (JsonProcessingException exception) {
            throw invalidIdeas();
        }
    }

    public WorkspaceViews.RewriteResult rewrite(long ownerId, WorkspaceRequests.Rewrite request) {
        model.requireAvailable();
        Draft draft = drafts.requireOwned(ownerId, request.draftId());
        drafts.requireCurrent(draft.getConversationId(), request.baseVersionId());
        if (request.action() == WorkspaceRequests.RewriteAction.TONE
                && (request.tone() == null || request.tone().isBlank())) {
            throw WorkspaceException.badRequest("请输入希望调整成的语气。");
        }
        String answer = collect(prompts.rewrite(request), 4096, 0.45);
        String selection = request.selection();
        String leading = selection.substring(0, selection.length() - selection.stripLeading().length());
        String trailing = selection.substring(selection.stripTrailing().length());
        return new WorkspaceViews.RewriteResult(leading + answer + trailing);
    }

    private String collect(List<Message> messages, int maxTokens, double temperature) {
        try {
            StringBuilder response = model.stream(messages, maxTokens, temperature)
                    .reduceWith(StringBuilder::new, WritingService::append)
                    .block(Duration.ofSeconds(90));
            return usableContent(Objects.toString(response, ""));
        } catch (WorkspaceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new WorkspaceException(HttpStatus.BAD_GATEWAY, userMessage(exception));
        }
    }

    private static String validateDraft(String response, WritingInput.Format format) {
        String content = stripFence(response, "markdown");
        if (!TITLE.matcher(content).find()
                || (format == WritingInput.Format.TALK && !TALK_SECTIONS.matcher(content).find())
                || content.lines().skip(1).allMatch(String::isBlank)) {
            throw new WorkspaceException(HttpStatus.BAD_GATEWAY, "模型返回的稿件缺少标题或正文，请重新生成。");
        }
        return content;
    }

    private static WorkspaceViews.TopicIdeas validateIdeas(WorkspaceViews.TopicIdeas payload) {
        if (payload == null || payload.ideas() == null || payload.ideas().size() != 3) {
            throw invalidIdeas();
        }
        List<WorkspaceViews.TopicIdea> ideas = new ArrayList<>(3);
        for (WorkspaceViews.TopicIdea idea : payload.ideas()) {
            if (idea == null || !field(idea.title(), 300) || !field(idea.angle(), 500)
                    || !field(idea.audience(), 120) || !field(idea.takeaway(), 500)) {
                throw invalidIdeas();
            }
            ideas.add(new WorkspaceViews.TopicIdea(idea.title().strip(), idea.angle().strip(),
                    idea.audience().strip(), idea.takeaway().strip()));
        }
        if (ideas.stream().map(idea -> idea.title().toLowerCase(Locale.ROOT)).distinct().count() != 3) {
            throw invalidIdeas();
        }
        return new WorkspaceViews.TopicIdeas(ideas);
    }

    private static boolean field(String value, int limit) {
        return value != null && !value.isBlank() && value.strip().length() <= limit;
    }

    private static WorkspaceException invalidIdeas() {
        return new WorkspaceException(HttpStatus.BAD_GATEWAY, "模型未返回三个完整选题，请再试一次。");
    }

    private static String usableContent(String answer) {
        if (answer.isBlank()) {
            throw new WorkspaceException(HttpStatus.BAD_GATEWAY, "模型没有返回内容，请稍后重试。");
        }
        return answer.strip();
    }

    private static String stripFence(String response, String language) {
        if (response.startsWith(FENCE) && response.endsWith(FENCE)) {
            int newline = response.indexOf('\n');
            if (newline > 0 && newline < response.length() - FENCE.length()) {
                String label = response.substring(FENCE.length(), newline).strip();
                if (label.isEmpty() || language.equalsIgnoreCase(label)) {
                    return response.substring(newline + 1, response.length() - FENCE.length()).strip();
                }
            }
        }
        return response;
    }

    private static StringBuilder append(StringBuilder text, String token) {
        if (text.length() + token.length() > MAX_CONTENT) {
            throw WorkspaceException.badRequest("生成内容太长，请缩短写作要求后重试。");
        }
        return text.append(token);
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
