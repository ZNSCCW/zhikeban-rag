package com.zhikeban.bishe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikeban.bishe.domain.entity.User;

/**
 * 用户 Mapper 骨架（MyBatis-Plus {@code BaseMapper}）。
 *
 * <p>★ 可空实现：**不写任何自定义 SQL**；{@code BaseMapper} 已提供单表 CRUD。
 * 扫描入口见 {@code BisheApplication} 的 {@code @MapperScan("com.zhikeban.bishe.mapper")}。
 */
public interface UserMapper extends BaseMapper<User> {
}
