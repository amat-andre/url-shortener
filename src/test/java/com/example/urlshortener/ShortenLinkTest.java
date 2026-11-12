package com.example.urlshortener;

import com.example.urlshortener.config.AppConfig;
import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.NotificationService;
import com.example.urlshortener.service.ShortCodeGenerator;
import com.example.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Этап 2.1: сокращение ссылок и уникальность для разных пользователей.
 */
class ShortenLinkTest {

    private UrlShortenerService service;
    private List<String> notifications;

    @BeforeEach
    void setUp() {
        notifications = new ArrayList<>();
        NotificationService notifier = (userId, message) -> notifications.add(userId + ": " + message);
        AppConfig config = new AppConfig(Duration.ofHours(24), "clck.ru", 100);
        service = new UrlShortenerService(new LinkRepository(), new ShortCodeGenerator(),
                notifier, config, Clock.systemUTC());
    }

    @Test
    void longUrlIsConvertedToShortLink() {
        UUID user = UUID.randomUUID();
        String shortUrl = service.shorten(user, "https://www.baeldung.com/java-9-http-client", 10);

        assertTrue(shortUrl.startsWith("clck.ru/"), "формат короткой ссылки");
        String code = shortUrl.substring("clck.ru/".length());
        assertEquals(7, code.length(), "длина кода");
        assertTrue(code.matches("[0-9a-zA-Z]+"), "код в base62");
    }

    @Test
    void sameUrlShortenedByDifferentUsersProducesUniqueLinks() {
        String url = "https://www.baeldung.com/java-9-http-client";
        String first = service.shorten(UUID.randomUUID(), url, 10);
        String second = service.shorten(UUID.randomUUID(), url, 10);

        assertNotEquals(first, second,
                "разные пользователи должны получать разные короткие ссылки на один URL");
    }

    @Test
    void sameUserShorteningSameUrlGetsSameLinkBack() {
        UUID user = UUID.randomUUID();
        String url = "https://example.com/page";
        String first = service.shorten(user, url, 10);
        String second = service.shorten(user, url, 10);

        assertEquals(first, second);
    }

    @Test
    void generatedCodesDoNotCollideAcrossManyUrls() {
        UUID user = UUID.randomUUID();
        var codes = new java.util.HashSet<String>();
        for (int i = 0; i < 500; i++) {
            codes.add(service.shorten(user, "https://example.com/page/" + i, 10));
        }
        assertEquals(500, codes.size(), "все сгенерированные ссылки уникальны");
    }

    @Test
    void invalidUrlIsRejected() {
        UUID user = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> service.shorten(user, "not-a-url", 10));
        assertThrows(IllegalArgumentException.class,
                () -> service.shorten(user, "", 10));
    }
}
