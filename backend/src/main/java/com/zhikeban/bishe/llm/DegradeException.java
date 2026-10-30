package com.zhikeban.bishe.llm;

/**
 * 触发降级链的专用异常（详设 §4.2 的 {@code DegradeException}）。
 *
 * <p>★ 语义：**外部依赖不可用**（本地模型未起、API 未配置 / 超时 / 限流）在基础设施层
 * **就地转译**为本异常，业务层只看到"成功 / 降级 / 失败"三种结果，不自己写重试循环。
 *
 * <p>★ 降级链第三级 = **全文检索兜底**：{@code FallbackLlmChain} 捕获本异常后，
 * 用 `material_chunk` 的关键词命中结果组织"资料中未找到依据"类回答，并把
 * {@code degraded} 标为真 —— 而**不是**把链路判失败。
 */
public class DegradeException extends RuntimeException {

    /** 降级发生在哪个阶段（详设的 {@code RagStage} 语义，此处用字符串以免 llm 包反向依赖 rag 包）。 */
    private final String stage;

    public DegradeException(String stage, String message) {
        super(message);
        this.stage = stage;
    }

    public DegradeException(String stage, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
    }

    public String getStage() {
        return stage;
    }
}
