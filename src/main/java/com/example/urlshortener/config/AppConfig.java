package com.example.urlshortener.config;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

/**
 * Конфигурация сервиса. Время жизни ссылки задаётся системой
 * (параметр link.ttl.minutes в config.properties, по умолчанию — сутки).
 */
public class AppConfig {

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final String DEFAULT_BASE_URL = "clck.ru";
    private static final int DEFAULT_MAX_CLICKS = 100;

    private final Duration linkTtl;
    private final String baseUrl;
    private final int defaultMaxClicks;

    public AppConfig(Duration linkTtl, String baseUrl, int defaultMaxClicks) {
        this.linkTtl = linkTtl;
        this.baseUrl = baseUrl;
        this.defaultMaxClicks = defaultMaxClicks;
    }

    public static AppConfig load() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream("/config.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
            // остаются значения по умолчанию
        }
        Duration ttl = props.getProperty("link.ttl.minutes") != null
                ? Duration.ofMinutes(Long.parseLong(props.getProperty("link.ttl.minutes").trim()))
                : DEFAULT_TTL;
        String baseUrl = props.getProperty("base.url", DEFAULT_BASE_URL).trim();
        int maxClicks = Integer.parseInt(
                props.getProperty("default.max.clicks", String.valueOf(DEFAULT_MAX_CLICKS)).trim());
        return new AppConfig(ttl, baseUrl, maxClicks);
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
