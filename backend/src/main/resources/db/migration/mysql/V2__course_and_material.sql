-- ============================================================================
-- V2__course_and_material.sql —— Flyway 迁移脚本（**由 schema.sql 派生**的装载脚本，非事实源）
-- ----------------------------------------------------------------------------
-- ★★ 由 schema.sql 派生（★ schema.sql = 唯一 DDL 事实源）。
--   本文件的建表语句**逐字**取自根 schema.sql 的同名表；本文件**不新增、不改写、不删减**任何 DDL。
--   ⇒ 改表**先改 schema.sql**，再**同批**用生成器同步本文件（`work/ENG/probe-eng009/gen-migrations.mjs`）；
--     **不得**反向只改本文件，也**不得**新开第二套建表脚本 / Flyway 之外的迁移目录。
-- ★ 本脚本覆盖（课程与资料域）：TB-04 course / TB-05 course_member / TB-06 material / TB-07 material_chunk / TB-08 knowledge_point
-- ★ 建表语句数与表号由生成器对账：schema.sql 的 28 张表在本目录 7 份脚本里**不重不漏**。
-- ★ 本脚本**只建结构**：不含资料切片写入、不含任何向量列或向量索引（`ENG-009` 边界 (a)）。
-- ============================================================================

-- 表 4-4 课程表（TB-04）
-- TB-04
CREATE TABLE IF NOT EXISTS `course` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '课程主键',
  `name`             VARCHAR(64)     NOT NULL COMMENT '课程名称',
  `teacher_id`       BIGINT UNSIGNED NOT NULL COMMENT '授课教师（FK→TB-01.id）',
  `semester`         VARCHAR(16)     NOT NULL COMMENT '学期，如 2026-2027-1',
  `credit`           DECIMAL(4,1)    DEFAULT NULL COMMENT '学分',
  `cover`            VARCHAR(255)    DEFAULT NULL COMMENT '封面图地址',
  `invite_code`      CHAR(8)         NOT NULL COMMENT '加课邀请码，可重置',
  `invite_expire_at` DATETIME(3)     DEFAULT NULL COMMENT '邀请码失效时间',
  `status`           TINYINT         NOT NULL DEFAULT 1 COMMENT '课程状态：1 进行中、0 已结课',
  `created_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`          TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_invite_code` (`invite_code`),
  KEY `idx_course_teacher` (`teacher_id`),
  CONSTRAINT `fk_course_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程表（TB-04）';

-- 表 4-5 课程成员表（TB-05）
-- TB-05
CREATE TABLE IF NOT EXISTS `course_member` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '成员关系主键',
  `course_id`      BIGINT UNSIGNED NOT NULL COMMENT '所属课程（FK→TB-04.id）',
  `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '成员用户（FK→TB-01.id）',
  `role_in_course` VARCHAR(16)     NOT NULL DEFAULT 'STUDENT' COMMENT '课程内角色：STUDENT、ASSISTANT',
  `joined_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入时间',
  `created_at`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`        TINYINT         NOT NULL DEFAULT 0 COMMENT '退课采用逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_member` (`course_id`, `user_id`),
  KEY `idx_course_member_user` (`user_id`),
  CONSTRAINT `fk_course_member_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
  CONSTRAINT `fk_course_member_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程成员表（TB-05）';

-- 表 4-6 课程资料表（TB-06）
-- TB-06
CREATE TABLE IF NOT EXISTS `material` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '资料主键',
  `course_id`      BIGINT UNSIGNED NOT NULL COMMENT '所属课程（FK→TB-04.id）',
  `name`           VARCHAR(255)    NOT NULL COMMENT '资料名称（含扩展名）',
  `type`           VARCHAR(16)     NOT NULL COMMENT '资料类型：PDF、PPT、DOC、VIDEO',
  `size`           BIGINT UNSIGNED NOT NULL COMMENT '字节数',
  `storage_path`   VARCHAR(512)    NOT NULL COMMENT '存储键（MinIO 对象名或本地相对路径），不对外暴露',
  `chapter`        VARCHAR(64)     DEFAULT NULL COMMENT '所属章节',
  `tags`           JSON            DEFAULT NULL COMMENT '标签数组',
  `uploader_id`    BIGINT UNSIGNED NOT NULL COMMENT '上传者（FK→TB-01.id）',
  `parse_status`   TINYINT         NOT NULL DEFAULT 0 COMMENT '解析状态：0 待解析、1 成功、2 失败',
  `download_count` INT             NOT NULL DEFAULT 0 COMMENT '下载次数',
  `created_at`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`        TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  KEY `idx_material_course_type` (`course_id`, `type`),
  KEY `idx_material_uploader` (`uploader_id`),
  CONSTRAINT `fk_material_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
  CONSTRAINT `fk_material_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程资料表（TB-06）';

-- 表 4-7 资料切片表（TB-07）
-- TB-07
CREATE TABLE IF NOT EXISTS `material_chunk` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '切片主键，向量表的 chunk_id 指向本列',
  `material_id` BIGINT UNSIGNED NOT NULL COMMENT '所属资料（FK→TB-06.id）',
  `chunk_index` INT             NOT NULL COMMENT '资料内切片序号，从 0 开始',
  `content`     TEXT            NOT NULL COMMENT '切片正文，全文索引列（BM25 一路）',
  `page_no`     INT             DEFAULT NULL COMMENT '起始页码',
  `chapter`     VARCHAR(64)     DEFAULT NULL COMMENT '所属章节',
  `vector_id`   VARCHAR(64)     DEFAULT NULL COMMENT '向量存储侧标识（Oracle 行标识或 MySQL 主键）',
  `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  KEY `idx_material_chunk_material` (`material_id`, `chunk_index`),
  FULLTEXT KEY `ft_material_chunk_content` (`content`) WITH PARSER ngram,
  CONSTRAINT `fk_material_chunk_material` FOREIGN KEY (`material_id`) REFERENCES `material` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资料切片表（TB-07）';

-- 表 4-8 知识点表（TB-08）
-- TB-08
CREATE TABLE IF NOT EXISTS `knowledge_point` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '知识点主键',
  `course_id`   BIGINT UNSIGNED NOT NULL COMMENT '所属课程（FK→TB-04.id）',
  `parent_id`   BIGINT UNSIGNED DEFAULT NULL COMMENT '父知识点，支持树形结构（FK→TB-08.id）',
  `name`        VARCHAR(64)     NOT NULL COMMENT '知识点名称',
  `description` VARCHAR(255)    DEFAULT NULL COMMENT '知识点说明',
  `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_point_course` (`course_id`),
  KEY `idx_knowledge_point_parent` (`parent_id`),
  CONSTRAINT `fk_knowledge_point_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
  CONSTRAINT `fk_knowledge_point_parent` FOREIGN KEY (`parent_id`) REFERENCES `knowledge_point` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识点表（TB-08）';
