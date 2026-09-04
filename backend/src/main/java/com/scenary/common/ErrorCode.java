package com.scenary.common;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码与 HTTP 状态映射，逐条对应 docs/02 §1.2 错误码表；新增错误先改契约再改此处。
 */
public enum ErrorCode {

    SUCCESS(0, HttpStatus.OK, "ok"),
    VALIDATION(40000, HttpStatus.BAD_REQUEST, "参数校验失败"),
    UNAUTHORIZED(40100, HttpStatus.UNAUTHORIZED, "未登录或缺少令牌"),
    TOKEN_INVALID(40101, HttpStatus.UNAUTHORIZED, "令牌无效或已过期"),
    FORBIDDEN(40300, HttpStatus.FORBIDDEN, "无权操作该资源"),
    ACCOUNT_DISABLED(40301, HttpStatus.FORBIDDEN, "账号已被禁用"),
    NOT_FOUND(40400, HttpStatus.NOT_FOUND, "资源不存在"),
    MEDIA_NOT_READY(40901, HttpStatus.CONFLICT, "媒体仍在处理中，请稍后重试"),
    UPLOAD_SESSION_EXPIRED(40902, HttpStatus.CONFLICT, "上传会话已过期，请重新选择视频"),
    UPLOAD_INCOMPLETE(40903, HttpStatus.CONFLICT, "上传分片不完整，请继续上传"),
    USERNAME_EXISTS(41001, HttpStatus.UNPROCESSABLE_ENTITY, "用户名已存在"),
    TOO_MANY_REQUESTS(42001, HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),
    INTERNAL_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "服务开小差了");

    private final int code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
