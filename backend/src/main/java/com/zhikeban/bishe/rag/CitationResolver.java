package com.zhikeban.bishe.rag;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * **引用回填**（`ENG-012` 任务①；详设 §4.3.7）—— "引用溯源"创新点的落地机制。
 *
 * <p>★ 机制：① 从正文里抽取形如 {@code [1]} {@code [2]} 的编号；② **只保留编号落在本次检索集合内**的引用
 * （越界编号 = 模型编造 ⇒ **剔除**）；③ 回填 `Citation`（`material_id` / `material_name` / `chapter` /
 * `page_no` / `snippet`）。
 *
 * <p>★ **引用不是模型自由生成**：`Citation` 的字段全部来自**检索层**的结构化元数据，
 * 模型只负责"指向哪个编号"。⇒ 即使模型写了越界编号，也不可能凭空造出一条参考文献。
 *
 * <p>★ 若模型一个编号都没给出（正文没有任何 `[n]`），则由**检索层回填**命中片段的元数据，
 * 并把这批引用标为"依据" —— 服务端**不编造**任何字段，只把已检索到的片段列出来。
 */
@Service
public class CitationResolver {

    /** 引用编号形态：`[1]` ~ `[999]`。 */
    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,3})]");

    /** 服务端回填时的最多条数（模型未给编号时用）。 */
    private static final int BACKFILL_LIMIT = 3;

    /**
     * 详设 §4.3.7 的原始签名（未降级）。
     *
     * @param deltas 流式分片（拼接为正文）
     * @param hits   本次检索集合（**编号的唯一合法来源**）
     * @param chatId 会话标识
     * @return 带 `citations` 的回答
     */
    public RagAnswer resolve(List<String> deltas, List<RetrievedChunk> hits, Long chatId) {
        return resolve(deltas, hits, chatId, false, null);
    }

    /**
     * 带降级信息的重载。
     *
     * @param deltas        流式分片
     * @param hits          本次检索集合
     * @param chatId        会话标识
     * @param degraded      是否走了降级链第三级（全文检索兜底）
     * @param degradeReason 降级原因（未降级传 {@code null}）
     * @return 带 `citations` 的回答
     */
    public RagAnswer resolve(List<String> deltas, List<RetrievedChunk> hits, Long chatId,
                             boolean degraded, String degradeReason) {
        String content = deltas == null ? "" : String.join("", deltas);
        List<RetrievedChunk> evidence = hits == null ? List.of() : hits;
        Set<Integer> numbers = new LinkedHashSet<>();
        Matcher m = CITATION.matcher(content);
        while (m.find()) {
            int idx = Integer.parseInt(m.group(1));
            if (idx >= 1 && idx <= evidence.size()) {
                numbers.add(idx);
            }
            // ★ 越界编号直接丢弃：模型编造的引用不进 citations（这是"杜绝编造参考文献"的机器化）
        }
        if (numbers.isEmpty()) {
            for (int i = 1; i <= Math.min(evidence.size(), BACKFILL_LIMIT); i++) {
                numbers.add(i);
            }
        }
        List<Citation> citations = new ArrayList<>(numbers.size());
        for (int idx : numbers) {
            RetrievedChunk c = evidence.get(idx - 1);
            citations.add(new Citation(c.materialId(), c.materialName(), c.chapter(), c.pageNo(),
                    snippet(c.content())));
        }
        return new RagAnswer(chatId, content, citations, degraded, degradeReason, false);
    }

    /** 片段摘录（截断到 120 字符，避免引用卡片过长）。 */
    static String snippet(String content) {
        if (content == null) {
            return "";
        }
        String s = content.strip();
        return s.length() <= 120 ? s : s.substring(0, 120) + "…";
    }
}
