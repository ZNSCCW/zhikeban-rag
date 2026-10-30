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
 * `OpenAiLlmClient` 单测（`ENG-013`）—— API 备选分支：
 * **未配置 api-key 即不可用**（不假装可用、也不报错）、OpenAI 兼容 SSE 解析、
 * 端点非 200 / 连不上都转成 `DegradeException`（交给第三级全文兜底）。
 */
class OpenAiLlmClientTest {

    private static final String SSE = "data: {\"choices\":[{\"delta\":{\"content\":\"甲\"}}]}\n"
            + "\n"
            + "data: {\"choices\":[{\"delta\":{\"content\":\"\"}}]}\n"
            + "data: {\"choices\":[{\"delta\":{\"content\":\"乙\"}}]}\n"
            + "data: [DONE]\n";

    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(s -> s.stop(0));
        servers.clear();
    }

    private HttpServer start(Function<HttpExchange, String[]> handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String[] plan = handler.apply(exchange);
            respond(exchange, Integer.parseInt(plan[0]), plan[1]);
        });
        server.start();
        servers.add(server);
        return server;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        exchange.close();
    }

    private static String base(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static PromptPayload payload() {
        return new PromptPayload("系统提示", "问题？", List.of("证据一"), List.of(), 0.2d, 512);
    }

    @Test
    void withoutApiKeyTheBackupIsSimplyUnavailable() {
        OpenAiLlmClient client = new OpenAiLlmClient("https://api.openai.com", "", "gpt-4o-mini", 3000);

        assertThat(client.healthy()).isFalse();
        assertThat(client.provider()).isEqualTo("openai");
        assertThatThrownBy(() -> client.stream(payload()))
                .isInstanceOf(DegradeException.class)
                .hasMessageContaining("API 备选不可用");
    }

    @Test
    void healthyRequiresHttp200FromModelsEndpoint() throws Exception {
        HttpServer ok = start(ex -> new String[]{"200", "{\"data\":[]}"});
        assertThat(new OpenAiLlmClient(base(ok), "k", "gpt-4o-mini", 3000).healthy()).isTrue();

        HttpServer down = start(ex -> new String[]{"500", "{}"});
        assertThat(new OpenAiLlmClient(base(down), "k", "gpt-4o-mini", 3000).healthy()).isFalse();
    }

    @Test
    void parsesOpenAiCompatibleSse() throws Exception {
        HttpServer server = start(ex -> new String[]{"200", SSE});
        OpenAiLlmClient client = new OpenAiLlmClient(base(server), "k", "gpt-4o-mini", 3000);

        assertThat(client.stream(payload()).collectList().block()).containsExactly("甲", "乙");
    }

    @Test
    void completeConcatenatesTheSseStream() throws Exception {
        HttpServer server = start(ex -> new String[]{"200", SSE});
        OpenAiLlmClient client = new OpenAiLlmClient(base(server), "k", "gpt-4o-mini", 3000);

        assertThat(client.complete(payload())).isEqualTo("甲乙");
    }

    @Test
    void non200BecomesDegradeException() throws Exception {
        // /v1/models 探活成功（200），但 /v1/chat/completions 返回 429 ⇒ 错误码原样透出
        HttpServer server = start(ex -> ex.getRequestURI().getPath().endsWith("/v1/models")
                ? new String[]{"200", "{\"data\":[]}"}
                : new String[]{"429", "{\"error\":\"rate limited\"}"});
        OpenAiLlmClient client = new OpenAiLlmClient(base(server), "k", "gpt-4o-mini", 3000);

        assertThat(client.healthy()).isTrue();
        assertThatThrownBy(() -> client.stream(payload()))
                .isInstanceOf(DegradeException.class)
                .hasMessageContaining("API 备选返回 429");
    }

    @Test
    void unreachableEndpointBecomesDegradeException() throws Exception {
        HttpServer server = start(ex -> new String[]{"200", SSE});
        String deadBase = base(server);
        servers.forEach(s -> s.stop(0));
        servers.clear();

        OpenAiLlmClient client = new OpenAiLlmClient(deadBase, "k", "gpt-4o-mini", 1500);
        // 端点连不上 ⇒ healthy() 先为假 ⇒ 直接判"备选不可用"（不假装可用）
        assertThat(client.healthy()).isFalse();
        assertThatThrownBy(() -> client.stream(payload()))
                .isInstanceOf(DegradeException.class)
                .hasMessageContaining("API 备选不可用");
    }

    @Test
    void malformedSseSurfacesAsDegradeExceptionOnSubscription() throws Exception {
        HttpServer server = start(ex -> new String[]{"200", "data: not-json\n"});
        OpenAiLlmClient client = new OpenAiLlmClient(base(server), "k", "gpt-4o-mini", 3000);

        assertThatThrownBy(() -> client.stream(payload()).collectList().block())
                .isInstanceOf(DegradeException.class)
                .hasMessageContaining("解析 API 备选流失败");
    }
}
