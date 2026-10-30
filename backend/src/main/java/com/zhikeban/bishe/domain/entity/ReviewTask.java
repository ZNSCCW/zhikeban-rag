package com.zhikeban.bishe.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 复习任务实体（`SRS` `TB-17`，表名逐字取自 `schema.sql`：`review_task`）。
 *
 * <p>★ `stage` 直接落**节点天数**（1／2／4／7／15／30），不落"第几个节点"—— 详设 §4.4.3：
 * 天数本身是稳定锚，节点顺序可配置后仍可解释。
 *
 * <p>★ `status`：0 待复习、1 已完成、2 已跳过／已关闭（归档与"仍需巩固"重置都会关闭未完成项）；
 * `result`：0 仍不会、1 模糊、2 已掌握。
 */
@TableName("review_task")
public class ReviewTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 对应错题（FK→TB-15.id）。 */
    @TableField("wrong_id")
    private Long wrongId;

    /** 所属学生（FK→TB-01.id）。 */
    @TableField("user_id")
    private Long userId;

    /** 计划复习日期。 */
    @TableField("plan_date")
    private LocalDate planDate;

    /** 复习节点：1、2、4、7、15、30 天。 */
    private Integer stage;

    /** 状态：0 待复习、1 已完成、2 已跳过。 */
    private Integer status;

    /** 复习结果：0 仍不会、1 模糊、2 已掌握。 */
    private Integer result;

    /** 实际复习时间。 */
    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWrongId() {
        return wrongId;
    }

    public void setWrongId(Long wrongId) {
        this.wrongId = wrongId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    public void setPlanDate(LocalDate planDate) {
        this.planDate = planDate;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getResult() {
        return result;
    }

    public void setResult(Integer result) {
        this.result = result;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
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
}
