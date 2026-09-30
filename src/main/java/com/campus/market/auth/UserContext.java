package com.campus.market.auth;

import com.campus.market.common.BusinessException;
import com.campus.market.common.ResultCode;

/**
 * 基于 ThreadLocal 的登录用户上下文。
 * <p>
 * 拦截器在 preHandle 中写入、在 afterCompletion 中清理，
 * 业务代码通过 {@link #requireUserId()} 直接拿到当前用户，避免层层传参。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long requireUserId() {
        return require().id();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
