package com.scenary.report;

/**
 * 举报结果（docs/02 §10.1，P18）：created=false 表示同用户同目标重复举报（幂等返回）；
 * hidden=true 表示本次举报触发阈值，笔记已自动隐藏。
 */
public record ReportCreatedVO(boolean created, boolean hidden) {
}
