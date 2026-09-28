package com.example.agent;

import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        // Ключ берём из .env, а не из кода
        EnvLoader env = new EnvLoader(".env");
        String apiKey = env.get("OPENROUTER_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("Ошибка: не найден OPENROUTER_API_KEY. Проверь файл .env в корне проекта.");
            return;
        }

        LlmClient llm = new LlmClient(apiKey);

        System.out.println("=== Secure AI Agent ===");
        System.out.println("Введите запрос (или 'exit' — выход):");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) break;
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("exit")) break;
            if (input.isEmpty()) continue;

            Logger.log("USER", input); // логируем запрос (без секретов)

            try {
                String answer = llm.ask(input);
                Logger.log("AGENT", answer); // логируем ответ
                System.out.println(answer);
            } catch (Exception e) {
                Logger.log("ERROR", e.getMessage());
                System.out.println("Ошибка при обращении к LLM: " + e.getMessage());
            }
        }

        System.out.println("Пока!");
    }
}
