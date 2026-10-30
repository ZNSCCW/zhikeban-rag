package com.zhikeban.bishe.web;

/**
 * 业务异常（任务①~③ 通用）。
 *
 * <p>★ 只承载"给调用方看的消息 + HTTP 状态"，由 {@link ApiExceptionHandler} 统一转成 JSON，
 * 避免在每个控制器里写 try/catch。
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    /** 400：入参不合法。 */
    public static ApiException badRequest(String message) {
        return new ApiException(400, message);
    }

    /** 401：未认证 / 令牌无效。 */
    public static ApiException unauthorized(String message) {
        return new ApiException(401, message);
    }

    /** 403：已认证但无权做这件事（例如采纳别人的回答）。 */
    public static ApiException forbidden(String message) {
        return new ApiException(403, message);
    }

    /** 404：目标不存在。 */
    public static ApiException notFound(String message) {
        return new ApiException(404, message);
    }

    /** 409：与既有状态冲突（例如用户名已占用、重复采纳）。 */
    public static ApiException conflict(String message) {
        return new ApiException(409, message);
    }
}
