package com.zhikeban.bishe.rag;

/**
 * 引用卡片（`ENG-012` 任务①；详设 §4.3.2 的 {@code Citation}）。
 *
 * <p>★ 这是"**引用溯源**"创新点的落地形态：引用**不是模型自由生成**，而是由检索层回填的
 * 结构化元数据（`material_id + chapter + page + chunk_id`）。
 *
 * @param materialId   资料标识
 * @param materialName 资料名
 * @param chapter      章节
 * @param pageNo       页码
 * @param snippet      片段摘录
 */
public record Citation(Long materialId, String materialName, String chapter,
                       Integer pageNo, String snippet) {
}
