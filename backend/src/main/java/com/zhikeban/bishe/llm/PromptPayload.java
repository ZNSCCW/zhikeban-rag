package com.zhikeban.bishe.llm;

import java.util.List;

/**
 * 提示词载荷（`ENG-012`：从空 `record` 占位**补字段**为真实现）。
 *
 * <p>★ 语义锚（详设 §4.3.2 的上下文块）：系统提示 ＋ 用户消息 ＋ 片段证据（**编号化**）
 * ＋ 生成参数（温度 / 上限）。⇒ 组件即按该语义定。
 *
 * <p>★ 片段**编号化**是 `CitationResolver` 剔除"编造引用"的前提：证据文本在
 * {@link #evidences} 里按 `[1] [2] …` 的顺序与 {@code CitationResolver} 的编号一一对应。
 *
 * @param systemPrompt 系统提示（来源 `system_config` 的 `prompt.system`）
 * @param question     当前用户问题
 * @param evidences    片段证据（已编号化：第 i 条对应引用编号 i+1）
 * @param history      最近若干轮对话（详设：只带最近 5 轮）
 * @param temperature  生成温度
 * @param maxTokens    生成长度上限（token）
 */
public record PromptPayload(String systemPrompt, String question, List<String> evidences,
                            List<String> history, double temperature, int maxTokens) {
}
