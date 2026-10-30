package com.zhikeban.bishe.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 资料切片实体（`ENG-012`：RAG 链路的**检索引擎表**，对应概设 §4.4 表 4-7，{@code TB-07}，`material_chunk`）。
 *
 * <p>★ 本表是**全文一路（BM25 / ngram）**的落点（`content` 列有 `FULLTEXT ... WITH PARSER ngram`），
 * 同时 `id` 就是向量表 `TB-27`／`TB-28` 的 `chunk_id`（`VectorStore` 的幂等键）。
 *
 * <p>★ `ENG-009` 只建了表结构、**一行没写**（边界 (a)）；本单把"切片 → 向量 → 检索"接起来。
 */
@TableName("`material_chunk`")
public class MaterialChunk {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属资料（FK→TB-06.id）。 */
    @TableField("material_id")
    private Long materialId;

    /** 资料内切片序号，从 0 开始。 */
    @TableField("chunk_index")
    private Integer chunkIndex;

    /** 切片正文（**全文索引列**，BM25 一路）。 */
    private String content;

    /** 起始页码。 */
    @TableField("page_no")
    private Integer pageNo;

    /** 所属章节。 */
    private String chapter;

    /** 向量存储侧标识（Oracle 行标识或 MySQL 主键）。 */
    @TableField("vector_id")
    private String vectorId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public String getChapter() {
        return chapter;
    }

    public void setChapter(String chapter) {
        this.chapter = chapter;
    }

    public String getVectorId() {
        return vectorId;
    }

    public void setVectorId(String vectorId) {
        this.vectorId = vectorId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
