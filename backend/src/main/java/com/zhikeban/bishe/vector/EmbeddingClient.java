package com.zhikeban.bishe.vector;

/**
 * 嵌入客户端（`ENG-012` 任务④）：把文本变成 {@code float32} 向量。
 *
 * <p>★ 契约：**维度必须与库结构同值** —— `bge-m3` = **1024** 维，与 `TB-27` 的 `VECTOR(1024)`、
 * `TB-28` 的 `BLOB(4096)`（= 1024 × 4 字节）与 `TB-28.dim` 的默认值 **三格同值**。
 * ★ 换模型时这三处必须**在同一个发布里一起改**（详设 §4.3.8 的"换模型重建步骤"），
 * 否则 {@code MysqlVectorStoreImpl.search} 的维度校验会**拒绝**检索（宁拒绝、不算错）。
 */
public interface EmbeddingClient {

    /**
     * 取文本的嵌入向量。
     *
     * @param text 文本（空白文本返回零向量或抛异常由实现自定，调用方按空结果处理）
     * @return 长度等于 {@link #dim()} 的浮点序列
     */
    float[] embed(String text);

    /** 当前嵌入维度（★ 必须与 `TB-27`／`TB-28` 的列定义同值）。 */
    int dim();

    /** 提供方标识（用于埋点与自检，例如 {@code "ollama"}）。 */
    String provider();
}
