package com.zhikeban.bishe.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 课程成员实体（`ENG-012`：RAG **权限过滤**的权威判据来源；对应概设 §4.4 表 4-5，{@code TB-05}，`course_member`）。
 *
 * <p>★ 详设 §3.2：`isMember(courseId, userId)` 是**全系统唯一的成员判定入口** ——
 * RAG 权限过滤、错题归属校验、统计接口都复用它，避免"每个模块各写一套判断"。
 *
 * <p>★ 退课采用**逻辑删除**（`deleted = 1`）⇒ 成员判定必须带 `deleted = 0`。
 */
@TableName("`course_member`")
public class CourseMember {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属课程（FK→TB-04.id）。 */
    @TableField("course_id")
    private Long courseId;

    /** 成员用户（FK→TB-01.id）。 */
    @TableField("user_id")
    private Long userId;

    /** 课程内角色：STUDENT、ASSISTANT。 */
    @TableField("role_in_course")
    private String roleInCourse;

    @TableField("joined_at")
    private LocalDateTime joinedAt;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRoleInCourse() {
        return roleInCourse;
    }

    public void setRoleInCourse(String roleInCourse) {
        this.roleInCourse = roleInCourse;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
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
