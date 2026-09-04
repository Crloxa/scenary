-- P9：点赞、收藏、关注关系表。唯一键保证写操作幂等，关系事实不依赖 notes.like_count。
CREATE TABLE note_likes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  note_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_note_like_user (note_id, user_id),
  KEY idx_like_user_created (user_id, created_at),
  CONSTRAINT fk_like_note FOREIGN KEY (note_id) REFERENCES notes(id),
  CONSTRAINT fk_like_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB COMMENT='笔记点赞关系';

CREATE TABLE note_bookmarks (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  note_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_note_bookmark_user (note_id, user_id),
  KEY idx_bookmark_user_created (user_id, created_at),
  CONSTRAINT fk_bookmark_note FOREIGN KEY (note_id) REFERENCES notes(id),
  CONSTRAINT fk_bookmark_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB COMMENT='笔记收藏关系';

CREATE TABLE follows (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  follower_id BIGINT UNSIGNED NOT NULL,
  following_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_follow_pair (follower_id, following_id),
  KEY idx_following_created (following_id, created_at),
  KEY idx_follower_created (follower_id, created_at),
  CONSTRAINT fk_follow_follower FOREIGN KEY (follower_id) REFERENCES users(id),
  CONSTRAINT fk_follow_following FOREIGN KEY (following_id) REFERENCES users(id),
  CONSTRAINT ck_no_self_follow CHECK (follower_id <> following_id)
) ENGINE=InnoDB COMMENT='用户关注关系';
