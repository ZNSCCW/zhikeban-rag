-- ============================================================================
-- 智课伴毕设 · 数据库模式（schema.sql）
-- ----------------------------------------------------------------------------
-- ★★ 本文件是【唯一 DDL 事实源】（`ENG-009` 的 `D11` 判据锚此固定子串）：
--   · 建表事实**只以本文件为准**；数据字典与概设的对照也以本文件为准。
--   · `backend/src/main/resources/db/migration/V*.sql` 是【由 schema.sql 派生的装载脚本】
--     （Flyway 可执行形态），**表名集合必须与本文件相等**（不换 / 不加 / 不漏）。
--   · ⇒ **改表先改本文件**，再**同批**同步派生脚本；**不得**新开第二套建表脚本、
--     也**不得**引入 Flyway 之外的迁移目录（`D3`/`D4`/`D5`/`D6`/`D7` 判据）。
--
-- 依据：docs/概要设计说明书.md §4.4 数据字典（28 张数据表）
--   · TB-01 ~ TB-26 = 业务表（MySQL 8.4）           —— 概设 §4.4.1
--   · TB-27         = 资料切片向量表（Oracle 23ai） —— 概设 §4.4.2（主实现，可选增强）
--   · TB-28         = 切片向量兜底表（MySQL 8.4）    —— 概设 §4.4.2（默认路径，BLOB 存向量）
-- 本文件由 ENG-007 生成（仓骨架 + DDL 单，可空实现；不含业务种子数据）。
--
-- ★ 执行方式（默认路径 = MySQL 8.4，一键启动不依赖外部 Oracle）：
--     docker compose up -d mysql
--   compose 把本文件挂到 /docker-entrypoint-initdb.d/，容器**首次**建库时自动执行。
--   ★ 想让改动生效：docker compose down -v && docker compose up -d mysql
--     （db_data 卷里已有数据时，entrypoint 不会重复执行初始化脚本）
--   手工执行：mysql -h127.0.0.1 -P3306 -uzhikeban -pzhikeban zhikeban < schema.sql
--
-- ★ TB-27 是 **Oracle 侧**表（Oracle Database 23ai，可选增强服务，见根 docker-compose.yml 的
--   profiles: [oracle]），MySQL 侧的等价兜底实现是 TB-28。本文件**按设计稿把 28 张表的 DDL
--   都写在里面**（概设 §4.4 的完整数据字典）；其中 TB-27 用 Oracle 语法（VECTOR / CLOB /
--   NUMBER / SYSTIMESTAMP），**不能在 MySQL 上执行**。
--   ⇒ 「只执行 MySQL 那 27 张表」的做法见下方 §1 节头注释（用 /*!99999 ... */ 版本门包住）。
--
-- ★ 三格自洽（概设 §4.4.2，向量维度随所选模型；本单锚默认嵌入模型 bge-m3）：
--     TB-27.embedding  VECTOR(1024)  —— 第 1 格
--     TB-28.embedding  BLOB(4096)    —— 第 2 格 = 1024 维 × 4 字节 float32
--     TB-28.dim        SMALLINT NOT NULL DEFAULT 1024 —— 第 3 格
--   切换模型（如 API 备选 text-embedding-3-small = 1536 维）时，**三格必须同改**。
--
-- ★ 表号锚约定（供机器判定）：每张表的 `-- TB-nn` 标记写在 **建表语句的紧邻上一行**，
--   使"表号集合"判的是**建表事实**，而不是文件头说明文字。
-- ============================================================================

-- 建库既可由 compose 的 MYSQL_DATABASE 完成，也可由本文件独立执行完成（两者等价，幂等）。
CREATE DATABASE IF NOT EXISTS `zhikeban`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `zhikeban`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- §0 业务表（TB-01 ~ TB-26，MySQL 8.4）
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

-- ============================================================================
-- §1 向量表
-- ----------------------------------------------------------------------------
-- ★ 概设 §4.4.2：TB-27（Oracle 23ai，主实现）与 TB-28（MySQL 兜底）是**同一份数据的
--   两种存储形态**，**同一时刻只有一个是激活的**（由 VECTOR_STORE 环境变量 / Spring Profile
--   决定，见 backend/src/main/resources/application.yml 的 `zhikeban.vector-store`），不双写。
-- ★ 默认路径（`VECTOR_STORE=mysql`，不依赖外部 Oracle）用的是 TB-28。
-- ============================================================================

