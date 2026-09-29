package com.example.agent;

import java.io.IOException;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        EnvLoader env = new EnvLoader(".env");
        String apiKey = env.get("OPENROUTER_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("Ошибка: не найден OPENROUTER_API_KEY. Проверь файл .env в корне проекта.");
            return;
        }

        SqlTool.init();

        // HTTP-эндпоинт (если задан AGENT_API_KEY) — запускается в фоне
        String agentKey = env.get("AGENT_API_KEY");
        if (agentKey != null && !agentKey.isBlank()) {
            try {
                new ApiServer(new LlmClient(apiKey), agentKey).start(8080);
            } catch (IOException e) {
                System.out.println("Не удалось запустить HTTP-эндпоинт: " + e.getMessage());
            }
        } else {
            System.out.println("AGENT_API_KEY не задан — HTTP-эндпоинт выключен (работает только CLI).");
        }

        // CLI
        LlmClient llm = new LlmClient(apiKey);
        System.out.println("=== Secure AI Agent ===");
        System.out.println("Инструменты: калькулятор, запрос к базе (products, customers), дата/время.");
        System.out.println("Агент помнит контекст в рамках сессии. Введите запрос (или 'exit' — выход):");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) break;
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("exit")) break;
            if (input.isEmpty()) continue;

            Logger.logTurn("user", input);
            try {
                String answer = llm.ask(input);
                Logger.logTurn("agent", answer);
                System.out.println(answer);
            } catch (Exception e) {
                Stats.countError();
                Logger.logTurn("error", e.getMessage());
                System.out.println("Ошибка при обращении к LLM: " + e.getMessage());
            }
        }

        System.out.println("Пока!");
        System.exit(0); // остановить фоновый HTTP-сервер
    }
}
