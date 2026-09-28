package com.example.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;


public class EnvLoader {

    private final Map<String, String> values = new HashMap<>();

    public EnvLoader(String path) {
        try {
            for (String line : Files.readAllLines(Path.of(path))) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                values.put(key, val);
            }
        } catch (IOException e) {
            // .env может отсутствовать — не страшно, ниже возьмём из System.getenv
        }
    }

    public String get(String key) {
        String v = values.get(key);
        if (v == null || v.isEmpty()) {
            v = System.getenv(key); // запасной вариант — системная переменная
        }
        return v;
    }
}
