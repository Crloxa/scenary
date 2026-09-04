-- P12-E1：视频分片上传会话与分片对象索引。
CREATE TABLE video_upload_sessions (
  upload_id          CHAR(36) NOT NULL,
  user_id            BIGINT UNSIGNED NOT NULL,
  original_name      VARCHAR(255) NOT NULL,
  declared_mime      VARCHAR(64) NULL,
  size_bytes         BIGINT UNSIGNED NOT NULL,
  total_parts        INT UNSIGNED NOT NULL,
  status             TINYINT NOT NULL DEFAULT 0 COMMENT '0上传中 1合并中 2已完成 3已过期',
  completed_media_id BIGINT UNSIGNED NULL,
  expires_at         DATETIME(3) NOT NULL,
  created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (upload_id),
  KEY idx_video_upload_expiry (status, expires_at),
  CONSTRAINT fk_video_upload_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_video_upload_media FOREIGN KEY (completed_media_id) REFERENCES media(id) ON DELETE SET NULL,
  CONSTRAINT chk_video_upload_status CHECK (status IN (0, 1, 2, 3)),
  CONSTRAINT chk_video_upload_parts CHECK (total_parts BETWEEN 1 AND 64),
  CONSTRAINT chk_video_upload_size CHECK (size_bytes BETWEEN 1 AND 209715200)
) ENGINE=InnoDB COMMENT='视频分片上传会话';

CREATE TABLE video_upload_parts (
  upload_id   CHAR(36) NOT NULL,
  part_number INT UNSIGNED NOT NULL,
  object_key  VARCHAR(256) NOT NULL,
  size_bytes  BIGINT UNSIGNED NULL,
  etag        VARCHAR(128) NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (upload_id, part_number),
  CONSTRAINT fk_video_upload_part_session FOREIGN KEY (upload_id)
    REFERENCES video_upload_sessions(upload_id) ON DELETE CASCADE,
  CONSTRAINT chk_video_upload_part_number CHECK (part_number BETWEEN 1 AND 64)
) ENGINE=InnoDB COMMENT='视频分片对象';
