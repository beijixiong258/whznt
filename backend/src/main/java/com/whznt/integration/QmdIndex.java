package com.whznt.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whznt.vo.ProjectViews;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class QmdIndex {

    private static final Pattern PASSAGE_FILE = Pattern.compile("(?:^|[/\\\\])p(\\d+)\\.md$");
    private final PrivateFiles files;
    private final ObjectMapper json;
    private final String node;
    private final Path cli;

    public QmdIndex(PrivateFiles files, ObjectMapper json, @Value("${whznt.qmd.node:node}") String node,
                    @Value("${whznt.qmd.cli:}") String path) {
        this.files = files;
        this.json = json;
        this.node = node;
        Path local = Path.of("qmd/node_modules/@tobilu/qmd/bin/qmd");
        if (!Files.isRegularFile(local)) {
            local = Path.of("backend").resolve(local);
        }
        this.cli = (path.isBlank() ? local : Path.of(path)).toAbsolutePath().normalize();
    }

    public synchronized List<Long> search(long projectId, List<ProjectViews.Passage> passages, String words, int limit) {
        if (passages.isEmpty() || words.isBlank() || !Files.isRegularFile(cli)) {
            return List.of();
        }
        Path projectDirectory = files.qmdDirectory().resolve("project-" + projectId);
        try {
            Files.createDirectories(projectDirectory);
            Map<String, String> environment = Map.of("QMD_CONFIG_DIR", projectDirectory.toString(),
                    "INDEX_PATH", projectDirectory.resolve("index.sqlite").toString(),
                    "LLAMA_LOG_LEVEL", "error");
            updateIndex(projectDirectory, environment, passages);
            ServerParameters server = ServerParameters.builder(node).args(cli.toString(), "mcp")
                    .env(environment).build();
            StdioClientTransport transport = new StdioClientTransport(server, new JacksonMcpJsonMapper(json));
            try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build()) {
                client.initialize();
                Map<String, Object> arguments = Map.of(
                        "searches", List.of(Map.of("type", "lex", "query", words)),
                        "collections", List.of("materials"),
                        "limit", Math.min(100, Math.max(limit * 3, 20)),
                        "rerank", false);
                McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest.builder()
                        .name("query").arguments(arguments).build());
                if (Boolean.TRUE.equals(result.isError())) {
                    throw new IOException("QMD query was rejected");
                }
                Set<Long> allowed = passages.stream().map(ProjectViews.Passage::id).collect(Collectors.toSet());
                List<Long> ids = new ArrayList<>();
                for (JsonNode row : json.valueToTree(result.structuredContent()).path("results")) {
                    var name = PASSAGE_FILE.matcher(row.path("file").asText());
                    if (name.find()) {
                        long id = Long.parseLong(name.group(1));
                        if (allowed.contains(id) && ids.size() < limit && !ids.contains(id)) {
                            ids.add(id);
                        }
                    }
                }
                log.info("QMD searched project {} and matched {} passages", projectId, ids.size());
                return ids;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception exception) {
            log.warn("QMD search failed for project {} ({})", projectId, exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private void updateIndex(Path root, Map<String, String> env, List<ProjectViews.Passage> passages)
            throws IOException, InterruptedException {
        Path documents = root.resolve("documents");
        Path pending = root.resolve("pending");
        Path config = root.resolve("index.yml");
        Files.createDirectories(documents);
        for (ProjectViews.Passage passage : passages) {
            Path entry = documents.resolve("p" + passage.id() + ".md");
            if (!Files.isRegularFile(entry)) {
                Files.writeString(pending, "");
                Files.writeString(entry, "# " + passage.material().replace('\n', ' ') + "\n\n" + passage.content(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            }
        }
        if (!Files.isRegularFile(config)) {
            run(root, env, List.of("collection", "add", documents.toString(), "--name", "materials"));
        } else if (Files.exists(pending)) {
            run(root, env, List.of("update"));
        }
        Files.deleteIfExists(pending);
    }

    private void run(Path directory, Map<String, String> env, List<String> args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(node, cli.toString()));
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().putAll(env);
        builder.environment().remove("SPRING_AI_OPENAI_API_KEY");
        Process process = builder.start();
        try {
            if (!process.waitFor(25, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IOException("QMD index update failed");
            }
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroy);
                process.destroyForcibly();
            }
        }
    }
}
