-- ============================================================================
-- V7__vector_tables.sql —— Flyway 迁移脚本（**由 schema.sql 派生**的装载脚本，非事实源）
-- ----------------------------------------------------------------------------
-- ★★ 由 schema.sql 派生（★ schema.sql = 唯一 DDL 事实源；`ENG-010` 后按 vendor 拆分落盘）。
--   本文件的建表语句**逐字**取自根 schema.sql 的同名表；本文件**不新增、不改写、不删减**任何 DDL。
--   ⇒ 改表**先改 schema.sql**，再**同批**用生成器同步本文件（`work/ENG/probe-eng009/gen-migrations.mjs`）；
--     **不得**反向只改本文件，也**不得**新开第二套建表脚本 / Flyway 之外的迁移目录。
-- ★ 本脚本覆盖（向量表域 · MySQL 侧）：TB-28 chunk_embedding（默认路径的兜底向量表）
-- ★ 建表语句数与表号由生成器对账：schema.sql 的 28 张表在本目录 7 份脚本里**不重不漏**。
-- ★★ **只建表结构，不建索引、不写向量 SQL、不做检索**（`ENG-009` 边界 (a)）。
--   ★ TB-27 用 Oracle 语法（VECTOR / CLOB / NUMBER / SYSTIMESTAMP），**MySQL 不能执行该段**；
--     在 MySQL 上只用 TB-28（默认路径），TB-27 由 `--profile oracle` 的 Oracle 实例执行。
--   ★ 向量索引（HNSW / COSINE）属 RAG 链路，**本单不落**（`R5`：无向量相似度/距离 SQL）。
-- ============================================================================

--
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
