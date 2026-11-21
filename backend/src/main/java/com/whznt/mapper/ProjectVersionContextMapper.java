package com.whznt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whznt.entity.ProjectVersionContext;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface ProjectVersionContextMapper extends BaseMapper<ProjectVersionContext> {

    @Insert("""
            INSERT INTO project_version_context (id, project_id, snapshot)
            SELECT #{target}, project_id, snapshot FROM project_version_context WHERE id = #{source}
            """)
    void copy(@Param("source") long source, @Param("target") long target);
}
