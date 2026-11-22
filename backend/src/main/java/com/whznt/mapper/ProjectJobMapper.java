package com.whznt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whznt.entity.ProjectJob;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProjectJobMapper extends BaseMapper<ProjectJob> {

    @Update("""
            UPDATE project_job SET state = 'RUNNING'
            WHERE id = #{id} AND state = 'QUEUED'
            """)
    int claim(long id);

    @Update("UPDATE project_job SET state = 'QUEUED' WHERE state = 'RUNNING'")
    void requeueInterrupted();

    @Update("""
            UPDATE project_job SET state = 'FAILED', error = '人物档案已删除。'
            WHERE project_id = #{projectId} AND state IN ('QUEUED', 'RUNNING')
            """)
    void cancelForDeletedProject(long projectId);

    @Select("SELECT * FROM project_job WHERE id = #{id} FOR UPDATE")
    ProjectJob lock(long id);
}
