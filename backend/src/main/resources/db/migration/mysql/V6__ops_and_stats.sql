-- ============================================================================
-- V6__ops_and_stats.sql —— Flyway 迁移脚本（**由 schema.sql 派生**的装载脚本，非事实源）
-- ----------------------------------------------------------------------------
-- ★★ 由 schema.sql 派生（★ schema.sql = 唯一 DDL 事实源）。
--   本文件的建表语句**逐字**取自根 schema.sql 的同名表；本文件**不新增、不改写、不删减**任何 DDL。
--   ⇒ 改表**先改 schema.sql**，再**同批**用生成器同步本文件（`work/ENG/probe-eng009/gen-migrations.mjs`）；
--     **不得**反向只改本文件，也**不得**新开第二套建表脚本 / Flyway 之外的迁移目录。
-- ★ 本脚本覆盖（通知、举报与运维统计域）：TB-21 notification / TB-22 report / TB-23 sensitive_word / TB-24 operation_log / TB-25 daily_statistics / TB-26 system_config
-- ★ 建表语句数与表号由生成器对账：schema.sql 的 28 张表在本目录 7 份脚本里**不重不漏**。
-- ★ 只建结构；运维后台与统计作业属后续编码单。
-- ============================================================================

-- 表 4-21 通知表（TB-21）
-- TB-21
CREATE TABLE IF NOT EXISTS `notification` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '通知主键',
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '接收人（FK→TB-01.id）',
  `type`       VARCHAR(16)     NOT NULL COMMENT '通知类型：ANSWER、ACCEPT、COMMENT、REPORT、SYSTEM',
  `title`      VARCHAR(64)     NOT NULL COMMENT '通知标题',
  `content`    VARCHAR(255)    DEFAULT NULL COMMENT '通知正文',
  `link`       VARCHAR(255)    DEFAULT NULL COMMENT '跳转地址',
  `is_read`    TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已读',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `deleted`    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  KEY `idx_notification_user_read` (`user_id`, `is_read`),
  CONSTRAINT `fk_notification_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知表（TB-21）';

-- 表 4-22 举报表（TB-22）
-- TB-22
CREATE TABLE IF NOT EXISTS `report` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '举报主键',
  `reporter_id`   BIGINT UNSIGNED NOT NULL COMMENT '举报人（FK→TB-01.id）',
  `target_type`   VARCHAR(16)     NOT NULL COMMENT '目标类型：QUESTION、ANSWER、COMMENT、USER',
  `target_id`     BIGINT UNSIGNED NOT NULL COMMENT '目标主键（多态关联）',
  `reason_type`   VARCHAR(32)     NOT NULL COMMENT '举报原因分类',
  `description`   VARCHAR(500)    DEFAULT NULL COMMENT '补充说明',
  `status`        TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0 待处理、1 已处理、2 已驳回',
  `handler_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '处理人（管理员，FK→TB-01.id）',
  `handle_result` VARCHAR(255)    DEFAULT NULL COMMENT '处理结果说明',
  `handled_at`    DATETIME(3)     DEFAULT NULL COMMENT '处理时间',
  `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_report_status_created` (`status`, `created_at`),
  KEY `idx_report_reporter` (`reporter_id`),
  KEY `idx_report_handler` (`handler_id`),
  CONSTRAINT `fk_report_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_report_handler` FOREIGN KEY (`handler_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='举报表（TB-22）';

-- 表 4-23 敏感词表（TB-23）
-- TB-23
CREATE TABLE IF NOT EXISTS `sensitive_word` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '敏感词主键',
  `word`       VARCHAR(64)     NOT NULL COMMENT '词条或正则表达式',
  `match_type` VARCHAR(16)     NOT NULL DEFAULT 'EXACT' COMMENT '匹配方式：EXACT、REGEX',
  `action`     VARCHAR(16)     NOT NULL DEFAULT 'REVIEW' COMMENT '命中动作：REPLACE、REJECT、REVIEW',
  `enabled`    TINYINT         NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sensitive_word_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感词表（TB-23）';

-- 表 4-24 操作日志表（TB-24）
-- TB-24
CREATE TABLE IF NOT EXISTS `operation_log` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  `user_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人（系统任务可为空；FK→TB-01.id）',
  `ip`         VARCHAR(45)     DEFAULT NULL COMMENT '客户端地址，兼容 IPv6',
  `module`     VARCHAR(32)     NOT NULL COMMENT '业务模块标识',
  `action`     VARCHAR(32)     NOT NULL COMMENT '动作标识',
  `target_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '目标对象标识',
  `result`     TINYINT         NOT NULL DEFAULT 1 COMMENT '结果：1 成功、0 失败',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_operation_log_user_created` (`user_id`, `created_at`),
  KEY `idx_operation_log_module_action` (`module`, `action`),
  CONSTRAINT `fk_operation_log_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表（TB-24）';

-- 表 4-25 日统计表（TB-25）
-- TB-25
CREATE TABLE IF NOT EXISTS `daily_statistics` (
  `stat_date`      DATE         NOT NULL COMMENT '统计日期，按天唯一',
  `active_users`   INT          NOT NULL DEFAULT 0 COMMENT '活跃用户数',
  `new_users`      INT          NOT NULL DEFAULT 0 COMMENT '新增用户数',
  `question_count` INT          NOT NULL DEFAULT 0 COMMENT '当日提问数',
  `solved_rate`    DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '问题解决率（百分比，取值区间 0 ~ 100）',
  `ai_calls`       INT          NOT NULL DEFAULT 0 COMMENT 'AI 调用次数',
  `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`stat_date`),
  CONSTRAINT `ck_daily_statistics_solved_rate` CHECK (`solved_rate` BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='日统计表（TB-25）';

-- 表 4-26 系统配置表（TB-26）
-- TB-26
CREATE TABLE IF NOT EXISTS `system_config` (
  `config_key`   VARCHAR(64)  NOT NULL COMMENT '配置键，唯一',
  `config_value` VARCHAR(512) NOT NULL COMMENT '配置值（字符串，复杂结构用 JSON 文本）',
  `description`  VARCHAR(255) DEFAULT NULL COMMENT '配置说明',
  `updated_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表（TB-26）';
