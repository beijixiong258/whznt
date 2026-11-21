package com.whznt.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whznt.dto.WorkspaceRequests;
import com.whznt.dto.WritingInput;
import com.whznt.entity.ChatMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class PromptCatalog {

    private final String principles = read("写作原则");
    private final String topicToDraft = read("从零写稿");
    private final String talk = read("口播稿");
    private final String article = read("图文稿");
    private final String ideas = read("三个选题");
    private final String rewrite = read("选段改写");
    private final String chat = read("自由对话");
    private final ObjectMapper json;

    public PromptCatalog(ObjectMapper json) {
        this.json = json;
    }

    public List<Message> write(WritingInput input) {
        String format = input.format() == WritingInput.Format.ARTICLE ? article : talk;
        return pair(topicToDraft + "\n\n" + format, writingParameters(input));
    }

    public List<Message> ideas(WritingInput input) {
        return pair(ideas, writingParameters(input));
    }

    public List<Message> rewrite(WorkspaceRequests.Rewrite request) {
        String action = switch (request.action()) {
            case POLISH -> "润色";
            case SHORTEN -> "缩写";
            case TONE -> "换语气";
        };
        return pair(rewrite, Map.of(
                "操作", action,
                "目标语气", Objects.toString(request.tone(), ""),
                "前文", Objects.toString(request.before(), ""),
                "选中内容", request.selection(),
                "后文", Objects.toString(request.after(), "")));
    }

    public List<Message> chat(List<ChatMessage> history, String input) {
        List<Message> messages = new ArrayList<>(history.size() + 2);
        messages.add(new SystemMessage(principles + "\n\n" + chat));
        for (ChatMessage row : history) {
            messages.add(row.getRole() == ChatMessage.Role.USER
                    ? new UserMessage(row.getContent()) : new AssistantMessage(row.getContent()));
        }
        messages.add(new UserMessage(input));
        return messages;
    }

    private List<Message> pair(String task, Map<String, ?> parameters) {
        try {
            return List.of(new SystemMessage(principles + "\n\n" + task),
                    new UserMessage(json.writeValueAsString(parameters)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to construct the writing prompt", exception);
        }
    }

    private static Map<String, ?> writingParameters(WritingInput input) {
        boolean article = input.format() == WritingInput.Format.ARTICLE;
        Map<String, Object> parameters = new LinkedHashMap<>(Map.of(
                "主题", input.topic(),
                "切入角度", input.angle(),
                "受众", input.audience(),
                "语气", input.tone(),
                "文体", article ? "图文稿" : "短视频口播稿",
                "时长（秒）", article ? "" : input.durationSeconds(),
                "参考资料", input.background()));
        if (!article) {
            parameters.put("口播规格", ScriptBudget.forSeconds(input.durationSeconds()).promptFields());
        }
        return parameters;
    }

    static String read(String name) {
        try {
            return new ClassPathResource("prompts/" + name + ".md").getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load prompt " + name, exception);
        }
    }
}
