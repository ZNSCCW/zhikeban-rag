package com.zhikeban.bishe.llm;

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
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * `OllamaLlmClient` 单测（`ENG-013`）—— 本地优先分支：
 * **真探活**（`/api/tags` 只有 HTTP 200 才算健康）、NDJSON **真流式**解析、
 * 主模型拉不动**退到小模型**（`qwen2.5:7b` → `qwen2.5:3b`）、全失败才抛 `DegradeException`。
 * ★ 用 JDK 自带 `HttpServer` 起本地桩，不打外网、不需要本机 Ollama。
 */
class OllamaLlmClientTest {

    private static final String NDJSON = "{\"response\":\"第一段\"}\n"
            + "{\"response\":\"\"}\n"
            + "\n"
            + "{\"response\":\"第二段\"}\n"
            + "{\"done\":true}\n";

    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(s -> s.stop(0));
        servers.clear();
    }

    private HttpServer start(Function<HttpExchange, int[]> handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int[] plan = handler.apply(exchange);
            respond(exchange, plan[0], plan[1]);
        });
        server.start();
        servers.add(server);
        return server;
    }

    private static void respond(HttpExchange exchange, int status, int kind) throws IOException {
        String body = kind == 0 ? NDJSON : (kind == 1 ? "{\"models\":[]}" : "{\"error\":\"boom\"}");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (status == 200 && kind == 0) {
            exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        exchange.close();
    }

    private static String base(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String body(HttpExchange exchange) {
        try {
            return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static PromptPayload payload() {
        return new PromptPayload("系统提示", "余弦相似度？", List.of("证据一"), List.of("上轮"), 0.2d, 512);
    }

    @Test
    void healthyRequiresHttp200FromApiTags() throws Exception {
        HttpServer ok = start(ex -> new int[]{200, 1});
        assertThat(new OllamaLlmClient(base(ok), "qwen2.5:7b,qwen2.5:3b", 3000).healthy()).isTrue();

        HttpServer down = start(ex -> new int[]{500, 2});
        assertThat(new OllamaLlmClient(base(down), "qwen2.5:7b", 3000).healthy()).isFalse();

        String deadBase = base(ok);
        servers.forEach(s -> s.stop(0));
        servers.clear();
        assertThat(new OllamaLlmClient(deadBase, "qwen2.5:7b", 1500).healthy()).isFalse();
    }

    @Test
    void streamsNdjsonPiecesAndSkipsEmptyOnes() throws Exception {
        HttpServer server = start(ex -> new int[]{200, 0});
        OllamaLlmClient client = new OllamaLlmClient(base(server), "qwen2.5:7b,qwen2.5:3b", 5000);

        assertThat(client.stream(payload()).collectList().block()).containsExactly("第一段", "第二段");
        assertThat(client.provider()).isEqualTo("ollama");
    }

    @Test
    void completeConcatenatesTheWholeStream() throws Exception {
        HttpServer server = start(ex -> new int[]{200, 0});
        OllamaLlmClient client = new OllamaLlmClient(base(server), "qwen2.5:7b", 5000);

        assertThat(client.complete(payload())).isEqualTo("第一段第二段");
    }

    @Test
    void failsOverToTheSmallerModelWhenThePrimaryReturnsAnError() throws Exception {
        HttpServer server = start(ex -> body(ex).contains("qwen2.5:7b") ? new int[]{500, 2} : new int[]{200, 0});
        OllamaLlmClient client = new OllamaLlmClient(base(server), "qwen2.5:7b,qwen2.5:3b", 5000);

        assertThat(client.stream(payload()).collectList().block()).containsExactly("第一段", "第二段");
    }

    @Test
    void allModelsFailingBecomesDegradeException() throws Exception {
        HttpServer server = start(ex -> new int[]{500, 2});
        OllamaLlmClient client = new OllamaLlmClient(base(server), "qwen2.5:7b,qwen2.5:3b", 5000);

        assertThatThrownBy(() -> client.stream(payload()))
                .isInstanceOf(DegradeException.class)
                .hasMessageContaining("本地模型全部失败");
        assertThat(((DegradeException) org.assertj.core.api.Assertions
                .catchThrowable(() -> client.stream(payload()))).getStage()).isEqualTo("GENERATE");
    }

    @Test
    void unreachableOllamaBecomesDegradeException() throws Exception {
        HttpServer server = start(ex -> new int[]{200, 0});
        String deadBase = base(server);
        servers.forEach(s -> s.stop(0));
        servers.clear();

        OllamaLlmClient client = new OllamaLlmClient(deadBase, "qwen2.5:7b", 1500);
        assertThatThrownBy(() -> client.stream(payload()))
                .isInstanceOf(DegradeException.class)
                .hasMessageContaining("调用本地模型失败");
    }

    @Test
    void blankModelListFallsBackToThePrimaryModelName() throws Exception {
        HttpServer server = start(ex -> body(ex).contains("qwen2.5:7b") ? new int[]{200, 0} : new int[]{500, 2});
        OllamaLlmClient client = new OllamaLlmClient(base(server), "", 5000);

        assertThat(client.stream(payload()).collectList().block()).containsExactly("第一段", "第二段");
    }

    @Test
    void malformedNdjsonSurfacesAsDegradeExceptionOnSubscription() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", exchange -> {
            byte[] bytes = "not-json\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
            exchange.close();
        });
        server.start();
        servers.add(server);

        OllamaLlmClient client = new OllamaLlmClient(base(server), "qwen2.5:7b", 5000);
        assertThatThrownBy(() -> client.stream(payload()).collectList().block())
                .isInstanceOf(DegradeException.class)
                .hasMessageContaining("解析本地模型流失败");
    }

    @Test
    void renderBuildsSystemEvidenceHistoryAndQuestionInOrder() {
        String text = OllamaLlmClient.render(payload());

        assertThat(text).startsWith("系统提示\n\n");
        assertThat(text).contains("[1] 证据一\n");
        assertThat(text).contains("上轮\n");
        assertThat(text).endsWith("问题：余弦相似度？");
        assertThat(OllamaLlmClient.render(null)).isEmpty();
    }

    @Test
    void renderToleratesPayloadWithoutSystemPromptEvidenceOrHistory() {
        String text = OllamaLlmClient.render(new PromptPayload(null, null, null, null, 0.2d, 512));

        assertThat(text).isEqualTo("问题：");
    }
}
