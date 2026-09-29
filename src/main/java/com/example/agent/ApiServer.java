package com.example.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class ApiServer {

    private final LlmClient llm;
    private final String agentKey;
    private final RateLimiter limiter = new RateLimiter(20, 60); // 20 запросов / минуту
    private final ObjectMapper mapper = new ObjectMapper();

    public ApiServer(LlmClient llm, String agentKey) {
        this.llm = llm;
        this.agentKey = agentKey;
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/chat", this::handleChat);
        server.createContext("/stats", this::handleStats);
        server.setExecutor(null);
        server.start();
        System.out.println("HTTP-эндпоинт запущен: POST http://localhost:" + port
                + "/chat  (нужен заголовок X-API-Key)");
    }

    private void handleChat(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        // 1) Аутентификация ДО вызова LLM
        if (!authorized(ex)) {
            send(ex, 401, "{\"error\":\"unauthorized\"}");
            return;
        }
        // 2) Rate limiting
        String client = ex.getRemoteAddress().getAddress().getHostAddress();
        if (!limiter.allow(client)) {
            send(ex, 429, "{\"error\":\"too many requests\"}");
            return;
        }
        // 3) Обработка
        try {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode json = mapper.readTree(body);
            String message = json.path("message").asText("");
            if (message.isBlank()) {
                send(ex, 400, "{\"error\":\"empty message\"}");
                return;
            }

            String answer;
            synchronized (llm) {
                answer = llm.ask(message);
            }

            ObjectNode resp = mapper.createObjectNode();
            resp.put("answer", answer);
            send(ex, 200, mapper.writeValueAsString(resp));
        } catch (Exception e) {
            Stats.countError();
            send(ex, 500, "{\"error\":\"internal error\"}");
        }
    }

    private void handleStats(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            send(ex, 401, "{\"error\":\"unauthorized\"}");
            return;
        }
        send(ex, 200, Stats.snapshotJson());
    }

    private boolean authorized(HttpExchange ex) {
        String key = ex.getRequestHeaders().getFirst("X-API-Key");
        return key != null && key.equals(agentKey);
    }

    private void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
