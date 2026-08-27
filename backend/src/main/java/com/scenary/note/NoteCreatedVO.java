package com.scenary.note;

/**
 * 发布成功响应（docs/02 §5.1）。
 */
public record NoteCreatedVO(long id, String coverUrl) {
}
