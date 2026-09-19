-- P18 配套（docs/05 §14）：审核隐藏态 visibility=3 的取值放行。
-- V1 的 chk_notes_visibility 限定 (0,1,2)；0=私密 1=公开 2=软删 3=举报达阈值隐藏。
ALTER TABLE notes DROP CHECK chk_notes_visibility;
ALTER TABLE notes ADD CONSTRAINT chk_notes_visibility CHECK (visibility IN (0, 1, 2, 3));
