package com.zhikeban.bishe.vector;

/**
 * 向量记录（`ENG-012`：从空 `record` 占位**补字段**为真实现；`ENG-011` 只留声明、字段明确留给本单）。
 *
 * <p>★ 语义锚（详设 §3.4 的 `upsert` 行）：**以分片标识为唯一键**，课程标识、维度、
 * 向量本体与元数据一并写入。⇒ 组件即按该语义定：{@code chunkId} 是幂等键、
 * {@code courseId} 是检索预过滤列、{@code dim} 是维度校验依据、{@code embedding} 是 `float32` 序列、
 * {@code metadata} 是资料名/章节/页码的 JSON 文本。
 *
 * @param chunkId   分片标识（= `material_chunk.id`，`TB-28.chunk_id` 主键；**幂等键**）
 * @param courseId  课程标识（= `TB-04.id`；检索时按课程级预过滤）
 * @param dim       向量维度（`bge-m3` = 1024；与 `TB-28.dim`、`TB-27` 的 `VECTOR(n)` 三格同值）
 * @param embedding 向量序列化前的浮点序列（长度必须等于 {@code dim}）
 * @param metadata  元数据 JSON 文本（资料名、章节、页码）
 */
public record VectorRecord(Long chunkId, Long courseId, int dim, float[] embedding, String metadata) {
}
