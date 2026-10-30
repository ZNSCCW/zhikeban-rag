-- ============================================================================
-- V3__community.sql —— Flyway 迁移脚本（**由 schema.sql 派生**的装载脚本，非事实源）
-- ----------------------------------------------------------------------------
-- ★★ 由 schema.sql 派生（★ schema.sql = 唯一 DDL 事实源）。
--   本文件的建表语句**逐字**取自根 schema.sql 的同名表；本文件**不新增、不改写、不删减**任何 DDL。
--   ⇒ 改表**先改 schema.sql**，再**同批**用生成器同步本文件（`work/ENG/probe-eng009/gen-migrations.mjs`）；
--     **不得**反向只改本文件，也**不得**新开第二套建表脚本 / Flyway 之外的迁移目录。
-- ★ 本脚本覆盖（问答社区域）：TB-09 question / TB-10 question_kp / TB-11 answer / TB-12 comment / TB-13 vote / TB-14 favorite
-- ★ 建表语句数与表号由生成器对账：schema.sql 的 28 张表在本目录 7 份脚本里**不重不漏**。
-- ★ 问答**只做社区面**（发帖/回复/采纳），**不含 AI 问答**（`ENG-009` 边界 (b)）。
-- ============================================================================

-- 表 4-9 提问表（TB-09）
-- TB-09
CREATE TABLE IF NOT EXISTS `question` (
  `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '提问主键',
  `course_id`          BIGINT UNSIGNED NOT NULL COMMENT '关联课程（FK→TB-04.id）',
  `author_id`          BIGINT UNSIGNED NOT NULL COMMENT '提问者（FK→TB-01.id）',
  `title`              VARCHAR(100)    NOT NULL COMMENT '标题（5 ~ 100 字）',
  `content`            TEXT            NOT NULL COMMENT '正文（10 ~ 5000 字）',
  `images`             JSON            DEFAULT NULL COMMENT '图片地址数组，最多 9 张',
  `status`             TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0 待解决、1 已解决、2 已关闭',
  `is_top`             TINYINT         NOT NULL DEFAULT 0 COMMENT '是否置顶',
  `is_essence`         TINYINT         NOT NULL DEFAULT 0 COMMENT '是否加精',
  `view_count`         INT             NOT NULL DEFAULT 0 COMMENT '浏览数',
  `answer_count`       INT             NOT NULL DEFAULT 0 COMMENT '回答数（冗余计数）',
  `accepted_answer_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '已采纳的回答标识',
  `created_at`         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`            TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  KEY `idx_question_course_status_created` (`course_id`, `status`, `created_at`),
  KEY `idx_question_author` (`author_id`),
  CONSTRAINT `fk_question_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
  CONSTRAINT `fk_question_author` FOREIGN KEY (`author_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提问表（TB-09）';

-- 表 4-10 问题知识点关联表（TB-10）
-- TB-10
CREATE TABLE IF NOT EXISTS `question_kp` (
  `question_id` BIGINT UNSIGNED NOT NULL COMMENT '提问标识（FK→TB-09.id）',
  `kp_id`       BIGINT UNSIGNED NOT NULL COMMENT '知识点标识（FK→TB-08.id）',
  `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '打标时间',
  PRIMARY KEY (`question_id`, `kp_id`),
  KEY `idx_question_kp_kp` (`kp_id`),
  CONSTRAINT `fk_question_kp_question` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`),
  CONSTRAINT `fk_question_kp_kp` FOREIGN KEY (`kp_id`) REFERENCES `knowledge_point` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问题知识点关联表（TB-10）';

-- 表 4-11 回答表（TB-11）
-- TB-11
CREATE TABLE IF NOT EXISTS `answer` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '回答主键',
  `question_id` BIGINT UNSIGNED NOT NULL COMMENT '所属提问（FK→TB-09.id）',
  `author_id`   BIGINT UNSIGNED NOT NULL COMMENT '回答者（FK→TB-01.id）',
  `content`     TEXT            NOT NULL COMMENT '回答正文，支持富文本',
  `images`      JSON            DEFAULT NULL COMMENT '图片地址数组',
  `is_accepted` TINYINT         NOT NULL DEFAULT 0 COMMENT '是否被采纳',
  `vote_up`     INT             NOT NULL DEFAULT 0 COMMENT '赞同数（冗余计数）',
  `vote_down`   INT             NOT NULL DEFAULT 0 COMMENT '反对数（冗余计数）',
  `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  KEY `idx_answer_question` (`question_id`),
  KEY `idx_answer_author` (`author_id`),
  CONSTRAINT `fk_answer_question` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`),
  CONSTRAINT `fk_answer_author` FOREIGN KEY (`author_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='回答表（TB-11）';

-- 表 4-12 评论表（TB-12）
-- TB-12
CREATE TABLE IF NOT EXISTS `comment` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评论主键',
  `target_type` VARCHAR(16)     NOT NULL COMMENT '目标类型：QUESTION、ANSWER',
  `target_id`   BIGINT UNSIGNED NOT NULL COMMENT '目标主键（多态关联，不建物理外键）',
  `author_id`   BIGINT UNSIGNED NOT NULL COMMENT '评论者（FK→TB-01.id）',
  `content`     VARCHAR(500)    NOT NULL COMMENT '评论内容',
  `parent_id`   BIGINT UNSIGNED DEFAULT NULL COMMENT '父评论，支持二级回复（FK→TB-12.id）',
  `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  KEY `idx_comment_target` (`target_type`, `target_id`),
  KEY `idx_comment_author` (`author_id`),
  KEY `idx_comment_parent` (`parent_id`),
  CONSTRAINT `fk_comment_author` FOREIGN KEY (`author_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_comment_parent` FOREIGN KEY (`parent_id`) REFERENCES `comment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论表（TB-12）';

-- 表 4-13 投票表（TB-13）
-- TB-13
CREATE TABLE IF NOT EXISTS `vote` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '投票主键',
  `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '投票人（FK→TB-01.id）',
  `target_type` VARCHAR(16)     NOT NULL COMMENT '目标类型：QUESTION、ANSWER、COMMENT',
  `target_id`   BIGINT UNSIGNED NOT NULL COMMENT '目标主键（多态关联）',
  `value`       TINYINT         NOT NULL COMMENT '1 赞同、-1 反对',
  `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '投票时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vote_user_target` (`user_id`, `target_type`, `target_id`),
  KEY `idx_vote_target` (`target_type`, `target_id`),
  CONSTRAINT `fk_vote_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投票表（TB-13）';

-- 表 4-14 收藏表（TB-14）
-- TB-14
CREATE TABLE IF NOT EXISTS `favorite` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '收藏主键',
  `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '收藏人（FK→TB-01.id）',
  `question_id` BIGINT UNSIGNED NOT NULL COMMENT '被收藏的提问（FK→TB-09.id）',
  `folder`      VARCHAR(32)     DEFAULT NULL COMMENT '收藏夹名称',
  `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_user_question` (`user_id`, `question_id`),
  KEY `idx_favorite_question` (`question_id`),
  CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_favorite_question` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏表（TB-14）';
