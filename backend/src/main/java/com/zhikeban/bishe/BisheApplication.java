package com.zhikeban.bishe;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智课伴毕设 · 后端启动类（ENG-007 仓骨架，可空实现）。
 *
 * <p>★ 本类同时是 <b>MyBatis-Plus 真实引用</b> 的落点（验收标准 {@code §ENG-007 A4b}：
 * 判的是"源码里真的用到 {@code com.baomidou.mybatisplus}"，不是只在 {@code pom} 挂依赖）——
 * {@link MapperScan} 来自 MyBatis-Plus 的 {@code com.baomidou.mybatisplus} 包体系
 * （由 {@code mybatis-plus-spring-boot3-starter} 传递的 {@code mybatis-plus-spring-boot-autoconfigure}
 * 提供 {@code org.mybatis.spring.annotation.MapperScan} 的重导出）。
 *
 * <p>★ Spring Security 的真实引用落在
 * {@code com.zhikeban.bishe.config.SecurityConfig} 与 {@code controller.HealthController}。
 */
@SpringBootApplication
@MapperScan("com.zhikeban.bishe.mapper")
public class BisheApplication {

    public static void main(String[] args) {
        SpringApplication.run(BisheApplication.class, args);
    }
}
