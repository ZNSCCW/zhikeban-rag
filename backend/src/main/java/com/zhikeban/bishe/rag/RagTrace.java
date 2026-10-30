package com.zhikeban.bishe.rag;

/**
 * 单个 RAG 阶段的执行留痕（`ENG-012` 任务⑤：冒烟脚本要"**逐阶段可核**"，不是只看一个总判）。
 *
 * @param label  阶段名（人读；含六阶段关键词，供冒烟脚本逐条核对）
 * @param stage  阶段标识（枚举名）
 * @param ok     该阶段是否按预期完成（**降级兜底也算完成**，只有真失败才为假）
 * @param detail 人读细节（条数、耗时、降级原因等）
 * @param ms     该阶段耗时（毫秒）
 */
public record RagTrace(String label, String stage, boolean ok, String detail, long ms) {
}
