# 后端骨架（`backend/`）—— Spring Boot 3.5.x

> 本文件属 `ENG-007` 授权面（`backend/**`，见 `§ENG-007` 的 `G1`：项目说明**落在源码树内**，不新开根文件）。
> **可空实现**：本单只要求"能编译、能起、目录结构清晰"，**不写业务功能**。

## 一句话说清怎么起

```bash
docker compose up -d --build backend      # 在仓库根执行；或 `docker compose up -d` 起默认全栈
```

起来后探活：`curl -fsS http://localhost:8080/api/health`

## 技术栈（依据 `shared/FACTS.md` §七）

| 项 | 取值 | 落点 |
| --- | --- | --- |
| JDK | **21**（LTS） | `pom.xml` 的 `<java.version>21</java.version>` |
| Spring Boot | **3.5.x** | `pom.xml` 的 `spring-boot-starter-parent` 版本 |
| Spring Security | — | `src/main/java/com/zhikeban/bishe/config/SecurityConfig.java`（**真实引用**，非只在 `pom` 挂依赖） |
| MyBatis-Plus | 3.5.x | `BisheApplication` 的 `@MapperScan` ＋ `domain/entity/User.java` 的 `@TableName`／`@TableId`／`@TableLogic` ＋ `mapper/UserMapper` 的 `BaseMapper` |
| MySQL | **8.4** | `src/main/resources/application.yml` 的数据源（默认指向 compose 默认栈内的 `mysql` 服务） |

## 目录结构

```
backend/
├── pom.xml
├── README.md                                   ← 本文件
└── src/main/
    ├── java/com/zhikeban/bishe/
    │   ├── BisheApplication.java               ← 启动类（@MapperScan）
    │   ├── config/SecurityConfig.java          ← Spring Security 骨架链
    │   ├── controller/HealthController.java    ← 探活端点（冒烟 curl 的落点）
    │   ├── domain/entity/User.java             ← TB-01 用户实体骨架
    │   └── mapper/UserMapper.java              ← MyBatis-Plus BaseMapper
    └── resources/application.yml
```

## 本地构建（可选，需联网拉依赖）

```bash
cd backend && mvn -DskipTests package
# 本机实测（2026-09-25）：mvn -DskipTests compile → 退 0
```

> ★ `§ENG-007` 的 `G4`：本单"能编译／能起"的**机器判据落在 `C` 组**
> （`backend` 服务能被 `docker compose up` 起来 ⇒ 编译通过），
> **不另加**依赖联网的本地 `mvn package` 断言。

## 边界（不在本单范围内）

- **不写业务功能**：RBAC／JWT 过滤链、`AuthService`、`RagService`、`VectorStore` 实现等都**不属本单**。
- **不建数据库表**：28 张表见仓库根 `schema.sql`（由 `docker-compose.yml` 的 `mysql` 服务初始化）。
- **逐列一致性属另一张单**（`§ENG-007` 的 `P2`）。
