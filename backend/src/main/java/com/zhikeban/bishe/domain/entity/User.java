package com.zhikeban.bishe.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户实体骨架 —— 对应概设 §4.4 表 4-1（{@code TB-01}，{@code user}）。
 *
 * <p>★ 本类同时是 <b>MyBatis-Plus 真实引用</b> 的落点（验收标准 {@code §ENG-007 A4b}：
 * 判的是"源码里真的用到 {@code com.baomidou.mybatisplus}"）。
 *
 * <p>★ 可空实现：字段与概设 §4.4 的 {@code TB-01} 表对齐，**不含任何业务方法**；
 * 逐列一致性属另一张单（{@code §ENG-007} 的 {@code P2}）。
 */
@TableName("`user`")
public class User {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 登录名（学号或工号）。 */
    private String username;

    /** BCrypt 密文，任何日志与接口响应不得出现明文。 */
    private String password;

    private String nickname;

    private String avatar;

    private String email;

    private String college;

    private String major;

    /** 账号状态：1 正常、0 禁用。 */
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 逻辑删除标志（{@code TINYINT}，0 = 未删除）。 */
    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCollege() {
        return college;
    }

    public void setCollege(String college) {
        this.college = college;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
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
