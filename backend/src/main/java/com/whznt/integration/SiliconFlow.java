package com.whznt.integration;

import com.whznt.exception.WorkspaceException;
import com.whznt.vo.WorkspaceViews;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class SiliconFlow {

    private static final String BASE_URL = "https://api.siliconflow.cn";
    private static final String DEFAULT_MODEL = "deepseek-ai/DeepSeek-V4-Flash";
    private static final Set<String> BASE_URLS = Set.of(BASE_URL, BASE_URL + "/", BASE_URL + "/v1",
            BASE_URL + "/v1/");

    private final OpenAiChatModel client;
    private final String model;
    private final String unavailableReason;

    public SiliconFlow(
            @Value("${spring.ai.openai.base-url:}") String baseUrl,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model:}") String model) {
        this.model = model.isBlank() ? DEFAULT_MODEL : model.strip();
        if (apiKey.isBlank()) {
            this.client = null;
            this.unavailableReason = "模型服务尚未配置。配置 API Key 后即可使用 AI 写作和对话。";
        } else if ((!baseUrl.isBlank() && !BASE_URLS.contains(baseUrl.strip()))
                || !supportedModel(this.model)) {
            this.client = null;
            this.unavailableReason = "模型配置有误，请检查硅基流动地址和模型名称。";
        } else {
            OpenAiApi api = OpenAiApi.builder().baseUrl(BASE_URL).apiKey(apiKey.strip()).build();
            this.client = OpenAiChatModel.builder().openAiApi(api).build();
            this.unavailableReason = null;
        }
    }

    public WorkspaceViews.ModelStatus status() {
        return new WorkspaceViews.ModelStatus(client != null, unavailableReason);
    }

    public String model() {
        return model;
    }

    public void requireAvailable() {
        if (client == null) {
            throw WorkspaceException.unavailable(unavailableReason);
        }
    }

    public Flux<String> stream(List<Message> messages, int maxTokens, double temperature) {
        requireAvailable();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .extraBody(Map.of("enable_thinking", false))
                .build();
        return Flux.defer(() -> client.stream(new Prompt(messages, options)))
                .<String>handle((response, sink) -> {
                    var generation = response.getResult();
                    if (generation == null) {
                        return;
                    }
                    String reason = generation.getMetadata().getFinishReason();
                    if ("length".equalsIgnoreCase(reason) || "content_filter".equalsIgnoreCase(reason)) {
                        sink.error(WorkspaceException.unavailable("模型未能返回完整内容，请调整输入后重试。"));
                        return;
                    }
                    String token = generation.getOutput().getText();
                    if (token != null && !token.isEmpty()) {
                        sink.next(token);
                    }
                })
                .timeout(Duration.ofSeconds(80));
    }

    private static boolean supportedModel(String value) {
        return value.toLowerCase(Locale.ROOT).matches("(?:pro/)?(?:deepseek-ai/deepseek[^/]+|qwen/qwen[^/]+)");
    }
}
