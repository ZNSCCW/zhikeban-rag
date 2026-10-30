-- ============================================================================
-- V1__auth_and_user.sql —— Flyway 迁移脚本（**由 schema.sql 派生**的装载脚本，非事实源）
-- ----------------------------------------------------------------------------
-- ★★ 由 schema.sql 派生（★ schema.sql = 唯一 DDL 事实源）。
--   本文件的建表语句**逐字**取自根 schema.sql 的同名表；本文件**不新增、不改写、不删减**任何 DDL。
--   ⇒ 改表**先改 schema.sql**，再**同批**用生成器同步本文件（`work/ENG/probe-eng009/gen-migrations.mjs`）；
--     **不得**反向只改本文件，也**不得**新开第二套建表脚本 / Flyway 之外的迁移目录。
-- ★ 本脚本覆盖（认证与用户域）：TB-01 user / TB-02 role / TB-03 user_role
-- ★ 建表语句数与表号由生成器对账：schema.sql 的 28 张表在本目录 7 份脚本里**不重不漏**。
-- ★ 本脚本只做表结构：不含 CREATE DATABASE / DROP DATABASE / USE（建库由部署侧负责）。
-- ============================================================================

-- 表 4-1 用户表（TB-01）
-- TB-01
CREATE TABLE IF NOT EXISTS `user` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户主键',
  `username`   VARCHAR(32)     NOT NULL COMMENT '登录名（学号或工号）',
  `password`   VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密文，任何日志与接口响应不得出现明文',
  `nickname`   VARCHAR(32)     NOT NULL COMMENT '昵称，展示用',
  `avatar`     VARCHAR(255)    DEFAULT NULL COMMENT '头像访问地址',
  `email`      VARCHAR(128)    DEFAULT NULL COMMENT '邮箱，用于找回密码与通知',
  `college`    VARCHAR(64)     DEFAULT NULL COMMENT '学院',
  `major`      VARCHAR(64)     DEFAULT NULL COMMENT '专业',
  `status`     TINYINT         NOT NULL DEFAULT 1 COMMENT '账号状态：1 正常、0 禁用',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  UNIQUE KEY `uk_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表（TB-01）';

-- 表 4-2 角色表（TB-02）
-- TB-02
CREATE TABLE IF NOT EXISTS `role` (
  `id`         INT          NOT NULL AUTO_INCREMENT COMMENT '角色主键（角色量级小，不用 BIGINT）',
  `code`       VARCHAR(16)  NOT NULL COMMENT '角色短码：STUDENT、TEACHER、ADMIN',
  `name`       VARCHAR(32)  NOT NULL COMMENT '角色名称',
  `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表（TB-02）';

-- 表 4-3 用户角色关联表（TB-03）
-- TB-03
CREATE TABLE IF NOT EXISTS `user_role` (
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '用户标识（FK→TB-01.id）',
  `role_id`    INT             NOT NULL COMMENT '角色标识（FK→TB-02.id）',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_user_role_role` (`role_id`),
  CONSTRAINT `fk_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表（TB-03）';
