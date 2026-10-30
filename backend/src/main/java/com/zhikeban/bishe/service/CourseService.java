package com.zhikeban.bishe.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikeban.bishe.domain.entity.Course;
import com.zhikeban.bishe.domain.entity.CourseMember;
import com.zhikeban.bishe.mapper.CourseMapper;
import com.zhikeban.bishe.mapper.CourseMemberMapper;
import com.zhikeban.bishe.web.ApiException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;

/**
 * 课程服务（任务②；`SRS` `FR-2` 子集：最小 CRUD / 列表）。
 *
 * <p>★ 范围刻意收窄：**列表 / 新建 / 详情 / 改基本信息 / 成员判定**。
 * 选课审批、邀请码重置、知识点树等属后续编码单，本单不做。
 *
 * <p>★★ `ENG-012` 任务①：新增 {@link #isMember(Long, Long)} —— 详设 §3.2 规定的
 * **全系统唯一成员判定入口**（RAG 权限过滤、错题归属校验、统计接口都复用它）。
 * 它是 `PermissionFilter` 的权威判据来源 ⇒ **越权防线只有一份实现**。
 */
@Service
public class CourseService {

    private static final String INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    private final CourseMapper courseMapper;
    private final CourseMemberMapper courseMemberMapper;

    public CourseService(CourseMapper courseMapper, CourseMemberMapper courseMemberMapper) {
        this.courseMapper = courseMapper;
        this.courseMemberMapper = courseMemberMapper;
    }

    /** 列表（可按教师过滤；不传则全部）。 */
    public List<Course> list(Long teacherId) {
        return courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(teacherId != null, Course::getTeacherId, teacherId)
                .orderByDesc(Course::getId));
    }

    /** 详情。 */
    public Course detail(Long id) {
        Course c = courseMapper.selectById(id);
        if (c == null) {
            throw ApiException.notFound("课程不存在：" + id);
        }
        return c;
    }

    /** 新建（邀请码自动生成，8 位，去掉易混字符）。 */
    public Course create(Course req, Long operatorId) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw ApiException.badRequest("课程名称不能为空");
        }
        Course c = new Course();
        c.setName(req.getName());
        c.setTeacherId(req.getTeacherId() != null ? req.getTeacherId() : operatorId);
        c.setSemester(req.getSemester() == null || req.getSemester().isBlank() ? "未指定学期" : req.getSemester());
        c.setCredit(req.getCredit());
        c.setCover(req.getCover());
        c.setInviteCode(req.getInviteCode() == null || req.getInviteCode().isBlank()
                ? newInviteCode() : req.getInviteCode());
        c.setInviteExpireAt(req.getInviteExpireAt());
        c.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        c.setDeleted(0);
        courseMapper.insert(c);
        return c;
    }

    /** 改基本信息（**只允许改这四个白名单字段**，避免把 teacher_id 等被改坏）。 */
    public Course update(Long id, Course req) {
        Course c = detail(id);
        if (req.getName() != null && !req.getName().isBlank()) {
            c.setName(req.getName());
        }
        if (req.getSemester() != null && !req.getSemester().isBlank()) {
            c.setSemester(req.getSemester());
        }
        if (req.getCredit() != null) {
            c.setCredit(req.getCredit());
        }
        if (req.getCover() != null) {
            c.setCover(req.getCover());
        }
        courseMapper.updateById(c);
        return c;
    }

    /** 逻辑删除。 */
    public void delete(Long id) {
        detail(id);
        courseMapper.deleteById(id);
    }

    /**
     * **成员判定（全系统唯一入口）** —— 详设 §3.2／§4.3.4 的权威口径。
     *
     * <p>★ 授课教师天然是成员（课程创建时不会给自己写一条 `course_member`）；
     * 其余人查 `course_member` 且**只认未退课**（`deleted = 0`，逻辑删除由全局配置接管）。
     *
     * <p>★ 返回 {@code false} 的含义是"**不确认是成员**"⇒ 调用方必须按**宁空不漏**处理：
     * RAG 链路的 `PermissionFilter` 收到假就丢弃全部候选片段，绝不"放行后再说"。
     *
     * @param courseId 课程标识
     * @param userId   用户标识
     * @return 是（教师或未退课的成员）则真
     */
    public boolean isMember(Long courseId, Long userId) {
        if (courseId == null || userId == null) {
            return false;
        }
        Course course = courseMapper.selectById(courseId);
        if (course != null && userId.equals(course.getTeacherId())) {
            return true;
        }
        Long n = courseMemberMapper.selectCount(Wrappers.<CourseMember>lambdaQuery()
                .eq(CourseMember::getCourseId, courseId)
                .eq(CourseMember::getUserId, userId));
        return n != null && n > 0;
    }

    private String newInviteCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(INVITE_ALPHABET.charAt(random.nextInt(INVITE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
