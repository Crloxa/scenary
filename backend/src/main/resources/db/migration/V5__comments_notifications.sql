-- P10 评论与通知：同库事务写入，评论 parent_id 通过复合外键限制为同一笔记。
CREATE TABLE comments (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  note_id     BIGINT UNSIGNED NOT NULL,
  user_id     BIGINT UNSIGNED NOT NULL,
  parent_id   BIGINT UNSIGNED NULL,
  content     VARCHAR(500) NOT NULL,
  status      TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 2已删除',
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at  DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_comment_id_note (id, note_id),
  KEY idx_comment_note_id (note_id, id),
  KEY idx_comment_parent_id (parent_id, id),
  CONSTRAINT fk_comment_note FOREIGN KEY (note_id) REFERENCES notes(id),
  CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_comment_parent_note FOREIGN KEY (parent_id, note_id) REFERENCES comments(id, note_id)
) ENGINE=InnoDB COMMENT='笔记评论（含回复）';

CREATE TABLE notifications (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  recipient_id BIGINT UNSIGNED NOT NULL,
  actor_id     BIGINT UNSIGNED NULL,
  type         VARCHAR(16) NOT NULL COMMENT 'LIKE/FOLLOW/COMMENT/REPLY',
  note_id      BIGINT UNSIGNED NULL,
  comment_id   BIGINT UNSIGNED NULL,
  read_at      DATETIME(3) NULL,
  created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_notification_recipient_id (recipient_id, id),
  KEY idx_notification_unread (recipient_id, read_at, id),
  CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES users(id),
  CONSTRAINT fk_notification_actor FOREIGN KEY (actor_id) REFERENCES users(id),
  CONSTRAINT fk_notification_note FOREIGN KEY (note_id) REFERENCES notes(id),
  CONSTRAINT fk_notification_comment FOREIGN KEY (comment_id) REFERENCES comments(id)
) ENGINE=InnoDB COMMENT='站内通知事件';
