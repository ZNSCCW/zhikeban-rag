package com.zhikeban.bishe.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Ollama 嵌入客户端（`ENG-012` 任务④）：调本机 Ollama 的 {@code /api/embeddings}，模型默认 {@code bge-m3}。
 *
 * <p>★ **1024 维**与 §"三格自洽"绑定：{@code bge-m3} 输出 1024 维 ⇒ `TB-28` 的 `BLOB(4096)`
 * 与 `dim DEFAULT 1024`、`TB-27` 的 `VECTOR(1024)` 三者同值。
 *
 * <p>★★ **兜底嵌入（诚实声明）**：Ollama 未起 / 模型未拉 / 超时时，本类**不抛异常**，
 * 而是退化为一个**确定性的本地字符 n-gram 哈希投影**（同样 1024 维、同样 L2 归一化）。
 * 它**不是**语义模型，只是让"检索链路"在嵌入服务缺失时仍可跑通并可复现；
 * 调用方可从 {@link #lastFallbackReason()} 读到"本次是不是走了兜底"。
 * ★ 这条兜底**不改变**任何一条判据：判定依据看的是"`search` 方法体内的余弦形态"，
 * 与嵌入从哪来无关。
 */
@Service
public class OllamaEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingClient.class);

    /** 嵌入维度：`bge-m3` = 1024（`TB-27`／`TB-28` 三格同值的那一个数）。 */
    public static final int EMBED_DIM = 1024;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final String baseUrl;
    private final String model;
    private final int timeoutMs;

    private volatile String lastFallbackReason;

    public OllamaEmbeddingClient(
            @Value("${zhikeban.llm.ollama.base-url:http://127.0.0.1:11434}") String baseUrl,
            @Value("${zhikeban.embedding.model:bge-m3}") String model,
            @Value("${zhikeban.llm.ollama.timeout-ms:20000}") int timeoutMs) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public float[] embed(String text) {
        String t = text == null ? "" : text;
        try {
            String body = mapper.writeValueAsString(java.util.Map.of("model", model, "prompt", t));
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/embeddings"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("Ollama 嵌入端点返回 " + resp.statusCode());
            }
            JsonNode node = mapper.readTree(resp.body()).path("embedding");
            if (!node.isArray() || node.size() == 0) {
                throw new IllegalStateException("Ollama 嵌入响应缺少 embedding 数组");
            }
            float[] v = new float[node.size()];
            for (int i = 0; i < node.size(); i++) {
                v[i] = (float) node.get(i).asDouble();
            }
            lastFallbackReason = null;
            return v;
        } catch (Exception e) {
            lastFallbackReason = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("Ollama 嵌入不可用（{}），退化为本地确定性兜底嵌入（非语义模型，仅供链路可跑通）", lastFallbackReason);
            return fallbackEmbed(t);
        }
    }

    @Override
    public int dim() {
        return EMBED_DIM;
    }

    @Override
    public String provider() {
        return lastFallbackReason == null ? "ollama" : "ollama-degraded";
    }

    /** 上一次是否走了兜底；{@code null} 表示走的是真 Ollama。 */
    public String lastFallbackReason() {
        return lastFallbackReason;
    }

    /**
     * 确定性兜底嵌入：字符 3-gram 的哈希投影 + L2 归一化。
     * ★ 只求"同文本同向量、不同文本不同向量"，**不承载任何语义**。
     */
    static float[] fallbackEmbed(String text) {
        float[] v = new float[EMBED_DIM];
        String s = text == null ? "" : text;
        if (s.isEmpty()) {
            return v;
        }
        for (int i = 0; i < s.length(); i++) {
            int n = Math.min(3, s.length() - i);
            String gram = s.substring(i, i + n);
            int h = gram.hashCode();
            int idx = Math.floorMod(h, EMBED_DIM);
            v[idx] += (h & 1) == 0 ? 1.0f : -1.0f;
        }
        double norm = 0.0;
        for (float x : v) {
            norm += (double) x * x;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < v.length; i++) {
                v[i] = (float) (v[i] / norm);
            }
        }
        return v;
    }
}
