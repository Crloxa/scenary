package com.scenary.common;

/**
 * 业务异常：service 层只抛它，HTTP 状态与响应体由全局异常处理器按 ErrorCode 统一表达。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /** message 允许覆盖默认文案（如 42001 需带剩余秒数），code 与 HTTP 状态仍取枚举 */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
