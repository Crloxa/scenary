-- Scenary MVP V1 · MySQL 8.4 · 引擎InnoDB · utf8mb4 · 全量 DDL 见 docs/01 §6
CREATE TABLE users (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username      VARCHAR(32)  NOT NULL COMMENT '登录名 [a-zA-Z0-9_]{4,20}',
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt',
  nickname      VARCHAR(32)  NOT NULL COMMENT '展示昵称，注册默认=username',
  avatar_url    VARCHAR(512) NULL,
  bio           VARCHAR(200) NOT NULL DEFAULT '' COMMENT '个性签名',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB COMMENT='用户';

CREATE TABLE notes (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  title        VARCHAR(64)  NOT NULL DEFAULT '',
  content      VARCHAR(2000) NOT NULL DEFAULT '',
  cover_url    VARCHAR(512) NULL COMMENT '第一张图缩略图，瀑布流用',
  media_count  INT          NOT NULL DEFAULT 0,
  place_name   VARCHAR(128) NULL COMMENT '自由文本地点(思绪二期挂坐标)',
  visibility   TINYINT      NOT NULL DEFAULT 1 COMMENT '1公开 0私密 2已删除(软删)',
  like_count   INT          NOT NULL DEFAULT 0 COMMENT '预留字段,MVP不动',
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at   DATETIME(3)  NULL,
  PRIMARY KEY (id),
  KEY idx_user_visibility_id (user_id, visibility, id),
  KEY idx_feed_cursor (visibility, id),
  CONSTRAINT fk_note_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB COMMENT='笔记';

CREATE TABLE media (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id         BIGINT UNSIGNED NOT NULL,
  note_id         BIGINT UNSIGNED NULL COMMENT '发布时回填绑定',
  order_no        TINYINT      NOT NULL DEFAULT 0 COMMENT '笔记内图片序号1..9',
  bucket          VARCHAR(64)  NOT NULL DEFAULT 'scenary-media',
  object_key      VARCHAR(256) NOT NULL COMMENT '原图 MinIO key',
  url             VARCHAR(512) NOT NULL COMMENT '原图直链',
  thumb_object_key VARCHAR(256) NULL,
  thumb_url       VARCHAR(512) NULL,
  mime            VARCHAR(64)  NOT NULL,
  size_bytes      BIGINT       NOT NULL DEFAULT 0,
  width           INT          NULL,
  height          INT          NULL,
  status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0处理中 1完成 2失败',
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_note (note_id),
  KEY idx_user_status (user_id, status),
  CONSTRAINT fk_media_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_media_note FOREIGN KEY (note_id) REFERENCES notes(id)
) ENGINE=InnoDB COMMENT='媒体文件';
