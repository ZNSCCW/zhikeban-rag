package com.zhikeban.bishe.rag;

/**
 * RAG 请求（`ENG-012` 任务①；详设 §4.3.2 的 {@code RagRequest}）。
 *
 * @param courseId 课程标识（检索范围 ＋ 权限过滤的课程级白名单）
 * @param chatId   会话标识（引用回填落 `TB-19` 用；可为 {@code null}）
 * @param userId   提问用户（成员级权限过滤的主体）
 * @param question 提问正文
 * @param stream   是否流式（本单链路一律按流式组织，标志位留给接口层）
 */
public record RagRequest(Long courseId, Long chatId, Long userId, String question, boolean stream) {
}
