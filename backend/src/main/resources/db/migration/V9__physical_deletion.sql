ALTER TABLE person_project
    DROP CONSTRAINT person_project_owner_id_fkey,
    ADD CONSTRAINT person_project_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES account(id) ON DELETE CASCADE;

ALTER TABLE conversation
    DROP CONSTRAINT conversation_owner_id_fkey,
    DROP CONSTRAINT conversation_project_id_fkey,
    ADD CONSTRAINT conversation_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES account(id) ON DELETE CASCADE,
    ADD CONSTRAINT conversation_project_id_fkey FOREIGN KEY (project_id) REFERENCES person_project(id) ON DELETE CASCADE;

ALTER TABLE chat_message
    DROP CONSTRAINT chat_message_conversation_id_fkey,
    ADD CONSTRAINT chat_message_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE;

ALTER TABLE draft
    DROP CONSTRAINT draft_conversation_id_fkey,
    DROP CONSTRAINT draft_current_version_fk,
    ADD CONSTRAINT draft_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
    ADD CONSTRAINT draft_current_version_fk FOREIGN KEY (current_version_id) REFERENCES draft_version(id) ON DELETE SET NULL;

ALTER TABLE draft_version
    DROP CONSTRAINT draft_version_draft_id_fkey,
    DROP CONSTRAINT draft_version_base_version_id_fkey,
    ADD CONSTRAINT draft_version_draft_id_fkey FOREIGN KEY (draft_id) REFERENCES draft(id) ON DELETE CASCADE,
    ADD CONSTRAINT draft_version_base_version_id_fkey FOREIGN KEY (base_version_id) REFERENCES draft_version(id) ON DELETE SET NULL;

ALTER TABLE generation_run
    DROP CONSTRAINT generation_run_owner_id_fkey,
    DROP CONSTRAINT generation_run_conversation_id_fkey,
    DROP CONSTRAINT generation_run_version_id_fkey,
    ADD CONSTRAINT generation_run_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES account(id) ON DELETE CASCADE,
    ADD CONSTRAINT generation_run_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
    ADD CONSTRAINT generation_run_version_id_fkey FOREIGN KEY (version_id) REFERENCES draft_version(id) ON DELETE CASCADE;

ALTER TABLE project_material
    DROP CONSTRAINT project_material_project_id_fkey,
    DROP CONSTRAINT material_current_version_fk,
    ADD CONSTRAINT project_material_project_id_fkey FOREIGN KEY (project_id) REFERENCES person_project(id) ON DELETE CASCADE,
    ADD CONSTRAINT material_current_version_fk FOREIGN KEY (current_version_id) REFERENCES material_version(id) ON DELETE SET NULL;

ALTER TABLE material_version
    DROP CONSTRAINT material_version_material_id_fkey,
    ADD CONSTRAINT material_version_material_id_fkey FOREIGN KEY (material_id) REFERENCES project_material(id) ON DELETE CASCADE;

ALTER TABLE material_passage
    DROP CONSTRAINT material_passage_version_id_fkey,
    ADD CONSTRAINT material_passage_version_id_fkey FOREIGN KEY (version_id) REFERENCES material_version(id) ON DELETE CASCADE;

ALTER TABLE project_artifact
    DROP CONSTRAINT project_artifact_project_id_fkey,
    DROP CONSTRAINT project_artifact_conversation_id_fkey,
    ADD CONSTRAINT project_artifact_project_id_fkey FOREIGN KEY (project_id) REFERENCES person_project(id) ON DELETE CASCADE,
    ADD CONSTRAINT project_artifact_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE;

ALTER TABLE project_version_context
    DROP CONSTRAINT project_version_context_id_fkey,
    DROP CONSTRAINT project_version_context_project_id_fkey,
    ADD CONSTRAINT project_version_context_id_fkey FOREIGN KEY (id) REFERENCES draft_version(id) ON DELETE CASCADE,
    ADD CONSTRAINT project_version_context_project_id_fkey FOREIGN KEY (project_id) REFERENCES person_project(id) ON DELETE CASCADE;

ALTER TABLE project_job
    DROP CONSTRAINT project_job_project_id_fkey,
    DROP CONSTRAINT project_job_conversation_id_fkey,
    DROP CONSTRAINT project_job_result_version_id_fkey,
    ADD CONSTRAINT project_job_project_id_fkey FOREIGN KEY (project_id) REFERENCES person_project(id) ON DELETE CASCADE,
    ADD CONSTRAINT project_job_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
    ADD CONSTRAINT project_job_result_version_id_fkey FOREIGN KEY (result_version_id) REFERENCES draft_version(id) ON DELETE CASCADE;

ALTER TABLE subtitle_task
    DROP CONSTRAINT subtitle_task_owner_id_fkey,
    DROP CONSTRAINT subtitle_task_draft_version_id_fkey,
    ADD CONSTRAINT subtitle_task_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES account(id) ON DELETE CASCADE,
    ADD CONSTRAINT subtitle_task_draft_version_id_fkey FOREIGN KEY (draft_version_id) REFERENCES draft_version(id) ON DELETE SET NULL;

ALTER TABLE recharge_order
    DROP CONSTRAINT recharge_order_owner_id_fkey,
    ADD CONSTRAINT recharge_order_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES account(id) ON DELETE CASCADE;

ALTER TABLE point_credit
    DROP CONSTRAINT point_credit_account_id_fkey,
    DROP CONSTRAINT point_credit_recharge_order_id_fkey,
    ADD CONSTRAINT point_credit_account_id_fkey FOREIGN KEY (account_id) REFERENCES account(id) ON DELETE CASCADE,
    ADD CONSTRAINT point_credit_recharge_order_id_fkey FOREIGN KEY (recharge_order_id) REFERENCES recharge_order(id) ON DELETE CASCADE;

CREATE FUNCTION clear_project_job_checkpoint(job_thread text) RETURNS boolean LANGUAGE plpgsql AS $$
BEGIN
    IF to_regclass('lg4jthread') IS NULL THEN
        RETURN false;
    END IF;

    DELETE FROM lg4jcheckpoint WHERE thread_id IN (
        SELECT thread_id FROM lg4jthread WHERE thread_name = job_thread
        UNION SELECT thread_id FROM lg4jthreadtag WHERE thread_name = job_thread
    );
    DELETE FROM lg4jthreadtag WHERE thread_name = job_thread;
    DELETE FROM lg4jthread WHERE thread_name = job_thread;
    RETURN true;
END;
$$;

CREATE FUNCTION clear_deleted_project_job_checkpoint() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM clear_project_job_checkpoint(OLD.project_id::text || '/' || OLD.request_key);
    RETURN OLD;
END;
$$;

CREATE TRIGGER project_job_delete_checkpoint AFTER DELETE ON project_job
    FOR EACH ROW EXECUTE FUNCTION clear_deleted_project_job_checkpoint();

DELETE FROM person_project WHERE deleted_at IS NOT NULL;
DELETE FROM conversation WHERE deleted_at IS NOT NULL;

ALTER TABLE person_project DROP COLUMN deleted_at;
ALTER TABLE conversation DROP COLUMN deleted_at;
