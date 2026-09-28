package com.example.agent;

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

    public static void log(String role, String text) {
        try {
            Files.createDirectories(LOG_DIR);
            String line = "[" + LocalDateTime.now().format(TS) + "] " + role + ": " + oneLine(text)
                    + System.lineSeparator();
            Files.writeString(LOG_FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("(не удалось записать лог: " + e.getMessage() + ")");
        }
    }

    // Складываем в одну строку, чтобы каждая запись занимала одну строку лога
    private static String oneLine(String s) {
        if (s == null) return "";
        return s.replace("\r", " ").replace("\n", " ");
    }
}
