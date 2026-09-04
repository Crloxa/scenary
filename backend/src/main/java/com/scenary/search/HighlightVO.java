package com.scenary.search;

/** 纯文本搜索摘录；不含 HTML，前端按文本渲染。 */
public record HighlightVO(String title, String content, String placeName, String author) {
}
