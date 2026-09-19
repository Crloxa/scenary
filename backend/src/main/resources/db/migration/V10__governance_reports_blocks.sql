-- P18 社区治理最小闭环（docs/05 §14）：举报与屏蔽
-- 举报去重：同一用户对同一目标只记一次（MySQL 组合唯一键对 NULL 列不冲突，
-- 评论举报的 target_note_id 为 NULL 时 uk_report_note 不参与判重，反之亦然）
CREATE TABLE reports (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT UNSIGNED NOT NULL,
    target_note_id BIGINT UNSIGNED NULL,
    target_comment_id BIGINT UNSIGNED NULL,
    reason_code VARCHAR(16) NOT NULL,
    reason_text VARCHAR(200) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_note (reporter_id, target_note_id),
    UNIQUE KEY uk_report_comment (reporter_id, target_comment_id),
    KEY idx_report_note_created (target_note_id, created_at),
    KEY idx_report_comment_created (target_comment_id, created_at),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES users(id),
    CONSTRAINT fk_report_note FOREIGN KEY (target_note_id) REFERENCES notes(id),
    CONSTRAINT fk_report_comment FOREIGN KEY (target_comment_id) REFERENCES comments(id)
) ENGINE=InnoDB;

-- 屏蔽为单向关系；可见性过滤按双向生效（屏蔽者不看被屏蔽者，被屏蔽者也不看屏蔽者）
CREATE TABLE user_blocks (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    blocked_user_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_block_pair (user_id, blocked_user_id),
    KEY idx_block_blocked (blocked_user_id),
    CONSTRAINT ck_no_self_block CHECK (user_id <> blocked_user_id),
    CONSTRAINT fk_block_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_block_blocked FOREIGN KEY (blocked_user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- 笔记举报计数；达阈值（scenary.moderation.report-hide-threshold，默认 5）自动隐藏
ALTER TABLE notes ADD COLUMN report_count INT UNSIGNED NOT NULL DEFAULT 0;
