package com.zhikeban.bishe.web.dto;

import java.util.List;

/**
 * 错题本与复习的入参 DTO（`SRS` `API-36`~`API-42`；`§ENG-014` `W`／`P` 组）。
 *
 * <p>★ 只承载**入参**：出参一律复用实体（与既有 `Question`／`Course` 控制器的风格一致），
 * 避免为了包装而再造一层同名字段（同一事实两份副本是本项目一直在治的病）。
 */
public final class WrongBookDtos {

    private WrongBookDtos() {
    }

    /**
     * 新增错题（`API-36`）。
     *
     * @param courseId        所属课程（必填；调用方必须先是该课程成员）
     * @param sourceType      来源：`QUESTION`／`AI`／`MANUAL`（缺省按 `MANUAL`）
     * @param sourceId        来源对象标识（`MANUAL` 可为空）
     * @param questionContent 题目内容快照（必填）
     * @param myAnswer        我的答案（可选）
     * @param rightAnswer     正确答案（可选）
     * @param errorReason     错误原因（可选）
     * @param masteryMark     手动掌握标记：`未掌握`／`模糊`／`已掌握`（缺省按未掌握）
     * @param kpIds           关联知识点（必填 1~3 个）
     */
    public record WrongCommand(Long courseId,
                               String sourceType,
                               Long sourceId,
                               String questionContent,
                               String myAnswer,
                               String rightAnswer,
                               String errorReason,
                               String masteryMark,
                               List<Long> kpIds) {
    }

    /**
     * 错题列表筛选（`API-37`）。
     *
     * @param courseId 按课程筛选（可选）
     * @param status   按状态筛选（可选：0 待复习 / 1 已掌握 / 2 已归档）
     * @param keyword  题目内容关键词（可选）
     */
    public record WrongQuery(Long courseId, Integer status, String keyword) {
    }

    /**
     * 更新错题（`API-38`）。
     *
     * @param errorReason 错误原因（null 表示不改）
     * @param masteryMark 手动掌握标记（null 表示不改）
     * @param kpIds       知识点（null 表示不改；给了就整组替换）
     */
    public record WrongUpdateCommand(String errorReason, String masteryMark, List<Long> kpIds) {
    }

    /**
     * 提交复习结果（`API-41`）。
     *
     * @param result 0 仍不会 / 1 模糊 / 2 已掌握（必填）
     */
    public record ReviewCompleteCommand(Integer result) {
    }
}
