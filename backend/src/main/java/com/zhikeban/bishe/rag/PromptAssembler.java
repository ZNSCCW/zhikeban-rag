package com.zhikeban.bishe.rag;

import com.zhikeban.bishe.llm.PromptPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * **提示词组装**（`ENG-012` 任务①；详设 §4.3.2 ④）。
 *
 * <p>★ 结构：`[系统提示] + [片段 1..n（带编号、资料名、章节、页码）] + [最近 5 轮对话] + [当前问题]`。
 * 片段**编号化**是 `CitationResolver` 剔除"编造引用"的前提。
 *
 * <p>★ **预算与截断策略集中在本类**（其余组件不感知）：片段总长超出预算时**按相关度降序截断**，
 * 且**截断发生在片段边界**（不切句子）—— 宁可少给一片证据，也不让链路失败。
 *
 * <p>★ 系统提示优先从 `system_config`（`TB-26`）的 `prompt.system` 读；读不到（表未初始化 /
 * 该键未配置）时退到配置里的默认值 —— **不抛异常**（配置缺失不该让问答失败）。
 */
@Service
public class PromptAssembler {

    private static final Logger log = LoggerFactory.getLogger(PromptAssembler.class);

    /** 片段总长预算（详设 §4.3.2 ④：片段 ≤ 3000 token；此处按字符近似，1 token ≈ 2 字符）。 */
    private static final int EVIDENCE_BUDGET_CHARS = 6000;

    /** 最近对话轮数（`SRS §3.4` FR-4-04：只带最近 5 轮）。 */
    private static final int HISTORY_TURNS = 5;

    private final DataSource dataSource;
    private final String defaultSystemPrompt;
    private final double temperature;
    private final int maxTokens;

    public PromptAssembler(
            DataSource dataSource,
            @Value("${zhikeban.rag.system-prompt:你是课程资料助教。只依据下面编号的片段回答，"
                    + "引用必须写成 [编号]；片段里没有依据时如实说\"资料中未找到依据\"。}") String defaultSystemPrompt,
            @Value("${zhikeban.rag.temperature:0.2}") double temperature,
            @Value("${zhikeban.rag.max-tokens:512}") int maxTokens) {
        this.dataSource = dataSource;
        this.defaultSystemPrompt = defaultSystemPrompt;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    /**
     * 组装提示词载荷。
     *
     * @param hits     过滤后的片段（按融合分降序）
     * @param history  历史对话（只取最近 {@value #HISTORY_TURNS} 轮）
     * @param question 当前问题
     * @return 提示词载荷（片段已编号化）
     */
    public PromptPayload assemble(List<RetrievedChunk> hits, List<String> history, String question) {
        List<String> evidences = new ArrayList<>();
        int used = 0;
        List<RetrievedChunk> ordered = hits == null ? List.of() : hits;
        for (RetrievedChunk c : ordered) {
            String piece = render(c);
            if (used + piece.length() > EVIDENCE_BUDGET_CHARS && !evidences.isEmpty()) {
                log.info("片段预算用尽（{} 字符上限），按相关度截断：已保留 {} 片，丢弃 {} 片",
                        EVIDENCE_BUDGET_CHARS, evidences.size(), ordered.size() - evidences.size());
                break;
            }
            evidences.add(piece);
            used += piece.length();
        }
        List<String> recent = new ArrayList<>();
        if (history != null) {
            int from = Math.max(0, history.size() - HISTORY_TURNS);
            recent.addAll(history.subList(from, history.size()));
        }
        return new PromptPayload(systemPrompt(), question, evidences, recent, temperature, maxTokens);
    }

    /** 片段渲染：`[i] 资料名（章节 · 第 N 页）：正文`。 */
    String render(RetrievedChunk c) {
        StringBuilder sb = new StringBuilder();
        if (c.materialName() != null && !c.materialName().isBlank()) {
            sb.append(c.materialName());
        } else {
            sb.append("资料#").append(c.materialId());
        }
        if (c.chapter() != null && !c.chapter().isBlank()) {
            sb.append("（").append(c.chapter());
            if (c.pageNo() != null) {
                sb.append(" · 第 ").append(c.pageNo()).append(" 页");
            }
            sb.append("）");
        } else if (c.pageNo() != null) {
            sb.append("（第 ").append(c.pageNo()).append(" 页）");
        }
        sb.append("：").append(c.content() == null ? "" : c.content());
        return sb.toString();
    }

    /** 系统提示：`system_config(TB-26)` 的 `prompt.system` 优先，读不到用配置默认值。 */
    String systemPrompt() {
        String sql = "SELECT config_value FROM system_config WHERE config_key = 'prompt.system'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String v = rs.getString(1);
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
        } catch (Exception e) {
            log.debug("读取 system_config.prompt.system 失败，使用配置默认系统提示：{}", e.getMessage());
        }
        return defaultSystemPrompt;
    }
}
