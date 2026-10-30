package com.zhikeban.bishe.vector;

import java.util.Collection;
import java.util.List;

/**
 * 向量存储**接口占位**（`ENG-009` 边界 (a)：**只留接口占位、不接通**；
 * `ENG-011` 任务①：把签名**对齐**《详细设计说明书》§3.4 的既有契约）。
 *
 * <p>★★ 本文件的**方法签名逐字对齐详设 §3.4 的接口定义块**（5 个方法）：
 * <ol>
 *   <li>{@code int upsert(List<VectorRecord> records)} —— 幂等，以 {@code chunkId} 为唯一键；</li>
 *   <li>{@code int delete(Collection<Long> chunkIds)} —— 集合口径，不存在的 {@code chunkId} 不报错；</li>
 *   <li>{@code List<SearchHit> search(SearchQuery query)} —— **返回带原始相似度分数**的命中；</li>
 *   <li>{@code RebuildReport rebuild(Long courseId)} —— 按课程整体重建（管理动作）；</li>
 *   <li>{@code String provider()} —— {@code "oracle"} / {@code "mysql"}，用于埋点与自检。</li>
 * </ol>
 *
 * <p>★ 本单**只对齐签名**，**不是接线、更不是实现**：本文件**不含**任何存储/检索实现，
 * 也**不提供实现类、不加装配标记、不注册 Bean** —— 真正的向量检索（Oracle 原生向量 /
 * MySQL 兜底）属 {@code MVP 二}，**本单推后**。
 *
 * <p>★ 边界纪律：本文件里**不写**任何相似度 / 距离 SQL 或函数（{@code R5} 判据），
 * 也**不出现**任何装配标记（{@code R3a} 判据）。
 *
 * <p>★ 一处**语义边界**（详设 §3.4 原文）：范围参数**只做课程级预过滤**，成员级权限过滤
 * **不在此层**完成（那是权限过滤组件的职责）—— 这样向量层保持"纯检索"。
 */
public interface VectorStore {

    /**
     * 写入或覆盖向量记录（**幂等**：以 {@code chunkId} 为唯一键）。
     *
     * @param records 待写入的向量记录集合
     * @return 受影响的记录条数
     */
    int upsert(List<VectorRecord> records);

    /**
     * 按分片标识集合删除向量（**幂等**：不存在的标识不报错）。
     *
     * @param chunkIds 分片标识集合（★ 是集合口径，不是列表口径）
     * @return 实际删除条数
     */
    int delete(Collection<Long> chunkIds);

    /**
     * 相似片段检索：按 {@code query} 的查询向量取 TopK，**返回带原始相似度分数**的命中。
     *
     * @param query 查询条件（含查询文本 / 向量、课程范围、TopK）
     * @return 带相似度分数的命中列表
     */
    List<SearchHit> search(SearchQuery query);

    /**
     * 按课程整体重建向量（**管理动作**：先算后写，失败可重跑）。
     *
     * @param courseId 课程标识（★ 是长整型标识，不是基本整型）
     * @return 重建报告（处理条数与耗时）
     */
    RebuildReport rebuild(Long courseId);

    /**
     * 当前向量存储的提供方标识，用于埋点与自检。
     *
     * @return {@code "oracle"} / {@code "mysql"}
     */
    String provider();
}
