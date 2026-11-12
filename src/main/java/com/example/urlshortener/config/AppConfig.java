package com.example.urlshortener.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация сервиса. Все ключевые параметры (TTL, лимиты, базовый адрес)
 * читаются из .env-файла; значения можно переопределить переменными окружения.
 * Приоритет: переменная окружения > .env > значение по умолчанию.
 */
public class AppConfig {

    public static final String ENV_TTL_MINUTES = "LINK_TTL_MINUTES";
    public static final String ENV_BASE_URL = "BASE_URL";
    public static final String ENV_DEFAULT_MAX_CLICKS = "DEFAULT_MAX_CLICKS";

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final String DEFAULT_BASE_URL = "clck.ru";
    private static final int DEFAULT_MAX_CLICKS = 100;

    /** Путь к .env по умолчанию — рабочая директория приложения. */
    private static final Path DEFAULT_ENV_FILE = Path.of(".env");

    private final Duration linkTtl;
    private final String baseUrl;
    private final int defaultMaxClicks;

    public AppConfig(Duration linkTtl, String baseUrl, int defaultMaxClicks) {
        this.linkTtl = linkTtl;
        this.baseUrl = baseUrl;
        this.defaultMaxClicks = defaultMaxClicks;
    }

    /** Загружает конфигурацию из .env в рабочей директории. */
    public static AppConfig load() {
        return load(DEFAULT_ENV_FILE);
    }

    /** Загружает конфигурацию из указанного .env-файла. */
    public static AppConfig load(Path envFile) {
        Map<String, String> fileValues = readEnvFile(envFile);

        Duration ttl = Duration.ofMinutes(Long.parseLong(
                resolve(ENV_TTL_MINUTES, fileValues,
                        String.valueOf(DEFAULT_TTL.toMinutes()))));
        String baseUrl = resolve(ENV_BASE_URL, fileValues, DEFAULT_BASE_URL);
        int maxClicks = Integer.parseInt(
                resolve(ENV_DEFAULT_MAX_CLICKS, fileValues, String.valueOf(DEFAULT_MAX_CLICKS)));

        return new AppConfig(ttl, baseUrl, maxClicks);
    }

    /**
     * Приоритет источников: переменная окружения, затем .env-файл,
     * затем значение по умолчанию.
     */
    private static String resolve(String key, Map<String, String> fileValues, String defaultValue) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromFile = fileValues.get(key);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.trim();
        }
        return defaultValue;
    }

    /** Читает .env-файл формата KEY=VALUE (# — комментарий). */
    static Map<String, String> readEnvFile(Path envFile) {
        Map<String, String> values = new HashMap<>();
        if (envFile == null || !Files.isRegularFile(envFile)) {
            return values;
        }
        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                values.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
            }
        } catch (IOException e) {
            System.err.println("Не удалось прочитать " + envFile + ": " + e.getMessage()
                    + " — используются значения по умолчанию");
        }
        return values;
    }

    public Duration getLinkTtl() {
        return linkTtl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public int getDefaultMaxClicks() {
        return defaultMaxClicks;
    }
}
