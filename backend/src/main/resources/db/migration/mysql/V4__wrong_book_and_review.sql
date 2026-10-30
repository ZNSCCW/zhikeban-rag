-- ============================================================================
-- V4__wrong_book_and_review.sql —— Flyway 迁移脚本（**由 schema.sql 派生**的装载脚本，非事实源）
-- ----------------------------------------------------------------------------
-- ★★ 由 schema.sql 派生（★ schema.sql = 唯一 DDL 事实源）。
--   本文件的建表语句**逐字**取自根 schema.sql 的同名表；本文件**不新增、不改写、不删减**任何 DDL。
--   ⇒ 改表**先改 schema.sql**，再**同批**用生成器同步本文件（`work/ENG/probe-eng009/gen-migrations.mjs`）；
--     **不得**反向只改本文件，也**不得**新开第二套建表脚本 / Flyway 之外的迁移目录。
-- ★ 本脚本覆盖（错题与复习域）：TB-15 wrong_book / TB-16 wrong_kp / TB-17 review_task
-- ★ 建表语句数与表号由生成器对账：schema.sql 的 28 张表在本目录 7 份脚本里**不重不漏**。
-- ★ 只建结构；复习调度算法属后续编码单。
-- ============================================================================

-- 表 4-15 错题本表（TB-15）
-- TB-15
CREATE TABLE IF NOT EXISTS `wrong_book` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '错题主键',
  `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '所属学生（FK→TB-01.id）',
  `course_id`       BIGINT UNSIGNED NOT NULL COMMENT '所属课程（FK→TB-04.id）',
  `source_type`     VARCHAR(16)     NOT NULL COMMENT '来源：QUESTION、AI、MANUAL',
  `source_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '来源对象标识（多态关联：QUESTION→TB-09、AI→TB-19、MANUAL→无）',
  `question_content` TEXT           NOT NULL COMMENT '题目内容快照',
  `my_answer`       TEXT            DEFAULT NULL COMMENT '我的答案',
  `right_answer`    TEXT            DEFAULT NULL COMMENT '正确答案',
  `error_reason`    VARCHAR(255)    DEFAULT NULL COMMENT '错误原因',
  `mastery_level`   TINYINT         NOT NULL DEFAULT 0 COMMENT '掌握等级：0 未掌握 ~ 3 已掌握',
  `mastery_score`   DECIMAL(5,2)    NOT NULL DEFAULT 0 COMMENT '掌握度分值，区间 0 ~ 100',
  `status`          TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0 待复习、1 已掌握、2 已归档',
  `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  KEY `idx_wrong_book_user_course` (`user_id`, `course_id`),
  CONSTRAINT `fk_wrong_book_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_wrong_book_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
  CONSTRAINT `ck_wrong_book_mastery_level` CHECK (`mastery_level` BETWEEN 0 AND 3),
  CONSTRAINT `ck_wrong_book_mastery_score` CHECK (`mastery_score` BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='错题本表（TB-15）';

-- 表 4-16 错题知识点关联表（TB-16）
-- TB-16
CREATE TABLE IF NOT EXISTS `wrong_kp` (
  `wrong_id`   BIGINT UNSIGNED NOT NULL COMMENT '错题标识（FK→TB-15.id）',
  `kp_id`      BIGINT UNSIGNED NOT NULL COMMENT '知识点标识（FK→TB-08.id）',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '关联时间',
  PRIMARY KEY (`wrong_id`, `kp_id`),
  KEY `idx_wrong_kp_kp` (`kp_id`),
  CONSTRAINT `fk_wrong_kp_wrong` FOREIGN KEY (`wrong_id`) REFERENCES `wrong_book` (`id`),
  CONSTRAINT `fk_wrong_kp_kp` FOREIGN KEY (`kp_id`) REFERENCES `knowledge_point` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='错题知识点关联表（TB-16）';

-- 表 4-17 复习任务表（TB-17）
-- TB-17
CREATE TABLE IF NOT EXISTS `review_task` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '复习任务主键',
  `wrong_id`    BIGINT UNSIGNED NOT NULL COMMENT '对应错题（FK→TB-15.id）',
  `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '所属学生（FK→TB-01.id）',
  `plan_date`   DATE            NOT NULL COMMENT '计划复习日期',
  `stage`       TINYINT         NOT NULL COMMENT '复习节点：1、2、4、7、15、30 天',
  `status`      TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0 待复习、1 已完成、2 已跳过',
  `result`      TINYINT         DEFAULT NULL COMMENT '复习结果：0 仍不会、1 模糊、2 已掌握',
  `reviewed_at` DATETIME(3)     DEFAULT NULL COMMENT '实际复习时间',
  `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_review_task_user_plan_status` (`user_id`, `plan_date`, `status`),
  KEY `idx_review_task_wrong` (`wrong_id`),
  CONSTRAINT `fk_review_task_wrong` FOREIGN KEY (`wrong_id`) REFERENCES `wrong_book` (`id`),
  CONSTRAINT `fk_review_task_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='复习任务表（TB-17）';
