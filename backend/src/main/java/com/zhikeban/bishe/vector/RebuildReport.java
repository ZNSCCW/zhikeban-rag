package com.zhikeban.bishe.vector;

/**
 * 重建报告（`ENG-012`：从空 `record` 占位**补字段**为真实现）。
 *
 * <p>★ 语义锚（详设 §3.4 的 `rebuild` 行）：返回**处理条数与耗时**。
 *
 * @param courseId   被重建的课程标识
 * @param processed  实际写入（upsert）的向量条数
 * @param deleted    重建前清理掉的旧向量条数
 * @param elapsedMs  耗时（毫秒）
 * @param provider   执行本次重建的实现标识（{@code "oracle"} / {@code "mysql"}）
 */
public record RebuildReport(Long courseId, int processed, int deleted, long elapsedMs, String provider) {
}
