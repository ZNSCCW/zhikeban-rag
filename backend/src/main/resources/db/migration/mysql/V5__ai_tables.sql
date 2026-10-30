-- ============================================================================
-- V5__ai_tables.sql —— Flyway 迁移脚本（**由 schema.sql 派生**的装载脚本，非事实源）
-- ----------------------------------------------------------------------------
-- ★★ 由 schema.sql 派生（★ schema.sql = 唯一 DDL 事实源）。
--   本文件的建表语句**逐字**取自根 schema.sql 的同名表；本文件**不新增、不改写、不删减**任何 DDL。
--   ⇒ 改表**先改 schema.sql**，再**同批**用生成器同步本文件（`work/ENG/probe-eng009/gen-migrations.mjs`）；
--     **不得**反向只改本文件，也**不得**新开第二套建表脚本 / Flyway 之外的迁移目录。
-- ★ 本脚本覆盖（AI 会话与反馈域（仅表结构））：TB-18 ai_chat / TB-19 ai_message / TB-20 ai_feedback
-- ★ 建表语句数与表号由生成器对账：schema.sql 的 28 张表在本目录 7 份脚本里**不重不漏**。
-- ★★ **只建表结构，不接通任何生成链路**：本单**不含 RAG 生成链路**，这些表今天**没有**对应的端点/服务/装配（`ENG-009` 边界 (a)，`R2`~`R4` 判据）。
-- ============================================================================

-- 表 4-18 AI 会话表（TB-18）
-- TB-18
CREATE TABLE IF NOT EXISTS `ai_chat` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话主键',
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '会话所有者（FK→TB-01.id）',
  `course_id`  BIGINT UNSIGNED NOT NULL COMMENT '提问所属课程，决定检索范围与权限（FK→TB-04.id）',
  `title`      VARCHAR(64)     NOT NULL COMMENT '会话标题（默认取首问前若干字）',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  KEY `idx_ai_chat_user_course` (`user_id`, `course_id`),
  CONSTRAINT `fk_ai_chat_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_ai_chat_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 会话表（TB-18）';

-- 表 4-19 AI 消息表（TB-19）
-- TB-19
CREATE TABLE IF NOT EXISTS `ai_message` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消息主键',
  `chat_id`    BIGINT UNSIGNED NOT NULL COMMENT '所属会话（FK→TB-18.id）',
  `role`       VARCHAR(16)     NOT NULL COMMENT '角色：user、assistant',
  `content`    TEXT            NOT NULL COMMENT '消息正文',
  `citations`  JSON            DEFAULT NULL COMMENT '引用数组（资料名、章节、页码、切片标识）',
  `tokens`     INT             DEFAULT NULL COMMENT '本次调用消耗的 token 数',
  `cost`       DECIMAL(10,4)   DEFAULT NULL COMMENT '本次调用成本（元）',
  `cache_hit`  TINYINT         NOT NULL DEFAULT 0 COMMENT '是否命中回答缓存',
  `useful_flag` TINYINT        DEFAULT NULL COMMENT '用户反馈标记：1 有用、0 无用',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `deleted`    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  KEY `idx_ai_message_chat` (`chat_id`),
  CONSTRAINT `fk_ai_message_chat` FOREIGN KEY (`chat_id`) REFERENCES `ai_chat` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 消息表（TB-19）';

-- 表 4-20 AI 回答反馈表（TB-20）
-- TB-20
CREATE TABLE IF NOT EXISTS `ai_feedback` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '反馈主键',
  `message_id` BIGINT UNSIGNED NOT NULL COMMENT '被反馈的 AI 消息（FK→TB-19.id）',
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '反馈人（FK→TB-01.id）',
  `useful`     TINYINT         NOT NULL COMMENT '1 有用、0 无用',
  `reason`     VARCHAR(255)    DEFAULT NULL COMMENT '反馈原因（选填）',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '反馈时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_feedback_message_user` (`message_id`, `user_id`),
  KEY `idx_ai_feedback_user` (`user_id`),
  CONSTRAINT `fk_ai_feedback_message` FOREIGN KEY (`message_id`) REFERENCES `ai_message` (`id`),
  CONSTRAINT `fk_ai_feedback_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 回答反馈表（TB-20）';
