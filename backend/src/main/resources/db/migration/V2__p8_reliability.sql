ALTER TABLE notes
  ADD COLUMN request_key VARCHAR(64) NULL COMMENT '客户端幂等键' AFTER visibility,
  ADD UNIQUE KEY uk_note_user_request_key (user_id, request_key),
  ADD CONSTRAINT chk_notes_visibility CHECK (visibility IN (0, 1, 2));

ALTER TABLE media
  ADD COLUMN failure_reason VARCHAR(255) NULL COMMENT '消费者失败原因' AFTER status,
  ADD COLUMN failed_at DATETIME(3) NULL COMMENT '最终失败时间' AFTER failure_reason,
  ADD UNIQUE KEY uk_media_note_order (note_id, order_no),
  ADD CONSTRAINT chk_media_status CHECK (status IN (0, 1, 2)),
  ADD CONSTRAINT chk_media_order_no CHECK (order_no BETWEEN 0 AND 9),
  ADD CONSTRAINT chk_media_failure_pair CHECK (status <> 2 OR (failure_reason IS NOT NULL AND failed_at IS NOT NULL));
