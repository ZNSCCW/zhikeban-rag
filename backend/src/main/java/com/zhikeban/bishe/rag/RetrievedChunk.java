package com.zhikeban.bishe.rag;

/**
 * 检索到的片段（`ENG-012` 任务①；详设 §4.3.2 的 {@code RetrievedChunk}）。
 *
 * <p>★ 这是**融合后**的中间表示：{@code score} 是 RRF 融合分（跨量纲稳健），
 * 不是原始余弦相似度 —— 原始分留在 {@code SearchHit} 里（`A6` 的契约）。
 *
 * @param chunkId      切片标识（去重键：同一片段被两路同时召回时只留一条）
 * @param materialId   资料标识（`PermissionFilter` 按它反查 `material.course_id`）
 * @param materialName 资料名（提示词与引用卡片用）
 * @param chapter      章节
 * @param pageNo       起始页码
 * @param score        融合分（RRF）
 * @param content      切片正文
 */
public record RetrievedChunk(Long chunkId, Long materialId, String materialName,
                             String chapter, Integer pageNo, double score, String content) {
}
