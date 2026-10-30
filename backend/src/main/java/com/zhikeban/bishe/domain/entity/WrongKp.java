package com.zhikeban.bishe.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 错题-知识点关联实体（`SRS` `TB-16`，表名逐字取自 `schema.sql`：`wrong_kp`）。
 *
 * <p>★ 注意 `SRS §5.1` 把该表写作 `wrong_book_kp`，而 `schema.sql` 的建表语句是 `wrong_kp`
 * ⇒ 按 `§ENG-014` §2 ①「表名逐字取 `schema.sql`」以 `wrong_kp` 为准（**不改 DDL**）。
 *
 * <p>★ 联合主键 `(wrong_id, kp_id)`：`MyBatis-Plus` 的单主键模型不适合声明 `@TableId`，
 * 本实体只做**插入 / 按 `wrong_id` 删除**两种访问（`F1` 只要求 `BaseMapper&lt;WrongKp&gt;` 在位）。
 */
@TableName("wrong_kp")
public class WrongKp {

    /** 错题标识（FK→TB-15.id）。 */
    @TableField("wrong_id")
    private Long wrongId;

    /** 知识点标识（FK→TB-08.id）。 */
    @TableField("kp_id")
    private Long kpId;

    /** 关联时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getWrongId() {
        return wrongId;
    }

    public void setWrongId(Long wrongId) {
        this.wrongId = wrongId;
    }

    public Long getKpId() {
        return kpId;
    }

    public void setKpId(Long kpId) {
        this.kpId = kpId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
