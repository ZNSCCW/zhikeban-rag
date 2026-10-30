package com.zhikeban.bishe.vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 向量存储**路由**（`ENG-012` 任务②；详设 §4.3.9 的 {@code VectorStoreRouter}）。
 *
 * <p>★ 职责：**按 `Profile` / `VECTOR_STORE` 裁决激活哪一个实现**，并对外**只暴露一个 `VectorStore`**
 * ⇒ 业务代码零改动即可在 Oracle 主实现与 MySQL 兜底实现之间切换（`NFR-REL-08`）。
 *
 * <p>★ 运行时降级：{@link #fallbackToMysql(String)} 把激活实现**当场切到 MySQL 兜底**并告警
 * —— 用于"Oracle 实例不可用 / `HNSW` 索引未建成"的场景；接口与业务代码**零改动**。
 *
 * <p>★ 本类**不缓存数据、不算相似度**：它只做委派，度量口径由被委派的实现负责。
 *
 * <p>★ 标 {@link Primary}：容器里同时存在 `mysqlVectorStore`（或 `oracleVectorStore`）与本路由
 * ⇒ 业务侧按类型注入时**必须**拿到路由（否则"双实现 + 一个接缝"会变成"两个候选 Bean"而起不来）。
 */
@Service
@Primary
public class VectorStoreRouter implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreRouter.class);

    /** 两个实现的 Bean 名（与 `@Service("...")` 上的名字一致）。 */
    public static final String ORACLE_BEAN = "oracleVectorStore";
    public static final String MYSQL_BEAN = "mysqlVectorStore";

    private final Environment env;
    private final ApplicationContext applicationContext;

    /** 运行时可切换（`volatile`：`fallbackToMysql` 可能被其它线程调用）。 */
    private volatile VectorStore active;

    public VectorStoreRouter(Environment env, ApplicationContext applicationContext) {
        this.env = env;
        this.applicationContext = applicationContext;
        String mode = env.getProperty("VECTOR_STORE", "mysql");
        this.active = lookup("oracle".equalsIgnoreCase(mode) ? ORACLE_BEAN : MYSQL_BEAN, mode);
        log.info("VectorStoreRouter 激活实现 = {}（VECTOR_STORE={}）", active.provider(), mode);
    }

    @Override
    public int upsert(List<VectorRecord> records) {
        return active.upsert(records);
    }

    @Override
    public int delete(Collection<Long> chunkIds) {
        return active.delete(chunkIds);
    }

    @Override
    public List<SearchHit> search(SearchQuery query) {
        return active.search(query);
    }

    @Override
    public RebuildReport rebuild(Long courseId) {
        return active.rebuild(courseId);
    }

    @Override
    public String provider() {
        return active.provider();
    }

    /**
     * Oracle 不可用时的**运行时降级**：切到 MySQL 兜底并打点告警（详设 §7.3）。
     *
     * @param reason 降级原因（进日志，便于定位）
     */
    public void fallbackToMysql(String reason) {
        log.warn("VectorStoreRouter 运行时降级到 MySQL 兜底：{}", reason);
        this.active = lookup(MYSQL_BEAN, "mysql");
    }

    /**
     * 按 Bean 名解析实现；该 Profile 未激活（Bean 不存在）时**退到另一个**；
     * 两个都不存在才拒绝启动（宁可起不来，也不静默跑一个"假实现"）。
     */
    private VectorStore lookup(String beanName, String mode) {
        try {
            return applicationContext.getBean(beanName, VectorStore.class);
        } catch (RuntimeException e) {
            String other = ORACLE_BEAN.equals(beanName) ? MYSQL_BEAN : ORACLE_BEAN;
            log.warn("VECTOR_STORE={} 对应的 Bean[{}] 不在容器里（{}），尝试 [{}]", mode, beanName, e.getMessage(), other);
            try {
                return applicationContext.getBean(other, VectorStore.class);
            } catch (RuntimeException e2) {
                throw new IllegalStateException("VectorStore 双实现都不在容器里：请激活 Profile"
                        + " vector-oracle 或 vector-mysql（当前 VECTOR_STORE=" + mode + "）", e2);
            }
        }
    }
}