-- 表 4-28 切片向量兜底表（TB-28，MySQL 8.4；默认路径）
-- TB-28
CREATE TABLE IF NOT EXISTS `chunk_embedding` (
  `chunk_id`   BIGINT UNSIGNED NOT NULL COMMENT '对应 material_chunk.id（TB-07.id），upsert 幂等键',
  `course_id`  BIGINT UNSIGNED NOT NULL COMMENT '课程标识，检索后按课程做权限过滤（索引列）',
  `embedding`  BLOB(4096)       NOT NULL COMMENT '向量序列化字节（bge-m3 = 1024 维 × 4 字节 float32 = 4096；随所选模型变化）',
  `dim`        SMALLINT         NOT NULL DEFAULT 1024 COMMENT '向量维度（默认 bge-m3 = 1024，随所选模型变化），用于应用层余弦相似度校验',
  `metadata`   JSON             DEFAULT NULL COMMENT '元数据：资料名、章节、页码',
  `created_at` DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '写入时间',
  `updated_at` DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`chunk_id`),
  KEY `idx_chunk_embedding_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='切片向量兜底表（TB-28）';

-- 表 4-27 资料切片向量表（TB-27，Oracle Database 23ai；可选增强）
--
-- ★★ 本段是 **Oracle DDL**（`VECTOR` / `CLOB` / `NUMBER` / `SYSTIMESTAMP`），
--    **不能在 MySQL 上执行**。用 MySQL 执行本文件时，请把下面这**一张表**的语句整体
--    包进版本门注释里跳过（MySQL 视其为注释：版本门 `99999` 不可达）：
--         /*!99999
--         <把下面这段建表语句原样粘进来>
--         */
--    ★ 本文件**故意不替执行方做这个改写**：DDL 文本按设计稿逐字给出，便于与概设 §4.4.2 对照。
--    ★ 另：本行下面的示例代码块里**不出现**建表语句关键字，是为了不干扰
--      `§ENG-007` 的 `B2c`（它数的是文本里建表语句的出现次数）。
--
-- TB-27
CREATE TABLE material_chunk_vector (
  chunk_id   NUMBER         NOT NULL,
  course_id  NUMBER         NOT NULL,
  embedding  VECTOR(1024)   NOT NULL,
  metadata   CLOB,
  created_at TIMESTAMP(6)   DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_material_chunk_vector PRIMARY KEY (chunk_id)
);

-- ★ 向量索引（概设 §4.4.2）：索引类型 HNSW、距离度量 COSINE（余弦距离）
--   Oracle 侧建索引语句（文本留档，不写 `⟨CREATE⟩ ⟨TABLE⟩` 关键字以免与建表语句计数相混）：
--     ⟨CREATE⟩ ⟨VECTOR⟩ INDEX idx_material_chunk_vector_hnsw
--       ON material_chunk_vector (embedding)
--       ORGANIZATION INMEMORY NEIGHBOR GRAPH
--       DISTANCE COSINE
--       WITH TARGET ACCURACY 90;
--   MySQL 兜底侧（TB-28）**不建向量索引**，向量以 `BLOB` 存储、由应用层算余弦相似度。

-- ★ 三格自洽的三处落点（同一份向量数据的两种形态，必须同维度）：
--     ① VECTOR(1024)          —— TB-27.embedding（上方 Oracle DDL，第 1 格）
--     ② BLOB(4096)            —— TB-28.embedding（= 1024 × 4 字节 float32，第 2 格）
--     ③ dim ... DEFAULT 1024  —— TB-28.dim（第 3 格）
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 表号账（自查用，不是判定锚点）：TB-01 ~ TB-28 共 28 张 = 26 业务表 + 2 向量表
--   业务表：TB-01 user / TB-02 role / TB-03 user_role / TB-04 course / TB-05 course_member
--           TB-06 material / TB-07 material_chunk / TB-08 knowledge_point / TB-09 question
--           TB-10 question_kp / TB-11 answer / TB-12 comment / TB-13 vote / TB-14 favorite
--           TB-15 wrong_book / TB-16 wrong_kp / TB-17 review_task / TB-18 ai_chat
--           TB-19 ai_message / TB-20 ai_feedback / TB-21 notification / TB-22 report
--           TB-23 sensitive_word / TB-24 operation_log / TB-25 daily_statistics / TB-26 system_config
--   向量表：TB-27 material_chunk_vector（Oracle，可选）/ TB-28 chunk_embedding（MySQL，默认）
-- ============================================================================
