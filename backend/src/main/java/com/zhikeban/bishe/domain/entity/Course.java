package com.zhikeban.bishe.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 任务② 课程实体（对应概设 §4.4 表 4-4，{@code TB-04}，{@code course}）。
 *
 * <p>★ {@code ENG-009} 只做 **MVP 一 的最小闭环**（列表 / 新建 / 详情 / 改基本信息），
 * 不做选课审批、邀请码重置、知识点树等完整业务（那些属后续编码单）。
 *
 * <p>★ 表结构以根 {@code schema.sql} 为**唯一 DDL 事实源**（见该文件头部声明）；
 * 本类字段与之一一对应，不得反向发明列。
 */
@TableName("`course`")
public class Course {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 课程名称。 */
    private String name;

    /** 授课教师（FK→TB-01.id）。 */
    @TableField("teacher_id")
    private Long teacherId;

    /** 学期，如 2026-2027-1。 */
    private String semester;

    /** 学分。 */
    private java.math.BigDecimal credit;

    /** 封面图地址。 */
    private String cover;

    /** 加课邀请码，可重置。 */
    @TableField("invite_code")
    private String inviteCode;

    /** 邀请码失效时间。 */
    @TableField("invite_expire_at")
    private LocalDateTime inviteExpireAt;

    /** 课程状态：1 进行中、0 已结课。 */
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 逻辑删除标志。 */
    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public java.math.BigDecimal getCredit() {
        return credit;
    }

    public void setCredit(java.math.BigDecimal credit) {
        this.credit = credit;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public LocalDateTime getInviteExpireAt() {
        return inviteExpireAt;
    }

    public void setInviteExpireAt(LocalDateTime inviteExpireAt) {
        this.inviteExpireAt = inviteExpireAt;
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
