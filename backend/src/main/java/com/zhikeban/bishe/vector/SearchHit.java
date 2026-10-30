package com.zhikeban.bishe.vector;

/**
 * 检索命中（`ENG-012`：从空 `record` 占位**补字段**为真实现）。
 *
 * <p>★ 语义锚（详设 §3.4 的 `search` 行）：**必须带原始相似度分数**（不是只给标识）。
 * ⇒ 组件 {@code score} 即该契约的落点（**余弦相似度**，越大越相关）；两侧实现（Oracle／MySQL）
 * 的分数语义必须一致，切换实现不改变相对次序。
 *
 * @param chunkId    分片标识（= `material_chunk.id`）
 * @param courseId   课程标识（权限过滤与二次核对用）
 * @param materialId 资料标识（`PermissionFilter` 按它反查 `material.course_id`）
 * @param score      原始相似度分数（**余弦相似度**，越大越相关；未做任何归一化改写）
 * @param content    切片正文（提示词组装与引用回填用）
 * @param metadata   元数据 JSON 文本（资料名、章节、页码）
 */
public record SearchHit(Long chunkId, Long courseId, Long materialId,
                        double score, String content, String metadata) {
}
