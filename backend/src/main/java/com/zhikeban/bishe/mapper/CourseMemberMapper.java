package com.zhikeban.bishe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikeban.bishe.domain.entity.CourseMember;

/**
 * 课程成员 Mapper（`ENG-012`）：`CourseService.isMember` 的**唯一**数据来源。
 *
 * <p>★ 单表 CRUD，`logic-delete-field = deleted` 由全局配置接管（退课 = 逻辑删除）。
 */
public interface CourseMemberMapper extends BaseMapper<CourseMember> {
}
