package com.whznt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whznt.entity.Conversation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ConversationMapper extends BaseMapper<Conversation> {

    @Select("""
            SELECT id, owner_id, project_id, kind, title, updated_at
            FROM conversation
            WHERE owner_id = #{ownerId}
              AND strpos(lower(title), lower(#{query})) > 0
            ORDER BY updated_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Conversation> findHistory(
            @Param("ownerId") long ownerId,
            @Param("query") String query,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Select("""
            SELECT id, owner_id, project_id, kind, title, updated_at
            FROM conversation WHERE id = #{id} AND owner_id = #{ownerId}
            FOR UPDATE
            """)
    Conversation lockOwned(@Param("id") long id, @Param("ownerId") long ownerId);
}
