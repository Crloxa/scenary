-- P11：搜索受控 LIKE 的可见性、时间游标、作者连接和封面回表索引。
ALTER TABLE notes ADD KEY idx_search_visibility_created_id (visibility, created_at, id);
ALTER TABLE notes ADD KEY idx_search_visibility_user_id (visibility, user_id, id);
ALTER TABLE users ADD KEY idx_search_status_id (status, id);
ALTER TABLE media ADD KEY idx_media_note_order (note_id, order_no);
