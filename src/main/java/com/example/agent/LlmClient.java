package com.example.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class LlmClient {

    private static final String URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL = "anthropic/claude-sonnet-4";
    private static final int MAX_TOOL_ROUNDS = 5;
    private static final int MAX_HISTORY = 10; // последние N реплик (память диалога)

    private final String apiKey;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    // Память диалога: пары {role, content} — только реплики пользователя и финальные ответы
    private final List<String[]> history = new ArrayList<>();

    public LlmClient(String apiKey) {
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String ask(String userMessage) throws Exception {
        ArrayNode messages = mapper.createArrayNode();

        // Подкладываем память диалога
        for (String[] h : history) {
            ObjectNode m = messages.addObject();
            m.put("role", h[0]);
            m.put("content", h[1]);
        }
        // Текущее сообщение пользователя
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);

        String answer = runToolLoop(messages);

        // Сохраняем в память и подрезаем до последних N
        history.add(new String[]{"user", userMessage});
        history.add(new String[]{"assistant", answer});
        while (history.size() > MAX_HISTORY) history.remove(0);

        return answer;
    }

    private String runToolLoop(ArrayNode messages) throws Exception {
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            JsonNode message = callApi(messages);

            JsonNode toolCalls = message.get("tool_calls");
            boolean hasTools = toolCalls != null && toolCalls.isArray() && toolCalls.size() > 0;

            if (!hasTools) {
                return message.path("content").asText();
            }

            ObjectNode assistantMsg = mapper.createObjectNode();
            assistantMsg.put("role", "assistant");
            if (message.hasNonNull("content")) {
                assistantMsg.put("content", message.get("content").asText());
            }
            assistantMsg.set("tool_calls", toolCalls);
            messages.add(assistantMsg);

            for (JsonNode call : toolCalls) {
                String id = call.path("id").asText();
                String name = call.path("function").path("name").asText();
                String argsJson = call.path("function").path("arguments").asText();

                String result = dispatchTool(name, argsJson);

                ObjectNode toolMsg = messages.addObject();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", id);
                toolMsg.put("content", result);
            }
        }
        return "Превышено число обращений к инструментам.";
    }

    /** Вызывает инструмент по имени, замеряет время и логирует. */
    private String dispatchTool(String name, String argsJson) {
        long start = System.currentTimeMillis();
        boolean ok = true;
        String result;
        try {
            switch (name) {
                case "calculator": {
                    JsonNode args = mapper.readTree(argsJson);
                    result = Calculator.evaluate(args.path("expression").asText());
                    break;
                }
                case "sql_query": {
                    JsonNode args = mapper.readTree(argsJson);
                    result = SqlTool.query(args.path("query").asText());
                    break;
                }
                case "current_datetime": {
                    result = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    break;
                }
                default:
                    result = "Неизвестный инструмент: " + name;
            }
            if (result != null && result.startsWith("Ошибка")) ok = false;
        } catch (Exception e) {
            ok = false;
            result = "Ошибка инструмента " + name + ": " + e.getMessage();
        }

        long dur = System.currentTimeMillis() - start;
        Stats.countTool(name);
        if (!ok) Stats.countError();
        Logger.logTool(name, argsJson, dur, ok);
        return result;
    }

    private JsonNode callApi(ArrayNode messages) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", MODEL);
        root.set("messages", messages);
        root.set("tools", buildTools());

        String body = mapper.writeValueAsString(root);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .timeout(Duration.ofSeconds(60))
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

    private ArrayNode buildTools() {
        ArrayNode tools = mapper.createArrayNode();

        {
            ObjectNode fn = addFunction(tools, "calculator",
                    "Вычисляет арифметическое выражение: + - * / и скобки. Для любых числовых расчётов.");
            ObjectNode params = fn.putObject("parameters");
            params.put("type", "object");
            params.putObject("properties").putObject("expression").put("type", "string")
                    .put("description", "Математическое выражение, например (2+3)*4");
            params.putArray("required").add("expression");
        }

        {
            ObjectNode fn = addFunction(tools, "sql_query",
                    "Выполняет ОДИН read-only SELECT к локальной SQLite-базе. " +
                    "Таблицы: products(id, name, category, price), customers(id, name, city). " +
                    "Только SELECT, без изменения данных.");
            ObjectNode params = fn.putObject("parameters");
            params.put("type", "object");
            params.putObject("properties").putObject("query").put("type", "string")
                    .put("description", "SQL SELECT-запрос, например: SELECT name, price FROM products WHERE category='Мебель'");
            params.putArray("required").add("query");
        }

        {
            ObjectNode fn = addFunction(tools, "current_datetime",
                    "Возвращает текущую дату и время сервера.");
            ObjectNode params = fn.putObject("parameters");
            params.put("type", "object");
            params.putObject("properties");
        }

        return tools;
    }

    private ObjectNode addFunction(ArrayNode tools, String name, String description) {
        ObjectNode tool = tools.addObject();
        tool.put("type", "function");
        ObjectNode fn = tool.putObject("function");
        fn.put("name", name);
        fn.put("description", description);
        return fn;
    }
}
