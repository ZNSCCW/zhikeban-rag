-- ============================================================================
-- V7__vector_tables.sql —— Flyway 迁移脚本（**由 schema.sql 派生**的装载脚本，非事实源）
-- ----------------------------------------------------------------------------
-- ★★ 由 schema.sql 派生（★ schema.sql = 唯一 DDL 事实源；`ENG-010` 后按 vendor 拆分落盘）。
-- ★ 本脚本覆盖（向量表域 · **Oracle 侧**）：TB-27 material_chunk_vector（Oracle 23ai，可选增强）。
-- ★★ 本段用 Oracle 语法（VECTOR(1024) / CLOB / NUMBER / SYSTIMESTAMP）**MySQL 不能执行** ⇒
--    它单独放在 db/migration/oracle/ 下；MySQL 走 db/migration/mysql/（只含 TB-28 chunk_embedding）。
--   `{vendor}` 占位按实际库型选路（见 backend/src/main/resources/application.yml 的 spring.flyway.locations）。
-- ★ 本脚本只做表结构：不建向量索引（HNSW/COSINE 属 RAG 链路，`ENG-009` 边界 (a)）、不写向量 SQL。
-- ============================================================================

-- TB-27
-- ★ 幂等：与 mysql/ 那 27 张同口径用 `IF NOT EXISTS`（否则实例上已手工建过该表时迁移会报错）
CREATE TABLE IF NOT EXISTS material_chunk_vector (
  chunk_id   NUMBER         NOT NULL,
  course_id  NUMBER         NOT NULL,
  embedding  VECTOR(1024)   NOT NULL,
  metadata   CLOB,
  created_at TIMESTAMP(6)   DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_material_chunk_vector PRIMARY KEY (chunk_id)
);
