package com.zhikeban.bishe.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 任务② 课程资料实体（对应概设 §4.4 表 4-6，{@code TB-06}，{@code material}）。
 *
 * <p>★ {@code ENG-009} 只做**最小 CRUD / 列表**：元数据登记与查询。
 * **不落 MinIO、不做解析、不写切片**（切片与向量化属 RAG 链路，本单边界外）。
 *
 * <p>★ {@code storage_path} 在接口响应里**不返回**（见 {@code MaterialService} 的说明）。
 */
@TableName("`material`")
public class Material {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属课程（FK→TB-04.id）。 */
    @TableField("course_id")
    private Long courseId;

    /** 资料名称（含扩展名）。 */
    private String name;

    /** 资料类型：PDF、PPT、DOC、VIDEO。 */
    private String type;

    /** 字节数。 */
    private Long size;

    /** 存储键（MinIO 对象名或本地相对路径），不对外暴露。 */
    @TableField("storage_path")
    private String storagePath;

    /** 所属章节。 */
    private String chapter;

    /** 标签数组（JSON）。 */
    private String tags;

    /** 上传者（FK→TB-01.id）。 */
    @TableField("uploader_id")
    private Long uploaderId;

    /** 解析状态：0 待解析、1 成功、2 失败。 */
    @TableField("parse_status")
    private Integer parseStatus;

    /** 下载次数。 */
    @TableField("download_count")
    private Integer downloadCount;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getChapter() {
        return chapter;
    }

    public void setChapter(String chapter) {
        this.chapter = chapter;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Long getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(Long uploaderId) {
        this.uploaderId = uploaderId;
    }

    public Integer getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(Integer parseStatus) {
        this.parseStatus = parseStatus;
    }

    public Integer getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(Integer downloadCount) {
        this.downloadCount = downloadCount;
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
