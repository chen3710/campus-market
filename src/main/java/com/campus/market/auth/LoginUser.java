package com.campus.market.auth;

/**
 * 当前登录用户的轻量视图，只承载鉴权所需的最小信息，不包含密码等敏感字段。
 */
public record LoginUser(Long id, String username, String role) {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }
}
