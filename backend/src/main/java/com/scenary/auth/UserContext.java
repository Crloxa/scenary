package com.scenary.auth;

import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;

/**
 * 当前登录用户载体：AuthInterceptor 写入，业务层读取，请求结束必须清理。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId) {
        USER_ID.set(userId);
    }

    /** 受保护路径内保证非空；拿不到说明拦截链被绕过，按缺陷处理而非静默 */
    public static long require() {
        Long id = USER_ID.get();
        if (id == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return id;
    }

    public static void clear() {
        USER_ID.remove();
    }
}
