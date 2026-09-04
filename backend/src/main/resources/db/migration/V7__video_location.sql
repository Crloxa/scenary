-- P12：视频媒体状态/播放产物与笔记地点坐标扩展。
ALTER TABLE media
  ADD COLUMN media_type TINYINT NOT NULL DEFAULT 0 COMMENT '0图片 1视频' AFTER mime,
  ADD COLUMN duration_ms BIGINT NULL COMMENT '视频时长毫秒' AFTER height,
  ADD COLUMN playback_object_key VARCHAR(256) NULL COMMENT '720p转码对象' AFTER duration_ms,
  ADD COLUMN playback_url VARCHAR(512) NULL COMMENT '720p播放地址' AFTER playback_object_key,
  ADD COLUMN playback_low_object_key VARCHAR(256) NULL COMMENT '480p转码对象' AFTER playback_url,
  ADD COLUMN playback_low_url VARCHAR(512) NULL COMMENT '480p播放地址' AFTER playback_low_object_key,
  ADD COLUMN exif_latitude DECIMAL(10,6) NULL COMMENT '服务端只读EXIF候选纬度' AFTER playback_low_url,
  ADD COLUMN exif_longitude DECIMAL(10,6) NULL COMMENT '服务端只读EXIF候选经度' AFTER exif_latitude,
  ADD KEY idx_media_type_status (media_type, status, id),
  ADD CONSTRAINT chk_media_type CHECK (media_type IN (0, 1));

ALTER TABLE media DROP CHECK chk_media_status;

ALTER TABLE media
  ADD CONSTRAINT chk_media_status CHECK (
    (media_type = 0 AND status IN (0, 1, 2))
    OR (media_type = 1 AND status IN (10, 11, 12, 13, 14))
  );

ALTER TABLE notes
  ADD COLUMN latitude DECIMAL(10,6) NULL COMMENT '明确分享的纬度或服务端EXIF候选值' AFTER place_name,
  ADD COLUMN longitude DECIMAL(10,6) NULL COMMENT '明确分享的经度或服务端EXIF候选值' AFTER latitude,
  ADD COLUMN place_source VARCHAR(16) NULL COMMENT 'MANUAL/EXIF/MAP' AFTER longitude,
  ADD COLUMN place_precision VARCHAR(32) NULL COMMENT '地点精度' AFTER place_source,
  ADD CONSTRAINT chk_notes_coordinates CHECK (
    (latitude IS NULL AND longitude IS NULL)
    OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
  ),
  ADD CONSTRAINT chk_notes_place_source CHECK (
    place_source IS NULL OR place_source IN ('MANUAL', 'EXIF', 'MAP')
  );
