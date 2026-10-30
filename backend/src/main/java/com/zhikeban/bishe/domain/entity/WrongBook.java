package com.zhikeban.bishe.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 错题本实体（`SRS` `TB-15`，表名逐字取自 `schema.sql`：`wrong_book`）。
 *
 * <p>★ 本单（`ENG-014`）**不新增 DDL**：字段与 `schema.sql` 的 `wrong_book` 一一对应，
 * 表名/列名的唯一事实源是 `schema.sql`（`§ENG-014` §2 ①）。
 *
 * <p>★★ **去重口径取甲**（`§ENG-014` `A-1`）：`TB-15` 只有多态 `source_type`/`source_id`，
 * **没有** `question_id`、**没有**唯一键 ⇒ 去重只能在**应用层**按
 * `(user_id, course_id, source_type, source_id)` 复用未删除行；`MANUAL` 来源**允许同题多条**。
 *
 * <p>★ `status`：0 待复习、1 已掌握、2 已归档（`W4` 归档即置 2）；逻辑删除由 {@link TableLogic} 接管。
 */
@TableName("wrong_book")
public class WrongBook {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属学生（FK→TB-01.id）。 */
    @TableField("user_id")
    private Long userId;

    /** 所属课程（FK→TB-04.id）。 */
    @TableField("course_id")
    private Long courseId;

    /** 来源：QUESTION / AI / MANUAL。 */
    @TableField("source_type")
    private String sourceType;

    /** 来源对象标识（多态关联；MANUAL 为 null）。 */
    @TableField("source_id")
    private Long sourceId;

    /** 题目内容快照。 */
    @TableField("question_content")
    private String questionContent;

    /** 我的答案。 */
    @TableField("my_answer")
    private String myAnswer;

    /** 正确答案。 */
    @TableField("right_answer")
    private String rightAnswer;

    /** 错误原因。 */
    @TableField("error_reason")
    private String errorReason;

    /** 掌握等级：0 未掌握 ~ 3 已掌握（库上 `CHECK 0 ~ 3`）。 */
    @TableField("mastery_level")
    private Integer masteryLevel;

    /** 掌握度分值（`DECIMAL(5,2)`，库上 `CHECK 0 ~ 100`）。 */
    @TableField("mastery_score")
    private BigDecimal masteryScore;

    /** 状态：0 待复习、1 已掌握、2 已归档。 */
    private Integer status;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getQuestionContent() {
        return questionContent;
    }

    public void setQuestionContent(String questionContent) {
        this.questionContent = questionContent;
    }

    public String getMyAnswer() {
        return myAnswer;
    }

    public void setMyAnswer(String myAnswer) {
        this.myAnswer = myAnswer;
    }

    public String getRightAnswer() {
        return rightAnswer;
    }

    public void setRightAnswer(String rightAnswer) {
        this.rightAnswer = rightAnswer;
    }

    public String getErrorReason() {
        return errorReason;
    }

    public void setErrorReason(String errorReason) {
        this.errorReason = errorReason;
    }

    public Integer getMasteryLevel() {
        return masteryLevel;
    }

    public void setMasteryLevel(Integer masteryLevel) {
        this.masteryLevel = masteryLevel;
    }

    public BigDecimal getMasteryScore() {
        return masteryScore;
    }

    public void setMasteryScore(BigDecimal masteryScore) {
        this.masteryScore = masteryScore;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
