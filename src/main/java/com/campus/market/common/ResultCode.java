package com.campus.market.common;

/**
 * 业务状态码。HTTP 状态码一律返回 200，业务结果由 code 字段表达，
 * 避免前端在 axios 的 catch 分支里处理业务错误。
 */
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "登录状态已失效，请重新登录"),
    FORBIDDEN(403, "没有操作权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突，请刷新后重试"),
    /**
     * 422 Unprocessable Entity：请求本身合法，但业务规则不允许执行。
     * 例如「订单状态不允许从已完成变更为已取消」「管理员账号不允许被封禁」。
     * 刻意区别于 500 —— 500 应该是「服务端出错了」，业务规则拦截不是错误，
     * 混用会让排查问题时误判严重程度。
     */
    BUSINESS_ERROR(422, "业务规则不允许该操作"),
    SYSTEM_ERROR(999, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
