package com.zhikeban.bishe.rag;

import com.zhikeban.bishe.domain.entity.Material;
import com.zhikeban.bishe.mapper.MaterialMapper;
import com.zhikeban.bishe.service.CourseService;
import com.zhikeban.bishe.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * **权限过滤**（`ENG-012` 任务①；详设 §4.3.4）—— **全链路唯一的安全咽喉**。
 *
 * <p>★ 三级过滤：
 * <ol>
 *   <li><b>课程级</b>：本次请求的 {@code courseId} 白名单；</li>
 *   <li><b>资料级</b>：按 {@code chunk.materialId} 反查 {@code material.course_id}，
 *       剔除不属于本次可见课程的片段（防"向量侧短暂滞后"造成越权）；</li>
 *   <li><b>成员级</b>：{@code CourseService.isMember(courseId, userId)} —— 非成员直接拒（40301）。</li>
 * </ol>
 *
 * <p>★ **兜底 = 宁空不漏**：除鉴权结论外的任何异常一律**返回空列表**，
 * 绝不让"没过滤干净"的片段进入提示词。这条与 `VectorStore` 只做课程级预过滤合起来，
 * 才是 `NFR-REL-08` 的"接口与业务代码零改动"。
 */
@Service
public class PermissionFilter {

    private static final Logger log = LoggerFactory.getLogger(PermissionFilter.class);

    private final CourseService courseService;
    private final MaterialMapper materialMapper;

    public PermissionFilter(CourseService courseService, MaterialMapper materialMapper) {
        this.courseService = courseService;
        this.materialMapper = materialMapper;
    }

    /**
     * 过滤候选片段。
     *
     * @param hits     融合后的候选（可能来自向量一路或全文一路）
     * @param userId   提问用户
     * @param courseId 本次请求的课程
     * @return 过滤后的片段（**非成员时抛 403；其它异常时返回空列表**）
     */
    public List<RetrievedChunk> apply(List<RetrievedChunk> hits, Long userId, Long courseId) {
        try {
            if (hits == null || hits.isEmpty()) {
                return List.of();
            }
            if (!courseService.isMember(courseId, userId)) {
                throw ApiException.forbidden("非课程成员，无权检索该课程资料（40301）");
            }
            Set<Long> materialIds = new LinkedHashSet<>();
            for (RetrievedChunk c : hits) {
                if (c.materialId() != null && c.materialId() > 0) {
                    materialIds.add(c.materialId());
                }
            }
            Map<Long, Long> courseOfMaterial = new HashMap<>();
            if (!materialIds.isEmpty()) {
                for (Material m : materialMapper.selectBatchIds(materialIds)) {
                    courseOfMaterial.put(m.getId(), m.getCourseId());
                }
            }
            List<RetrievedChunk> kept = new ArrayList<>(hits.size());
            int dropped = 0;
            for (RetrievedChunk c : hits) {
                Long belongs = courseOfMaterial.get(c.materialId());
                if (belongs != null && belongs.equals(courseId)) {
                    kept.add(c);
                } else {
                    dropped++;
                }
            }
            if (dropped > 0) {
                log.warn("权限过滤剔除了 {} 条不属于课程 {} 的候选片段（宁空不漏）", dropped, courseId);
            }
            return kept;
        } catch (ApiException e) {
            // 鉴权结论必须上抛：不能被"宁空不漏"的兜底吞掉（否则越权请求会得到 200 + 空结果）
            throw e;
        } catch (RuntimeException e) {
            log.warn("权限过滤出现异常，按宁空不漏处理（丢弃全部候选）：{}", e.getMessage());
            return List.of();
        }
    }
}
