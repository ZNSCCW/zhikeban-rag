package com.zhikeban.bishe.llm;

import reactor.core.publisher.Flux;

/**
 * 大模型调用**接口占位**（`ENG-009` 边界 (a)：**只留接口占位、不接通**；
 * `ENG-011` 任务②：把签名**对齐**《详细设计说明书》§4.3.2 ⑤ 的既有契约）。
 *
 * <p>★★ 本文件的**方法签名逐字对齐详设 §4.3.2 ⑤ 的接口定义块**（4 个方法）：
 * <ol>
 *   <li>{@code Flux<String> stream(PromptPayload payload)} —— 流式生成；</li>
 *   <li>{@code String complete(PromptPayload payload)} —— 一次性生成（题目解析等非流式场景）；</li>
 *   <li>{@code String provider()} —— 提供方标识；★ 保留（GM 裁决 {@code P4}）；</li>
 *   <li>{@code boolean healthy()} —— 健康探活。</li>
 * </ol>
 *
 * <p>★ 本单**只对齐签名**，**不是接线、更不是实现**：本文件**不含**任何生成实现，
 * 也**不提供实现类、不加装配标记、不注册 Bean** —— 真正的生成链路（本地优先 +
 * API 备选 + 降级兜底）属 {@code MVP 二}，**本单推后**。
 *
 * <p>★ 边界纪律：本文件里**不出现**任何装配标记（{@code R3a} 判据）。
 */
public interface LlmClient {

    /**
     * 流式生成（后端按流式分片下发；前端按事件类型分派）。
     *
     * @param payload 提示词载荷
     * @return 流式分片的文本序列
     */
    Flux<String> stream(PromptPayload payload);

    /**
     * 一次性生成（题目解析等非流式场景）。
     *
     * @param payload 提示词载荷
     * @return 模型回复文本
     */
    String complete(PromptPayload payload);

    /**
     * 当前大模型提供方标识，用于埋点与自检。
     *
     * @return 提供方标识
     */
    String provider();

    /**
     * 健康探活：用于判定当前提供方是否可用。
     *
     * @return 可用则真
     */
    boolean healthy();
}
