package com.zhikeban.bishe.vector;

import java.util.List;

/**
 * 检索查询条件（`ENG-012`：从空 `record` 占位**补字段**为真实现）。
 *
 * <p>★ 语义锚（详设 §3.4 的 `search` 行 ＋ 其后的边界说明）：含**查询文本的向量**、
 * **课程范围**（★ 只做**课程级预过滤**，成员级权限过滤**不在**本层做）与**返回条数**。
 *
 * <p>★ {@code embedding} 允许为 {@code null}：此时实现侧按 {@code text} 现场取嵌入
 * （便于调用方只给文本）；{@code dim} 与库内 {@code dim} 不一致时实现侧**拒绝**而不是"算出错误结果"。
 *
 * @param text      查询原文（用于全文一路与取嵌入）
 * @param embedding 查询向量（可为 {@code null}，由实现侧按 {@code text} 现算）
 * @param courseIds 课程范围（**课程级预过滤**；为空表示不限课程）
 * @param topK      返回条数上限（TopK 截断）
 */
public record SearchQuery(String text, float[] embedding, List<Long> courseIds, int topK) {

    /** 便捷构造：只给文本与课程范围，向量由实现侧现算（默认 TopK = 8，详设 §4.3.3）。 */
    public static SearchQuery of(String text, Long courseId, int topK) {
        return new SearchQuery(text, null, courseId == null ? List.of() : List.of(courseId), topK);
    }
}
