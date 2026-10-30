package com.zhikeban.bishe.rag;

import java.util.List;

/**
 * RAG 回答（`ENG-012` 任务①；详设 §4.3.2 的 {@code RagAnswer}）。
 *
 * @param messageId     消息标识（落 `TB-19` 后的主键；本单可为 {@code null}）
 * @param content       正文（流式分片拼接后的完整回答）
 * @param citations     引用卡片（**只含本次检索集合内的编号**，越界编号已剔除）
 * @param degraded      是否发生降级（本地/API 均不可用 ⇒ 全文检索兜底）
 * @param degradeReason 降级原因（未降级则为 {@code null}）
 * @param cacheHit      是否命中结果缓存
 */
public record RagAnswer(Long messageId, String content, List<Citation> citations,
                        boolean degraded, String degradeReason, boolean cacheHit) {
}
