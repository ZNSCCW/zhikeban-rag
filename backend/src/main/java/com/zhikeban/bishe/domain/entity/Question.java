package com.zhikeban.bishe.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 任务③ 提问实体（对应概设 §4.4 表 4-9，{@code TB-09}，{@code question}）。
 *
 * <p>★ {@code ENG-009} 的问答**只做社区面**（发帖 / 回复 / 采纳），**不做 AI 问答** ——
 * 见 {@code ORDERS.md} 一之十五 的边界三行与 {@code STANDARDS.md} §ENG-009 的 `R` 组。
 *
 * <p>★ {@code accepted_answer_id} 是**采纳**的落点（`R6` 的第三类端点依赖它）。
 */
@TableName("`question`")
public class Question {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联课程（FK→TB-04.id）。 */
    @TableField("course_id")
    private Long courseId;

    /** 提问者（FK→TB-01.id）。 */
    @TableField("author_id")
    private Long authorId;

    /** 标题（5 ~ 100 字）。 */
    private String title;

    /** 正文（10 ~ 5000 字）。 */
    private String content;

    /** 图片地址数组（JSON），最多 9 张。 */
    private String images;

    /** 状态：0 待解决、1 已解决、2 已关闭。 */
    private Integer status;

    /** 是否置顶。 */
    @TableField("is_top")
    private Integer isTop;

    /** 是否加精。 */
    @TableField("is_essence")
    private Integer isEssence;

    /** 浏览数。 */
    @TableField("view_count")
    private Integer viewCount;

    /** 回答数（冗余计数）。 */
    @TableField("answer_count")
    private Integer answerCount;

    /** 已采纳的回答标识。 */
    @TableField("accepted_answer_id")
    private Long acceptedAnswerId;

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

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getImages() {
        return images;
    }

    public void setImages(String images) {
        this.images = images;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getIsTop() {
        return isTop;
    }

    public void setIsTop(Integer isTop) {
        this.isTop = isTop;
    }

    public Integer getIsEssence() {
        return isEssence;
    }

    public void setIsEssence(Integer isEssence) {
        this.isEssence = isEssence;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public Integer getAnswerCount() {
        return answerCount;
    }

    public void setAnswerCount(Integer answerCount) {
        this.answerCount = answerCount;
    }

    public Long getAcceptedAnswerId() {
        return acceptedAnswerId;
    }

    public void setAcceptedAnswerId(Long acceptedAnswerId) {
        this.acceptedAnswerId = acceptedAnswerId;
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
