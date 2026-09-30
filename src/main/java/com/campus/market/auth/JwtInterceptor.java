package com.campus.market.auth;

import com.campus.market.common.BusinessException;
import com.campus.market.common.ResultCode;
import com.campus.market.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 登录拦截器。
 * <p>
 * 设计要点：
 * 1. 按注解驱动，而不是按 URL 前缀硬编码白名单 —— 新增接口时不会因为漏配路径而裸奔。
 * 2. 校验顺序是「先认证、再鉴权」：先解析出用户身份，再判断角色是否满足要求。
 * 3. 用户信息写入 ThreadLocal 后，务必在 afterCompletion 中清理，否则线程池复用会串号。
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final AppProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        boolean needLogin = hasAnnotation(handlerMethod, LoginRequired.class);
        boolean needAdmin = hasAnnotation(handlerMethod, AdminOnly.class);
        String token = resolveToken(request);

        if (!needLogin && !needAdmin) {
            // 「可选登录」：公开接口如果带了有效 Token，就顺手把身份放进上下文，
            // 这样商品列表才能显示当前用户的收藏状态；
            // 没带 Token 或 Token 已失效则静默忽略，不影响匿名访问。
            if (StringUtils.hasText(token)) {
                try {
                    UserContext.set(jwtUtil.parse(token));
                } catch (BusinessException ignored) {
                    // 匿名访问，无需处理
                }
            }
            return true;
        }

        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }

        LoginUser loginUser = jwtUtil.parse(token);

        if (needAdmin && !loginUser.isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该操作仅管理员可用");
        }

        UserContext.set(loginUser);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean hasAnnotation(HandlerMethod handlerMethod, Class<? extends java.lang.annotation.Annotation> type) {
        return handlerMethod.hasMethodAnnotation(type)
                || handlerMethod.getBeanType().isAnnotationPresent(type);
    }

    /**
     * 支持两种携带方式：标准 Authorization: Bearer xxx，以及自定义头。
     */
    private String resolveToken(HttpServletRequest request) {
        String headerName = properties.jwt().header();
        String raw = request.getHeader(headerName);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String prefix = properties.jwt().prefix();
        if (StringUtils.hasText(prefix) && raw.startsWith(prefix)) {
            return raw.substring(prefix.length()).trim();
        }
        return raw.trim();
    }
}
