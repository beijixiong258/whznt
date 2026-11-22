package com.whznt.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whznt.dto.AdminRequests;
import com.whznt.exception.WorkspaceException;
import com.whznt.vo.AdminViews;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TextModel {

    private static final String BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-flash";
    private final ObjectMapper json;
    private final Path settingsFile;
    private volatile Connection active;

    public TextModel(ObjectMapper json, PrivateFiles files,
            @Value("${spring.ai.openai.base-url:}") String baseUrl,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model:}") String model) {
        this.json = json;
        settingsFile = files.upstreamSettingsFile();
        Configuration fallback = new Configuration(normalizedUrl(baseUrl.isBlank() ? BASE_URL : baseUrl),
                model.isBlank() ? DEFAULT_MODEL : model.strip(), apiKey.strip());
        active = connect(readSettings(fallback));
    }

    public AdminViews.Upstream settings() {
        Configuration config = active.configuration();
        return new AdminViews.Upstream(config.baseUrl(), config.model(), !config.apiKey().isBlank());
    }

    public synchronized AdminViews.Upstream save(AdminRequests.Upstream input) {
        Connection next = connect(resolve(input));
        try {
            Files.createDirectories(settingsFile.getParent());
            Path pending = Files.createTempFile(settingsFile.getParent(), "ai-upstream-", ".tmp");
            try {
                json.writeValue(pending.toFile(), next.configuration());
                Files.move(pending, settingsFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(pending);
            }
        } catch (IOException exception) {
            throw WorkspaceException.unavailable("无法保存上游设置，请检查后端存储。");
        }
        active = next;
        return settings();
    }

    public AdminViews.Probe probe(AdminRequests.Upstream input) {
        Client candidate = connect(resolve(input)).client();
        long start = System.nanoTime();
        AtomicLong firstReply = new AtomicLong(-1);
        boolean connected;
        try {
            candidate.stream(List.of(new UserMessage("请只回复一句简短的问候。")), 96, 0.2)
                    .filter(part -> !part.isBlank())
                    .doOnNext(part -> firstReply.compareAndSet(-1, elapsedMs(start)))
                    .timeout(Duration.ofSeconds(10))
                    .then().block(Duration.ofSeconds(15));
            connected = firstReply.get() >= 0;
        } catch (RuntimeException exception) {
            connected = false;
        }
        Long firstReplyMs = firstReply.get() >= 0 ? firstReply.get() : null;
        return new AdminViews.Probe(connected, firstReplyMs, elapsedMs(start),
                connected ? "已收到模型回复。" : "测试未收到完整回复，请检查上游地址、模型名称和 API Key。");
    }

    public Client ready() {
        Client client = active.client();
        if (client == null) throw WorkspaceException.unavailable("暂时无法生成，请稍后再试。");
        return client;
    }

    private Configuration readSettings(Configuration fallback) {
        if (!Files.isRegularFile(settingsFile)) return fallback;
        try {
            return json.readValue(settingsFile.toFile(), Configuration.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read saved AI upstream settings");
        }
    }

    private Configuration resolve(AdminRequests.Upstream input) {
        String baseUrl = normalizedUrl(input.baseUrl());
        String apiKey = Objects.toString(input.apiKey(), "").strip();
        Configuration current = active.configuration();
        if (apiKey.isBlank() && baseUrl.equals(current.baseUrl())) apiKey = current.apiKey();
        if (apiKey.isBlank()) throw WorkspaceException.badRequest("请输入这个上游的 API Key。");
        return new Configuration(baseUrl, input.model().strip(), apiKey);
    }

    private static String normalizedUrl(String input) {
        String address = input.strip().replaceFirst("/+$", "");
        URI uri;
        try {
            uri = URI.create(address);
        } catch (IllegalArgumentException exception) {
            throw WorkspaceException.badRequest("请输入有效的 AI 上游地址。");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getPath().endsWith("/chat/completions")) {
            throw WorkspaceException.badRequest("请填写 OpenAI 兼容的基础地址，例如 https://api.deepseek.com/v1。");
        }
        return address;
    }

    private static Connection connect(Configuration config) {
        if (config.apiKey().isBlank()) return new Connection(config, null);
        OpenAiApi api = OpenAiApi.builder().baseUrl(config.baseUrl()).completionsPath("/chat/completions")
                .apiKey(config.apiKey()).build();
        boolean deepSeek = "api.deepseek.com".equalsIgnoreCase(URI.create(config.baseUrl()).getHost());
        return new Connection(config, new Client(config.model(), OpenAiChatModel.builder().openAiApi(api).build(), deepSeek));
    }

    private static long elapsedMs(long start) {
        return Duration.ofNanos(System.nanoTime() - start).toMillis();
    }

    private record Configuration(String baseUrl, String model, String apiKey) {
    }

    private record Connection(Configuration configuration, Client client) {
    }

    public record Client(String model, OpenAiChatModel chat, boolean deepSeek) {
        public Flux<String> stream(List<Message> messages, int maxTokens, double temperature) {
            var options = OpenAiChatOptions.builder().model(model).maxTokens(maxTokens).temperature(temperature);
            if (deepSeek) options.extraBody(Map.of("thinking", Map.of("type", "disabled")));
            return Flux.defer(() -> chat.stream(new Prompt(messages, options.build())))
                    .<String>handle((response, sink) -> {
                        var generation = response.getResult();
                        if (generation == null) return;
                        String reason = generation.getMetadata().getFinishReason();
                        if ("length".equalsIgnoreCase(reason) || "content_filter".equalsIgnoreCase(reason)) {
                            sink.error(WorkspaceException.unavailable("模型未能返回完整内容，请调整输入后重试。"));
                            return;
                        }
                        String token = generation.getOutput().getText();
                        if (token != null && !token.isEmpty()) sink.next(token);
                    })
                    .timeout(Duration.ofSeconds(80));
        }
    }
}
