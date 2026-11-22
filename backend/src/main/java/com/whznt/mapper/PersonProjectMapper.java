package com.whznt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whznt.entity.PersonProject;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface PersonProjectMapper extends BaseMapper<PersonProject> {

    @Select("""
            SELECT id, owner_id, name, summary, updated_at
            FROM person_project WHERE id = #{id} AND owner_id = #{ownerId} AND deleted_at IS NULL
            FOR UPDATE
            """)
    PersonProject lockOwned(@Param("id") long id, @Param("ownerId") long ownerId);

    @Update("""
            UPDATE person_project SET deleted_at = coalesce(deleted_at, now())
            WHERE id = #{id} AND owner_id = #{ownerId}
            """)
    int markDeleted(@Param("id") long id, @Param("ownerId") long ownerId);
}
