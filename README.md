# 智课伴 · 校园课程问答与错题管理平台

> 本科毕业设计 · 基于 RAG 与大模型的检索增强实现
>
> Zhikeban: A Campus Course Q&A and Wrong-Question Management Platform Based on RAG and LLM

把课程资料变成可检索的知识库：学生提问时，先从老师上传的资料里检索相关片段，再交给大模型生成答案，**答案带引用出处**；再配合错题本与掌握度复习，形成学习闭环。

---

## 为什么做这个

- 通用大模型不知道你这门课讲了什么，还会一本正经地编；
- 课程资料散落在 PDF / PPT / Word 里，翻起来很慢；
- 把整本资料直接塞进提示词，又贵又容易超出上下文。

所以走 RAG：**知识放在外部索引里，资料更新只重建索引，不重训模型。**

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 21 · Spring Boot 3.5.4 · MyBatis-Plus 3.5.7 · Spring Security |
| 业务库 | MySQL 8.4（26 张业务表，Flyway 管理迁移） |
| 向量存储 | Oracle 23ai `VECTOR`（可选）· MySQL 兜底（默认）· `VectorStore` 策略 + Profile 切换 |
| 嵌入 | bge-m3（1024 维），Ollama 本地优先，可切 OpenAI |
| 大模型 | `LlmClient` 可切换 + 三级降级链 |
| 前端 | Vue 3.5 · Vite 6 · Pinia · Element Plus · ECharts |
| 部署 | Docker Compose 一键起全栈 |

## RAG 主链路

```
课程资料 → 解析(抽取/清洗) → 语义切片(512 token, 重叠 64)
        → 向量化(bge-m3, 1024d) → 写入 VectorStore

提问 → 向量检索 Top8 ┐
     → BM25 检索 Top8 ┘ → 去重(chunkId) → RRF 融合 → Top5
     → 权限过滤(按选课关系，宁空不漏)
     → 提示词组装(长度预算 + 片段编号)
     → LLM 生成(主模型 → 备选 → 仅返回检索片段)
```

几个刻意的取舍：

- **混合检索而不是纯向量**：课程资料里专有名词、公式编号、代码符号很多，BM25 对精确匹配更稳。
- **RRF 而不是分数加权**：向量相似度和 BM25 分数不同量纲，直接加权要么归一化、要么拍权重，都不可解释；RRF 只用排名。
- **权限过滤放在提示词之前**：越权片段一律丢弃，过滤异常时也是全部丢弃——漏出去是数据事故，答不出来只是体验问题。
- **切片与向量都可重建**：切片以 `(material_id, chunk_index)` 为自然键，向量以 `chunk_id` 为唯一键，重跑解析是覆盖而不是追加。

## 工程要点

| 点 | 做法 |
|---|---|
| 幂等重建 | 自然键去重；向量写失败不回滚切片，靠 `rebuild` + 巡检补偿 |
| 双存储 | `VectorStore` 接口 + 两个实现，Profile 切换，检索口径一致 |
| 降级 | `FallbackLlmChain`：主模型 → 备选模型 → 仅返回检索片段并标记 |
| 掌握度 | 按题目难度与耗时折算观测，带时间衰减，反推复习优先级 |

## 快速开始

前置：Docker（含 Compose）。

```bash
# 1. 准备环境变量（数据库密码等，均有默认值，生产请务必修改）
cp .env.example .env

# 2. 起全栈（MySQL + Redis + 后端 + 前端）
docker compose up -d --build

# 3. 健康检查
curl -fsS http://localhost:8080/api/health

# 4. 关闭
docker compose down          # 加 -v 连数据卷一起删
```

默认路径不依赖 Oracle；需要验证 Oracle 向量检索时再叠加：

```bash
docker compose --profile oracle up -d
```

> 前端与端口以 `docker-compose.yml` 为准。

## 目录结构

```
backend/                 Spring Boot 后端
  src/main/java/com/zhikeban/bishe/
    rag/                 HybridRetriever、RagOrchestrator、PromptAssembler
    llm/                 LlmClient、Ollama/OpenAI 实现、FallbackLlmChain
    vector/              VectorStore 双实现与路由
    security/            权限过滤
    service/             业务服务（含掌握度）
    domain/ controller/ mapper/ config/ web/
  src/main/resources/
    db/migration/{vendor}/  Flyway 迁移（按 vendor 选路）
frontend/                Vue 3 前端
docker-compose.yml       一键启动编排
schema.sql               人读权威 DDL（与迁移脚本表名集合一致）
```

## 已验证 / 未验证

**已实测（单机 Windows + Docker Desktop，Ollama 本机推理）**

端到端冒烟跑通了完整链路：真 HTTP 健康探活 → 真写库 → 真嵌入（bge-m3，1024 维）→ 真写向量 → 混合检索与 RRF → 权限过滤 → 提示词组装 → 真生成（qwen2.5:7b）→ 引用回填 → 越权负例。

单次问答各阶段耗时：

| 阶段 | 耗时 |
|---|---|
| 混合检索（向量 + BM25 → RRF） | 228 ms |
| 权限过滤 | 7 ms |
| 提示词组装 | 7 ms |
| 大模型生成（qwen2.5:7b，本机） | 5397 ms |
| 引用回填 | 1 ms |
| **端到端** | **约 5.6 s** |

结论很直白：**瓶颈在生成，不在检索**——检索 228 ms 只占端到端的 4%，7B 模型本机推理占了 96%。想提升体验，该做的是流式输出、模型量化或换更小的模型，而不是继续优化检索。

**未验证（不在文档里编数字）**

- **RAG 效果未做对照实验**：没有基线组与评分口径，因此不提供检索命中率、答案准确率。链路跑通 ≠ 效果好。
- **压测未执行**：问答链路没有 HTTP 入口（由内部编排器调用），无法直接压；且当前语料只有冒烟用的 3 条片段，不具备压测意义。
- **Oracle 路径为可选增强**：默认路径只在 MySQL 兜底实现上验证。

设计说明书、测试报告等文档属于学校交付物，不在本仓库内。

## 常见问题

**本机 3306 被占用（已装本地 MySQL）**：在 `.env` 里把 `MYSQL_PORT` 改成别的端口（如 `13306`），后端走容器内网连接，不受影响。

**容器里连不到本机 Ollama**：Ollama 默认只监听 `127.0.0.1`，容器需要通过 `host.docker.internal` 访问，启动后端时加 `OLLAMA_BASE_URL=http://host.docker.internal:11434`。

## License

MIT
