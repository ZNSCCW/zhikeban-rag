package com.zhikeban.bishe.config;

import com.zhikeban.bishe.service.MasteryCalculator;
import com.zhikeban.bishe.service.ReviewNodePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 错题/复习域的**无状态组件**注册（`ENG-014`）。
 *
 * <p>★ `MasteryCalculator` 与 `ReviewNodePolicy` 都是**纯函数 / 无状态策略**（详设 §3.5 设计决定 2、
 * §4.4.3）⇒ 它们**不写 `@Service`/`@Component`**，由本配置显式声明为 Bean：
 * 计算与策略类因此不依赖框架注解（`§ENG-014` `M6` 的"纯函数"要求），而容器仍可注入与替换。
 */
@Configuration
public class DomainBeansConfig {

    /** 掌握度计算纯函数（基准时刻由调用方注入 ⇒ Bean 本身无状态）。 */
    @Bean
    public MasteryCalculator masteryCalculator() {
        return new MasteryCalculator();
    }

    /** 复习节点策略（1/2/4/7/15/30 天 ＋ 「仍需巩固」重置）。 */
    @Bean
    public ReviewNodePolicy reviewNodePolicy() {
        return new ReviewNodePolicy();
    }
}
