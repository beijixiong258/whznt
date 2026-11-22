package com.whznt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whznt.entity.Conversation;
import com.whznt.vo.WorkspaceViews.HistoryItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

public interface ConversationMapper extends BaseMapper<Conversation> {

    String OWNED_CONVERSATION = """
            SELECT c.id, c.owner_id, c.project_id, c.kind, c.title, c.updated_at, c.deleted_at
            FROM conversation c
            LEFT JOIN person_project p ON p.id = c.project_id
            WHERE c.id = #{id} AND c.owner_id = #{ownerId} AND p.deleted_at IS NULL
            """;

    @Select("""
            WITH matching AS (
                SELECT c.id, c.kind, c.title, c.updated_at, c.project_id, d.id AS draft_id, v.content AS draft_content,
                       CASE
                           WHEN c.kind = 'CHAT' THEN '通用 AI'
                           WHEN c.project_id IS NOT NULL THEN '人物故事与定位'
                           WHEN input.value IS NULL THEN '手动写稿'
                           ELSE coalesce(substring(input.value ->> 'angle' FROM '^以([^。]+)的身份完成本次写作。'),
                                         CASE WHEN input.value ->> 'format' = 'ARTICLE' THEN '公众号编辑' ELSE '短视频编导' END)
                       END AS assistant
                FROM conversation c
                LEFT JOIN person_project project ON project.id = c.project_id
                LEFT JOIN draft d ON d.conversation_id = c.id
                LEFT JOIN draft_version v ON v.id = d.current_version_id
                CROSS JOIN LATERAL (
                    SELECT CASE WHEN v.writing_input IS JSON OBJECT THEN v.writing_input::json END AS value
                ) input
                WHERE c.owner_id = #{ownerId} AND c.deleted_at IS NULL AND project.deleted_at IS NULL
                  AND strpos(lower(c.title), lower(#{query})) > 0
                  AND c.updated_at >= coalesce(#{since}, '-infinity'::timestamptz)
            ), page AS (
                SELECT * FROM matching
                WHERE #{assistant} = '' OR assistant = #{assistant}
                ORDER BY updated_at DESC, id DESC
                LIMIT #{limit} OFFSET #{offset}
            )
            SELECT p.id, p.kind, p.title, p.updated_at, p.project_id, p.assistant,
                   CASE WHEN p.kind = 'CHAT' THEN (SELECT count(*) FROM chat_message WHERE conversation_id = p.id)
                        ELSE (SELECT count(*) FROM draft_version WHERE draft_id = p.draft_id) END AS entry_count,
                   CASE WHEN p.kind = 'CHAT' THEN (SELECT left(content, 180) FROM chat_message WHERE conversation_id = p.id
                                                  ORDER BY id DESC LIMIT 1)
                        ELSE left(p.draft_content, 180) END AS preview
            FROM page p
            ORDER BY p.updated_at DESC, p.id DESC
            """)
    List<HistoryItem> findHistory(
            @Param("ownerId") long ownerId,
            @Param("query") String query,
            @Param("assistant") String assistant,
            @Param("since") OffsetDateTime since,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Select(OWNED_CONVERSATION)
    Conversation findOwned(@Param("id") long id, @Param("ownerId") long ownerId);

    @Select(OWNED_CONVERSATION + "FOR UPDATE OF c")
    Conversation lockOwned(@Param("id") long id, @Param("ownerId") long ownerId);
}
