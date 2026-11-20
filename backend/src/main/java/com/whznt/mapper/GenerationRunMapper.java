package com.whznt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whznt.entity.GenerationRun;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface GenerationRunMapper extends BaseMapper<GenerationRun> {

    @Insert("""
            INSERT INTO generation_run (owner_id, request_key, kind, state, input_snapshot)
            VALUES (#{ownerId}, #{requestKey}, #{kind}, #{state}, #{inputSnapshot})
            ON CONFLICT (owner_id, request_key) DO NOTHING
            """)
    int reserve(GenerationRun run);

    @Select("""
            SELECT id, owner_id, request_key, conversation_id, kind,
                   state, input_snapshot, version_id, started_at
            FROM generation_run WHERE id = #{id} FOR UPDATE
            """)
    GenerationRun lockById(long id);

    @Update("""
            UPDATE generation_run SET state = 'FAILED'
            WHERE conversation_id = #{conversationId} AND state = 'RUNNING'
              AND started_at < now() - interval '5 minutes'
            """)
    void expireAbandoned(long conversationId);
}
