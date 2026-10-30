package com.zhikeban.bishe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikeban.bishe.domain.entity.MaterialChunk;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 资料切片 Mapper（`ENG-012`）：单表 CRUD ＋ **全文一路的检索语句**。
 *
 * <p>★ 全文检索落在 `material_chunk.content` 的 `FULLTEXT ... WITH PARSER ngram` 索引上
 * （概设 §4.5：全文索引**仅**建于该列），先按 `course_id` 做课程级预过滤 —— 与向量一路同口径，
 * 成员级权限过滤仍由 `PermissionFilter` 统一做（详设 §3.4 边界）。
 *
 * <p>★ 本语句**只读**：不建索引、不写任何 DDL。
 */
public interface MaterialChunkMapper extends BaseMapper<MaterialChunk> {

    /**
     * 全文一路：按 `courseId` 预过滤后取 BM25 命中（TopK）。
     *
     * @param courseId 课程标识（课程级预过滤）
     * @param keyword  查询原文（自然语言模式）
     * @param topK     返回条数上限
     * @return 命中的切片（按库内相关度排序）
     */
    @Select("""
            SELECT c.id, c.material_id, c.chunk_index, c.content, c.page_no, c.chapter, c.vector_id
              FROM material_chunk c
              JOIN material m ON m.id = c.material_id AND m.deleted = 0
             WHERE c.deleted = 0
               AND m.course_id = #{courseId}
               AND MATCH(c.content) AGAINST(#{keyword} IN NATURAL LANGUAGE MODE)
             LIMIT #{topK}
            """)
    List<MaterialChunk> searchFullText(@Param("courseId") Long courseId,
                                       @Param("keyword") String keyword,
                                       @Param("topK") int topK);
}
