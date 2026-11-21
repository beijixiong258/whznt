package com.whznt.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whznt.dto.ProjectGeneration;
import com.whznt.entity.DraftVersion;
import com.whznt.entity.ProjectMaterial;
import com.whznt.entity.ProjectStep;
import com.whznt.exception.WorkspaceException;
import com.whznt.integration.DeepSeekModel;
import com.whznt.vo.ProjectViews;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectPromptService {

    private static final Pattern SENTENCE_BREAK = Pattern.compile("[。！？!?；;\\r\\n]+");
    private static final Map<ProjectStep, String> PROMPTS = Map.of(
            ProjectStep.TRAITS, PromptCatalog.read("提炼人物特质"),
            ProjectStep.STORY, PromptCatalog.read("写人物故事"),
            ProjectStep.POSITION, PromptCatalog.read("做IP定位"),
            ProjectStep.TOPICS, PromptCatalog.read("出人物选题"),
            ProjectStep.SCRIPT, PromptCatalog.read("人物口播"));
    private static final String PRINCIPLES = PromptCatalog.read("人物创作原则");
    private static final String STORY_REVIEW = PromptCatalog.read("复核人物故事");

    private final ProjectArtifactService artifacts;
    private final DeepSeekModel model;
    private final ObjectMapper json;

    public String write(ProjectGeneration.Plan plan, List<ProjectViews.Passage> passages) {
        Set<String> allowedIds = passages.stream().filter(part -> part.kind() != ProjectMaterial.Kind.STYLE)
                .map(part -> String.valueOf(part.id())).collect(Collectors.toSet());
        List<Map<String, Object>> upstream = plan.upstreamVersionIds().stream().map(id -> {
            ProjectArtifactService.ArtifactVersion source = artifacts.requireVersion(plan.projectId(), id);
            DraftVersion version = source.version();
            String content = ScriptBudget.CITATION.matcher(version.getContent())
                    .replaceAll(mark -> allowedIds.contains(mark.group(1)) ? mark.group() : "");
            String origin = version.getSource() == DraftVersion.Source.GENERATED ? "生成稿"
                    : version.getBaseVersionId() == null ? "手动录入稿" : "用户编辑稿";
            return Map.<String, Object>of("成果", source.step().label(),
                    "版本", id, "来源", origin, "正文", content);
        }).toList();
        List<Map<String, Object>> facts = passages.stream().filter(part -> part.kind() != ProjectMaterial.Kind.STYLE)
                .map(part -> Map.<String, Object>of("编号", part.id(), "类型", materialLabel(part.kind()),
                        "原文", part.content())).toList();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("人物", plan.person());
        input.put("人物简介", plan.summary());
        input.put("选题", plan.topic());
        input.put("补充要求", plan.instructions());
        input.put("已有稿件", upstream);
        input.put("事实片段", facts);
        if (plan.step().usesWritingStyle()) {
            input.put("表达特征", styleNotes(passages));
        }
        String prompt = PRINCIPLES + "\n\n" + PROMPTS.get(plan.step());
        ScriptBudget budget = plan.step() == ProjectStep.SCRIPT ? ScriptBudget.forSeconds(plan.durationSeconds()) : null;
        if (budget != null) {
            input.put("口播规格", budget.promptFields());
        }
        List<Message> messages = List.of(new SystemMessage(prompt), new UserMessage(encode(input)));
        String answer = budget != null
                ? writeScript(messages, budget)
                : generate(messages, plan.step() == ProjectStep.STORY ? 8600 : 3400);
        if (plan.step() == ProjectStep.STORY) {
            answer = reviewStory(plan, facts, upstream, answer);
        }
        if (plan.step() == ProjectStep.TOPICS && TopicTitles.parse(answer).size() != 10) {
            throw WorkspaceException.unavailable("模型返回的选题数量不完整，请重新生成。");
        }
        if (ScriptBudget.CITATION.matcher(answer).results().anyMatch(mark -> !allowedIds.contains(mark.group(1)))) {
            throw WorkspaceException.unavailable("模型返回的资料编号无对应原文，请重新生成。");
        }
        return answer;
    }

    private String reviewStory(ProjectGeneration.Plan plan, List<Map<String, Object>> facts,
                               List<Map<String, Object>> upstream, String draft) {
        List<Map<String, Object>> supplied = upstream.stream().filter(row -> "手动录入稿".equals(row.get("来源"))).toList();
        Map<String, Object> evidence = Map.of("人物", plan.person(), "人物简介", plan.summary(),
                "事实片段", facts, "手动录入稿", supplied, "待核稿", draft);
        String reviewed = generate(List.of(new SystemMessage(STORY_REVIEW), new UserMessage(encode(evidence))), 8600);
        List<String> originals = new ArrayList<>();
        originals.add(plan.summary());
        facts.forEach(row -> originals.add((String) row.get("原文")));
        supplied.forEach(row -> originals.add((String) row.get("正文")));
        String story = StoryGrounding.retainSupportedClaims(reviewed, originals);
        boolean hasBody = story.lines().map(String::stripLeading)
                .anyMatch(line -> !line.startsWith("#") && ScriptBudget.countWords(line) > 0);
        if (!hasBody) {
            throw WorkspaceException.unavailable("复核后没有可保留的故事正文，请补充资料后重试。");
        }
        return story;
    }

    private String writeScript(List<Message> messages, ScriptBudget budget) {
        int tokens = budget.tokenLimit();
        String answer = generate(messages, tokens);
        int words = ScriptBudget.countWords(answer);
        if (!budget.contains(words)) {
            log.info("Rewriting a {}-second script with {} spoken words (expected {}-{})",
                    budget.seconds(), words, budget.minimum(), budget.maximum());
            answer = generate(budget.revision(messages, answer, words), tokens);
            words = ScriptBudget.countWords(answer);
            if (!budget.contains(words)) {
                log.warn("A revised {}-second script has {} spoken words (expected {}-{})",
                        budget.seconds(), words, budget.minimum(), budget.maximum());
                throw WorkspaceException.unavailable("生成稿未达到所选时长，请重新生成或调整时长。");
            }
        }
        return answer;
    }

    private String generate(List<Message> messages, int tokens) {
        String answer = GeneratedContent.collect(model.stream(messages, tokens, 0.62), Duration.ofMinutes(4)).strip();
        if (answer.isEmpty()) {
            throw WorkspaceException.unavailable("模型没有返回可保存的成果，请重试。");
        }
        return answer;
    }

    private static List<String> styleNotes(List<ProjectViews.Passage> passages) {
        String examples = passages.stream().filter(part -> part.kind() == ProjectMaterial.Kind.STYLE)
                .map(ProjectViews.Passage::content).collect(Collectors.joining("\n"));
        if (examples.isBlank()) {
            return List.of();
        }
        double sentenceLength = SENTENCE_BREAK.splitAsStream(examples).filter(part -> !part.isBlank())
                .mapToInt(ScriptBudget::countWords).average().orElse(0);
        String sentence = sentenceLength <= 20 ? "以短句为主，每句说清一个意思。"
                : "允许完整的叙述句，穿插短句形成停顿。";
        long questions = examples.codePoints().filter(mark -> mark == '？' || mark == '?').count();
        String address = questions > 0 ? "可用一两个具体问句带动叙述。"
                : "以陈述推进，少用连续反问。";
        double lineLength = examples.lines().filter(line -> !line.isBlank())
                .mapToInt(ScriptBudget::countWords).average().orElse(0);
        String paragraph = lineLength <= 38 ? "段落短，换行紧跟意思变化。"
                : "围绕同一场景连贯成段，转折时再分段。";
        return List.of(sentence, address, paragraph);
    }

    private String encode(Map<String, Object> data) {
        try {
            return json.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to construct a project prompt", exception);
        }
    }

    private static String materialLabel(ProjectMaterial.Kind kind) {
        return switch (kind) {
            case LIFE -> "人生访谈";
            case BUSINESS -> "商业访谈";
            case OBSERVATION -> "现场观察";
            case STYLE -> "风格样稿";
        };
    }

}
