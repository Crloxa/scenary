-- E5 地图浏览（docs/05 §15）：视野框范围查询的坐标复合索引。
-- 仅加索引，不动数据语义；bbox 走 latitude 区间 + longitude 过滤。
ALTER TABLE notes
    ADD INDEX idx_notes_latlng (latitude, longitude);
