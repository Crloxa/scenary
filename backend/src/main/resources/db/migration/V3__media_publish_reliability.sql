-- P8：把“对象已入库但 MQ 尚未可靠发布”从不可见日志变成可重试状态。
ALTER TABLE media
  ADD COLUMN publish_status TINYINT NOT NULL DEFAULT 1 COMMENT '0待发布 1已发布',
  ADD COLUMN publish_attempts TINYINT NOT NULL DEFAULT 0 COMMENT 'MQ 发布尝试次数',
  ADD COLUMN last_publish_at DATETIME(3) NULL COMMENT '最近一次 MQ 发布尝试时间',
  ADD CONSTRAINT chk_media_publish_status CHECK (publish_status IN (0, 1)),
  ADD CONSTRAINT chk_media_publish_attempts CHECK (publish_attempts BETWEEN 0 AND 3);

-- V1/V2 历史行已经经历过原有发布流程，默认视为已发布；新 INSERT 由 Mapper 显式写 0。
