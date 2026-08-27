package com.scenary.common;

/**
 * 统一响应包络，契约见 docs/02 §1.1。
 */
public class Result<T> {

    private int code;
    private String message;
    private T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    public static Result<Void> ok() {
        return new Result<>(0, "ok", null);
    }

    public static <T> Result<T> fail(ErrorCode ec) {
        return new Result<>(ec.getCode(), ec.getDefaultMessage(), null);
    }

    public static <T> Result<T> fail(ErrorCode ec, String message) {
        return new Result<>(ec.getCode(), message, null);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
