package com.example.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class LlmClient {

    private static final String URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL = "anthropic/claude-sonnet-4";
    private static final int MAX_TOOL_ROUNDS = 5; // защита от бесконечного цикла

    private final String apiKey;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public LlmClient(String apiKey) {
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)) // таймаут — защита от зависаний
                .build();
    }

    public String ask(String userMessage) throws Exception {
        ArrayNode messages = mapper.createArrayNode();
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            JsonNode message = callApi(messages);

            JsonNode toolCalls = message.get("tool_calls");
            boolean hasTools = toolCalls != null && toolCalls.isArray() && toolCalls.size() > 0;

            if (!hasTools) {
                return message.path("content").asText(); // финальный ответ
            }

            // Возвращаем в историю сообщение ассистента с его tool_calls
            ObjectNode assistantMsg = mapper.createObjectNode();
            assistantMsg.put("role", "assistant");
            if (message.hasNonNull("content")) {
                assistantMsg.put("content", message.get("content").asText());
            }
            assistantMsg.set("tool_calls", toolCalls);
            messages.add(assistantMsg);

            // Выполняем каждый вызов инструмента
            for (JsonNode call : toolCalls) {
                String id = call.path("id").asText();
                String name = call.path("function").path("name").asText();
                String argsJson = call.path("function").path("arguments").asText();

                String result;
                if ("calculator".equals(name)) {
                    JsonNode args = mapper.readTree(argsJson);
                    String expr = args.path("expression").asText();
                    result = Calculator.evaluate(expr);
                } else {
                    result = "Неизвестный инструмент: " + name;
                }

                ObjectNode toolMsg = messages.addObject();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", id);
                toolMsg.put("content", result);
            }
        }
        return "Превышено число обращений к инструментам.";
    }

    private JsonNode callApi(ArrayNode messages) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", MODEL);
        root.set("messages", messages);
        root.set("tools", buildTools());

        String body = mapper.writeValueAsString(root);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .timeout(Duration.ofSeconds(60)) // таймаут на весь запрос
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Ошибка LLM " + response.statusCode() + ": " + response.body());
        }
        JsonNode json = mapper.readTree(response.body());
        return json.at("/choices/0/message");
    }

    /** Описание инструмента "calculator" для модели. */
    private ArrayNode buildTools() {
        ArrayNode tools = mapper.createArrayNode();
        ObjectNode tool = tools.addObject();
        tool.put("type", "function");
        ObjectNode fn = tool.putObject("function");
        fn.put("name", "calculator");
        fn.put("description", "Вычисляет арифметическое выражение: сложение, вычитание, умножение, деление и скобки. Используй для любых числовых расчётов.");
        ObjectNode params = fn.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");
        ObjectNode expr = props.putObject("expression");
        expr.put("type", "string");
        expr.put("description", "Математическое выражение, например: (2+3)*4");
        ArrayNode required = params.putArray("required");
        required.add("expression");
        return tools;
    }
}
