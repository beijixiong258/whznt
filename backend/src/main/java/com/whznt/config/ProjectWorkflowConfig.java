package com.whznt.config;

import org.bsc.langgraph4j.checkpoint.PostgresSaverV2;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

@Configuration
@EnableScheduling
public class ProjectWorkflowConfig {

    @Bean
    ObjectStreamStateSerializer<AgentState> projectStateSerializer() {
        return new ObjectStreamStateSerializer<>(AgentState::new);
    }

    @Bean
    @DependsOnDatabaseInitialization
    PostgresSaverV2 projectCheckpointSaver(DataSource dataSource, ObjectStreamStateSerializer<AgentState> serializer)
            throws Exception {
        var builder = PostgresSaverV2.builder();
        builder.datasource(dataSource);
        builder.stateSerializer(serializer);
        builder.createTables(true);
        return builder.build();
    }
}
