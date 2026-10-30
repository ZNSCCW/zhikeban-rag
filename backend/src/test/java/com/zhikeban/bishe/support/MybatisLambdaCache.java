package com.zhikeban.bishe.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhikeban.bishe.domain.entity.ReviewTask;
import com.zhikeban.bishe.domain.entity.WrongBook;
import com.zhikeban.bishe.domain.entity.WrongKp;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 单测用的 `MyBatis-Plus` 元数据预热（`ENG-014`）。
 *
 * <p>★ 为什么需要它（**如实说明边界**）：`LambdaUpdateWrapper.set(...)` 在**构造 wrapper 时**
 * 就要解析"实体字段 → 列名"，而该映射由容器启动时注册 `Mapper` 才有；
 * 纯 Mockito 单测**不起 Spring / 不起 DataSource** ⇒ 不预热会抛
 * {@code can not find lambda cache for this entity}。
 *
 * <p>★ 本类只做**元数据登记**（等价于容器启动时那一步），**不连库、不改变被测行为**：
 * 判定逻辑仍由被测服务与断言承担。
 */
public final class MybatisLambdaCache {

    private MybatisLambdaCache() {
    }

    /** 把本单三张表的 `TableInfo`（含字段→列映射）登记进全局缓存；可重复调用。 */
    public static synchronized void warmUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WrongBook.class);
        TableInfoHelper.initTableInfo(assistant, WrongKp.class);
        TableInfoHelper.initTableInfo(assistant, ReviewTask.class);
    }
}
