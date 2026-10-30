package com.zhikeban.bishe.vector;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `OllamaEmbeddingClient` 单测（`ENG-013`）—— 真调 `/api/embeddings`（本测试用 JDK 自带
 * `HttpServer` 起一个**本地桩**，不打外网）；Ollama 不可用时**不抛异常**，退化为
 * **确定性**的 1024 维本地兜底嵌入（`provider()` 变成 `ollama-degraded`，如实暴露）。
 */
class OllamaEmbeddingClientTest {

    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(s -> s.stop(0));
        servers.clear();
    }

    private String startStub(int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/embeddings", exchange -> respond(exchange, status, body));
        server.start();
        servers.add(server);
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        exchange.close();
    }

    /** 起一个桩再关掉，得到一个"一定连不上"的端口。 */
    private String startThenStopStub() throws IOException {
        String base = startStub(200, "{\"embedding\":[0.1]}");
        servers.forEach(s -> s.stop(0));
        servers.clear();
        return base;
    }

    @Test
    void parsesEmbeddingFromOllamaAndReportsProviderOllama() throws Exception {
        String base = startStub(200, "{\"embedding\":[0.1,0.2,0.3]}");
        OllamaEmbeddingClient client = new OllamaEmbeddingClient(base, "bge-m3", 3000);

        float[] v = client.embed("余弦相似度");

        assertThat(v).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(client.dim()).isEqualTo(1024);
        assertThat(client.provider()).isEqualTo("ollama");
        assertThat(client.lastFallbackReason()).isNull();
    }

    @Test
    void non200ResponseFallsBackToDeterministicLocalEmbedding() throws Exception {
        String base = startStub(500, "{\"error\":\"model not found\"}");
        OllamaEmbeddingClient client = new OllamaEmbeddingClient(base, "bge-m3", 3000);

        float[] first = client.embed("同一段文本");
        float[] second = client.embed("同一段文本");

        assertThat(first).hasSize(1024);
        assertThat(first).containsExactly(second);              // 确定性：同文本同向量
        assertThat(client.provider()).isEqualTo("ollama-degraded");
        assertThat(client.lastFallbackReason()).contains("IllegalStateException");
        double norm = 0.0d;
        for (float x : first) {
            norm += (double) x * x;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0d, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void unreachableEndpointAlsoFallsBackInsteadOfThrowing() throws Exception {
        String base = startThenStopStub();
        OllamaEmbeddingClient client = new OllamaEmbeddingClient(base, "bge-m3", 2000);

        float[] v = client.embed("离线段落");

        assertThat(v).hasSize(1024);
        assertThat(client.lastFallbackReason()).isNotBlank();
    }

    @Test
    void responseWithoutEmbeddingArrayIsTreatedAsFailure() throws Exception {
        String base = startStub(200, "{\"error\":\"bad\"}");
        OllamaEmbeddingClient client = new OllamaEmbeddingClient(base, "bge-m3", 3000);

        client.embed("x");

        assertThat(client.lastFallbackReason()).contains("embedding");
    }

    @Test
    void fallbackEmbeddingIsStableDistinctAndZeroForBlankInput() {
        assertThat(OllamaEmbeddingClient.fallbackEmbed(null)).containsOnly(0.0f);
        assertThat(OllamaEmbeddingClient.fallbackEmbed("")).containsOnly(0.0f);

        float[] a = OllamaEmbeddingClient.fallbackEmbed("余弦相似度");
        float[] b = OllamaEmbeddingClient.fallbackEmbed("余弦相似度");
        float[] c = OllamaEmbeddingClient.fallbackEmbed("完全不同的另一段文字");

        assertThat(a).containsExactly(b);
        assertThat(a).isNotEqualTo(c);
    }
}
