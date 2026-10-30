package com.zhikeban.bishe.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 大模型调用 · **API 备选实现**（`ENG-012` 任务③；详设 §4.3.2 ⑤ 的"API 可选"分支）。
 *
 * <p>★ 契约：**OpenAI 兼容**的 {@code /v1/chat/completions}（`Authorization: Bearer <key>`），
 * 流式走 SSE（每行 {@code data: {...}}）。
 *
 * <p>★ **未配置即不可用**：{@code zhikeban.llm.openai.api-key} 为空时 {@link #healthy()} 直接
 * 返回假 —— 不假装可用、也不报错；降级链随后走第三级（全文检索兜底）。
 * 这样"本地优先 + API 备选 + 全文兜底"整条链在**没有 API key** 的机器上也能如实跑完。
 */
@Service
public class OpenAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int timeoutMs;

    public OpenAiLlmClient(
            @Value("${zhikeban.llm.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${zhikeban.llm.openai.api-key:}") String apiKey,
            @Value("${zhikeban.llm.openai.model:gpt-4o-mini}") String model,
            @Value("${zhikeban.llm.openai.timeout-ms:60000}") int timeoutMs) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public Flux<String> stream(PromptPayload payload) {
        if (!healthy()) {
            throw new DegradeException("GENERATE", "API 备选不可用（未配置 api-key 或端点探活失败）");
        }
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "stream", true,
                    "messages", List.of(
                            Map.of("role", "system", "content", payload == null || payload.systemPrompt() == null
                                    ? "" : payload.systemPrompt()),
                            Map.of("role", "user", "content", OllamaLlmClient.render(payload)))));
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                throw new DegradeException("GENERATE", "API 备选返回 " + resp.statusCode());
            }
            return Flux.fromStream(() -> sse(resp.body()).stream()).filter(s -> !s.isEmpty());
        } catch (DegradeException e) {
            throw e;
        } catch (Exception e) {
            throw new DegradeException("GENERATE", "调用 API 备选失败：" + e.getMessage(), e);
        }
    }

    @Override
    public String complete(PromptPayload payload) {
        StringBuilder sb = new StringBuilder();
        for (String piece : stream(payload).toIterable()) {
            sb.append(piece);
        }
        return sb.toString();
    }

    @Override
    public String provider() {
        return "openai";
    }

    @Override
    public boolean healthy() {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/models"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.statusCode() == 200;
        } catch (Exception e) {
            log.debug("API 备选探活失败：{}", e.getMessage());
            return false;
        }
    }

    /** 解析 OpenAI 兼容的 SSE：`data: {...}` 行取 `choices[0].delta.content`。 */
    private List<String> sse(InputStream in) {
        List<String> out = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String json = line.substring("data:".length()).trim();
                if (json.isEmpty() || "[DONE]".equals(json)) {
                    continue;
                }
                JsonNode delta = mapper.readTree(json).path("choices").path(0).path("delta").path("content");
                if (delta.isTextual() && !delta.asText().isEmpty()) {
                    out.add(delta.asText());
                }
            }
        } catch (Exception e) {
            throw new DegradeException("GENERATE", "解析 API 备选流失败：" + e.getMessage(), e);
        }
        return out;
    }
}
