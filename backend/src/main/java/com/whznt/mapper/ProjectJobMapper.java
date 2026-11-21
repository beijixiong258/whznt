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

    @Select("SELECT * FROM project_job WHERE id = #{id} FOR UPDATE")
    ProjectJob lock(long id);
}
