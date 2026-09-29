package com.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {

    private static final Path LOG_DIR = Path.of("logs");
    private static final Path LOG_FILE = LOG_DIR.resolve("agent.log");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper mapper = new ObjectMapper();

    /** Лог реплики диалога: type = "user" / "agent" / "error". */
    public static synchronized void logTurn(String type, String text) {
        ObjectNode o = mapper.createObjectNode();
        o.put("ts", now());
        o.put("type", type);
        o.put("text", truncate(text, 500));
        write(o);
    }

    /** Структурированный лог вызова инструмента. */
    public static synchronized void logTool(String tool, String input, long durationMs, boolean ok) {
        ObjectNode o = mapper.createObjectNode();
        o.put("ts", now());
        o.put("type", "tool");
        o.put("tool", tool);
        o.put("input", truncate(input, 200)); // усечённый вход
        o.put("duration_ms", durationMs);
        o.put("status", ok ? "ok" : "error");
        write(o);
    }

    private static void write(ObjectNode o) {
        try {
            Files.createDirectories(LOG_DIR);
            String line = mapper.writeValueAsString(o) + System.lineSeparator();
            Files.writeString(LOG_FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("(не удалось записать лог: " + e.getMessage() + ")");
        }
    }

    private static String now() {
        return LocalDateTime.now().format(TS);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        s = s.replace("\r", " ").replace("\n", " ");
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
