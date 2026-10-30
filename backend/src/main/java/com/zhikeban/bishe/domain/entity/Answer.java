package com.zhikeban.bishe.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 任务③ 回答实体（对应概设 §4.4 表 4-11，{@code TB-11}，{@code answer}）。
 *
 * <p>★ 采纳的两处落点：本表的 {@code is_accepted} 与 {@code question.accepted_answer_id}
 * （`R6` 的第三类端点 {@code /accept} 同时更新两者，见 {@code QuestionService#accept}）。
 */
@TableName("`answer`")
public class Answer {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属提问（FK→TB-09.id）。 */
    @TableField("question_id")
    private Long questionId;

    /** 回答者（FK→TB-01.id）。 */
    @TableField("author_id")
    private Long authorId;

    /** 回答正文，支持富文本。 */
    private String content;

    /** 图片地址数组（JSON）。 */
    private String images;

    /** 是否被采纳。 */
    @TableField("is_accepted")
    private Integer isAccepted;

    /** 赞同数（冗余计数）。 */
    @TableField("vote_up")
    private Integer voteUp;

    /** 反对数（冗余计数）。 */
    @TableField("vote_down")
    private Integer voteDown;

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

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
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

    public Integer getIsAccepted() {
        return isAccepted;
    }

    public void setIsAccepted(Integer isAccepted) {
        this.isAccepted = isAccepted;
    }

    public Integer getVoteUp() {
        return voteUp;
    }

    public void setVoteUp(Integer voteUp) {
        this.voteUp = voteUp;
    }

    public Integer getVoteDown() {
        return voteDown;
    }

    public void setVoteDown(Integer voteDown) {
        this.voteDown = voteDown;
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
