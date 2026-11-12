package com.example.urlshortener;

import com.example.urlshortener.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Конфигурация из .env-файла: чтение параметров, значения по умолчанию,
 * устойчивость к отсутствующему/битому файлу.
 */
class AppConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void parametersAreReadFromEnvFile() throws Exception {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, """
                # комментарий
                LINK_TTL_MINUTES=60
                BASE_URL=short.io
                DEFAULT_MAX_CLICKS=7
                """);

        AppConfig config = AppConfig.load(env);

        assertEquals(Duration.ofMinutes(60), config.getLinkTtl());
        assertEquals("short.io", config.getBaseUrl());
        assertEquals(7, config.getDefaultMaxClicks());
    }

    @Test
    void missingEnvFileFallsBackToDefaults() {
        AppConfig config = AppConfig.load(tempDir.resolve("no-such-file"));

        assertEquals(Duration.ofHours(24), config.getLinkTtl());
        assertEquals("clck.ru", config.getBaseUrl());
        assertEquals(100, config.getDefaultMaxClicks());
    }

    @Test
    void partialEnvFileUsesDefaultsForMissingKeys() throws Exception {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, "LINK_TTL_MINUTES=10\n");

        AppConfig config = AppConfig.load(env);

        assertEquals(Duration.ofMinutes(10), config.getLinkTtl());
        assertEquals("clck.ru", config.getBaseUrl());
        assertEquals(100, config.getDefaultMaxClicks());
    }

    @Test
    void commentsBlankLinesAndGarbageAreIgnored() throws Exception {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, """
                # это комментарий

                BASE_URL = my.link
                строка без равно
                =пустой-ключ
                """);

        AppConfig config = AppConfig.load(env);

        assertEquals("my.link", config.getBaseUrl());
    }

    @Test
    void environmentVariableOverridesEnvFile() throws Exception {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, "BASE_URL=from-file\n");
        // переменная окружения, выставленная до запуска JVM, имеет приоритет
        String fromEnv = System.getenv("BASE_URL");
        AppConfig config = AppConfig.load(env);
        assertEquals(fromEnv != null ? fromEnv : "from-file", config.getBaseUrl());
    }
}
