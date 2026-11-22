package com.whznt.workflow;

import com.whznt.dto.ProjectGeneration;
import com.whznt.entity.ProjectJob;
import com.whznt.service.ProjectJobService;
import com.whznt.service.ProjectMaterialService;
import com.whznt.service.ProjectPromptService;
import com.whznt.service.ProjectSearchService;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.PostgresSaverV2;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
public class PersonCreationWorkflow {

    private final ProjectJobService jobs;
    private final ProjectSearchService search;
    private final ProjectMaterialService materials;
    private final ProjectPromptService prompts;
    private final CompiledGraph<AgentState> graph;

    public PersonCreationWorkflow(ProjectJobService jobs, ProjectSearchService search, ProjectMaterialService materials,
                                  ProjectPromptService prompts, PostgresSaverV2 saver,
                                  ObjectStreamStateSerializer<AgentState> serializer) throws GraphStateException {
        this.jobs = jobs;
        this.search = search;
        this.materials = materials;
        this.prompts = prompts;
        this.graph = new StateGraph<>(serializer)
                .addNode("sources", node_async(this::collectSources))
                .addNode("compose", node_async(this::compose))
                .addNode("save", node_async(this::save))
                .addEdge(START, "sources")
                .addEdge("sources", "compose")
                .addEdge("compose", "save")
                .addEdge("save", END)
                .compile(CompileConfig.builder().checkpointSaver(saver).releaseThread(false).build());
    }

    public void run(ProjectJob job) {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(job.getProjectId() + "/" + job.getRequestKey()).build();
        GraphInput input = graph.stateOf(config).isPresent() ? GraphInput.resume()
                : GraphInput.args(Map.of("jobId", job.getId()));
        graph.invoke(input, config).orElseThrow();
    }

    private Map<String, Object> collectSources(AgentState state) {
        return Map.of("passages", new ArrayList<>(search.select(plan(state))));
    }

    private Map<String, Object> compose(AgentState state) {
        ProjectGeneration.Plan plan = plan(state);
        ProjectGeneration.Result result = prompts.write(plan, materials.resolve(plan.projectId(), passageIds(state)));
        return Map.of("content", result.content(), "model", result.model());
    }

    private Map<String, Object> save(AgentState state) {
        String content = state.<String>value("content").orElseThrow();
        String modelId = state.<String>value("model").orElseThrow();
        jobs.complete(jobId(state), plan(state), content, passageIds(state), modelId);
        return Map.of("saved", true);
    }

    private ProjectGeneration.Plan plan(AgentState state) {
        return jobs.planOf(jobs.getById(jobId(state)));
    }

    private static long jobId(AgentState state) {
        return state.<Number>value("jobId").orElseThrow().longValue();
    }

    private static List<Long> passageIds(AgentState state) {
        return state.<List<Long>>value("passages").orElseThrow();
    }
}
