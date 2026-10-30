package com.zhikeban.bishe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 时间源配置（`ENG-014`）。
 *
 * <p>★ 为什么需要一个 {@link Clock} Bean：掌握度的 {@code Δt} 是**整天差**（详设 §4.4.2），
 * "今天"必须由**服务端统一注入** ⇒ 生产用系统时区时钟，单测注入固定时钟即可复算金标值
 * （`§ENG-014` `A-5`：基准时刻参数注入、纯函数体内不得 `.now()`）。
 */
@Configuration
public class TimeConfig {

    /** 系统默认时区的时钟（`GMT+8` 环境；详设 §4.4.2「以数据库/服务器时间为准」）。 */
    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
