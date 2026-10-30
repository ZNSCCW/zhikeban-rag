package com.zhikeban.bishe.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikeban.bishe.domain.entity.Material;
import com.zhikeban.bishe.mapper.MaterialMapper;
import com.zhikeban.bishe.web.ApiException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 课程资料服务（任务②；`SRS` `FR-2` 子集：最小 CRUD / 列表）。
 *
 * <p>★★ **本单只登记元数据**：不落对象存储、不解析、不切片、**不做任何向量化或检索**。
 * 资料切片（{@code TB-07}）与向量表（{@code TB-27}/{@code TB-28}）属 RAG 链路，
 * 按 {@code §ENG-009} 边界 (a) **推后**。
 *
 * <p>★ {@code storage_path} 是"不对外暴露"的内部存储键（概设 §4.4 该列注释明写），
 * 故出参前**置空**。
 */
@Service
public class MaterialService {

    private final MaterialMapper materialMapper;

    public MaterialService(MaterialMapper materialMapper) {
        this.materialMapper = materialMapper;
    }

    /** 列表（按课程过滤；不传则全部）。 */
    public List<Material> list(Long courseId) {
        List<Material> rows = materialMapper.selectList(Wrappers.<Material>lambdaQuery()
                .eq(courseId != null, Material::getCourseId, courseId)
                .orderByDesc(Material::getId));
        rows.forEach(this::hideStoragePath);
        return rows;
    }

    /** 详情。 */
    public Material detail(Long id) {
        Material m = materialMapper.selectById(id);
        if (m == null) {
            throw ApiException.notFound("资料不存在：" + id);
        }
        return hideStoragePath(m);
    }

    /** 新建（元数据登记）。 */
    public Material create(Material req, Long operatorId) {
        if (req.getCourseId() == null) {
            throw ApiException.badRequest("courseId 不能为空");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw ApiException.badRequest("资料名称不能为空");
        }
        Material m = new Material();
        m.setCourseId(req.getCourseId());
        m.setName(req.getName());
        m.setType(req.getType() == null || req.getType().isBlank() ? "PDF" : req.getType());
        m.setSize(req.getSize() == null ? 0L : req.getSize());
        m.setStoragePath(req.getStoragePath() == null || req.getStoragePath().isBlank()
                ? "local://material/" + System.currentTimeMillis() : req.getStoragePath());
        m.setChapter(req.getChapter());
        m.setTags(req.getTags());
        m.setUploaderId(req.getUploaderId() != null ? req.getUploaderId() : operatorId);
        m.setParseStatus(0);
        m.setDownloadCount(0);
        m.setDeleted(0);
        materialMapper.insert(m);
        return hideStoragePath(m);
    }

    /** 改元数据（白名单：名称 / 类型 / 章节 / 标签）。 */
    public Material update(Long id, Material req) {
        Material m = materialMapper.selectById(id);
        if (m == null) {
            throw ApiException.notFound("资料不存在：" + id);
        }
        if (req.getName() != null && !req.getName().isBlank()) {
            m.setName(req.getName());
        }
        if (req.getType() != null && !req.getType().isBlank()) {
            m.setType(req.getType());
        }
        if (req.getChapter() != null) {
            m.setChapter(req.getChapter());
        }
        if (req.getTags() != null) {
            m.setTags(req.getTags());
        }
        materialMapper.updateById(m);
        return hideStoragePath(m);
    }

    /** 逻辑删除。 */
    public void delete(Long id) {
        detail(id);
        materialMapper.deleteById(id);
    }

    private Material hideStoragePath(Material m) {
        m.setStoragePath(null);
        return m;
    }
}
