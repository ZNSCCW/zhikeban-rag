package com.zhikeban.bishe.rag;

import com.zhikeban.bishe.llm.PromptPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * `PromptAssembler` 单测（`ENG-013`）：片段编号化前的渲染、**预算截断发生在片段边界**、
 * 只带最近 5 轮历史、系统提示从 `TB-26` 读且**读不到不抛异常**。
 */
class PromptAssemblerTest {

    private static final String DEFAULT_PROMPT = "默认系统提示";

    private DataSource dataSource;
    private Connection conn;
    private PreparedStatement ps;
    private ResultSet rs;
    private PromptAssembler assembler;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        conn = mock(Connection.class);
        ps = mock(PreparedStatement.class);
        rs = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        assembler = new PromptAssembler(dataSource, DEFAULT_PROMPT, 0.2d, 512);
    }

    private static RetrievedChunk chunk(long id, String name, String chapter, Integer pageNo, String content) {
        return new RetrievedChunk(id, 10L, name, chapter, pageNo, 1.0d, content);
    }

    private static String repeat(String s, int times) {
        return s.repeat(Math.max(0, times));
    }

    @Test
    void rendersNameChapterAndPageVariants() {
        assertThat(assembler.render(chunk(1L, "资料A", "第1章", 3, "正文")))
                .isEqualTo("资料A（第1章 · 第 3 页）：正文");
        assertThat(assembler.render(chunk(1L, null, null, 7, "正文")))
                .isEqualTo("资料#10（第 7 页）：正文");
        assertThat(assembler.render(chunk(1L, "  ", "第2章", null, "正文")))
                .isEqualTo("资料#10（第2章）：正文");
        assertThat(assembler.render(chunk(1L, "资料A", null, null, null)))
                .isEqualTo("资料A：");
    }

    @Test
    void assembleNumbersNothingButKeepsOrderAndCarriesSamplingParams() {
        PromptPayload payload = assembler.assemble(List.of(
                chunk(1L, "资料A", "第1章", 1, "甲"),
                chunk(2L, "资料B", "第2章", 2, "乙")), List.of(), "问题？");

        assertThat(payload.evidences()).containsExactly("资料A（第1章 · 第 1 页）：甲", "资料B（第2章 · 第 2 页）：乙");
        assertThat(payload.question()).isEqualTo("问题？");
        assertThat(payload.history()).isEmpty();
        assertThat(payload.temperature()).isEqualTo(0.2d);
        assertThat(payload.maxTokens()).isEqualTo(512);
    }

    @Test
    void assembleToleratesNullHitsAndNullHistory() {
        PromptPayload payload = assembler.assemble(null, null, "问题？");

        assertThat(payload.evidences()).isEmpty();
        assertThat(payload.history()).isEmpty();
    }

    @Test
    void budgetTruncationHappensAtChunkBoundaryNotMidSentence() {
        // 第一片超预算但 evidences 为空 ⇒ 仍收下（不能一片都不给）；第二片再超 ⇒ 在片段边界截断
        String big = repeat("字", 7000);
        PromptPayload payload = assembler.assemble(List.of(
                chunk(1L, "资料A", null, null, big),
                chunk(2L, "资料B", null, null, big)), List.of(), "q");

        assertThat(payload.evidences()).hasSize(1);
        assertThat(payload.evidences().get(0)).endsWith(big);
    }

    @Test
    void budgetTruncationWhenFirstChunkFits() {
        PromptPayload payload = assembler.assemble(List.of(
                chunk(1L, "资料A", null, null, repeat("甲", 3000)),
                chunk(2L, "资料B", null, null, repeat("乙", 4000))), List.of(), "q");

        assertThat(payload.evidences()).hasSize(1);
    }

    @Test
    void onlyTheLastFiveHistoryTurnsAreKept() {
        List<String> history = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            history.add("第" + i + "轮");
        }

        PromptPayload payload = assembler.assemble(List.of(), history, "q");

        assertThat(payload.history()).containsExactly("第4轮", "第5轮", "第6轮", "第7轮", "第8轮");
    }

    @Test
    void systemPromptComesFromSystemConfigWhenPresent() throws Exception {
        when(rs.next()).thenReturn(true);
        when(rs.getString(1)).thenReturn("来自 TB-26 的系统提示");

        assertThat(assembler.systemPrompt()).isEqualTo("来自 TB-26 的系统提示");
    }

    @Test
    void blankOrMissingSystemConfigFallsBackToDefault() throws Exception {
        when(rs.next()).thenReturn(true);
        when(rs.getString(1)).thenReturn("   ");
        assertThat(assembler.systemPrompt()).isEqualTo(DEFAULT_PROMPT);

        when(rs.next()).thenReturn(false);
        assertThat(assembler.systemPrompt()).isEqualTo(DEFAULT_PROMPT);
    }

    @Test
    void databaseFailureFallsBackToDefaultInsteadOfFailingTheAnswer() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("表还没初始化"));

        assertThat(assembler.systemPrompt()).isEqualTo(DEFAULT_PROMPT);
    }
}
