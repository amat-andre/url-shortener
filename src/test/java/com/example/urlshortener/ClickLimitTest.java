package com.example.urlshortener;

import com.example.urlshortener.config.AppConfig;
import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.NotificationService;
import com.example.urlshortener.service.ShortCodeGenerator;
import com.example.urlshortener.service.UrlShortenerService;
import com.example.urlshortener.service.UrlShortenerService.ClickLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Лимит переходов: учёт кликов, блокировка и уведомления владельца.
 */
class ClickLimitTest {

    private UrlShortenerService service;
    private List<String> notifications;

    @BeforeEach
    void setUp() {
        notifications = new ArrayList<>();
        NotificationService notifier = (userId, message) -> notifications.add(message);
        AppConfig config = new AppConfig(Duration.ofHours(24), "clck.ru", 100);
        service = new UrlShortenerService(new LinkRepository(), new ShortCodeGenerator(),
                notifier, config, Clock.systemUTC());
    }

    private String codeOf(String shortUrl) {
        return service.extractCode(shortUrl);
    }

    @Test
    void clicksAreCounted() {
        UUID user = UUID.randomUUID();
        String shortUrl = service.shorten(user, "https://example.com/a", 5);
        String code = codeOf(shortUrl);

        service.resolve(code);
        service.resolve(code);

        assertEquals(2, service.findByCode(code).orElseThrow().getClickCount());
    }

    @Test
    void linkIsBlockedWhenLimitExhausted() {
        UUID user = UUID.randomUUID();
        String shortUrl = service.shorten(user, "https://example.com/b", 3);
        String code = codeOf(shortUrl);

        // три разрешённых перехода
        assertEquals("https://example.com/b", service.resolve(code));
        service.resolve(code);
        service.resolve(code);

        // четвёртый — заблокирован
        assertThrows(ClickLimitException.class, () -> service.resolve(code));
    }

    @Test
    void ownerIsNotifiedWhenLimitRunsOut() {
        UUID user = UUID.randomUUID();
        String shortUrl = service.shorten(user, "https://example.com/c", 2);
        String code = codeOf(shortUrl);

        service.resolve(code);
        service.resolve(code); // здесь лимит исчерпывается

        assertTrue(notifications.stream().anyMatch(m -> m.contains("лимит")),
                "владелец должен получить уведомление об исчерпании лимита");

        // и при последующих попытках перехода — тоже
        notifications.clear();
        assertThrows(ClickLimitException.class, () -> service.resolve(code));
        assertFalse(notifications.isEmpty(), "уведомление при попытке перехода по исчерпанной ссылке");
    }

    @Test
    void defaultLimitIsUsedWhenNotSpecified() {
        UUID user = UUID.randomUUID();
        String shortUrl = service.shorten(user, "https://example.com/d", 0);
        String code = codeOf(shortUrl);

        assertEquals(100, service.findByCode(code).orElseThrow().getMaxClicks());
    }

    @Test
    void ownerCanRaiseLimitAndLinkWorksAgain() {
        UUID user = UUID.randomUUID();
        String shortUrl = service.shorten(user, "https://example.com/e", 1);
        String code = codeOf(shortUrl);

        service.resolve(code);
        assertThrows(ClickLimitException.class, () -> service.resolve(code));

        service.updateMaxClicks(user, code, 3);
        assertEquals("https://example.com/e", service.resolve(code));
    }

    @Test
    void ownerIsWarnedWhenOneClickRemains() {
        UUID user = UUID.randomUUID();
        String shortUrl = service.shorten(user, "https://example.com/warn", 3);
        String code = codeOf(shortUrl);

        service.resolve(code); // осталось 2 — предупреждения нет
        assertTrue(notifications.stream().noneMatch(m -> m.contains("последний переход")),
                "при двух оставшихся переходах предупреждения быть не должно");

        service.resolve(code); // остался 1 — предупреждение
        assertTrue(notifications.stream().anyMatch(m -> m.contains("последний переход")),
                "когда остаётся один переход, владелец должен получить предупреждение");

        service.resolve(code); // лимит исчерпан — финальное уведомление
        assertTrue(notifications.stream().anyMatch(m -> m.contains("закончились")));
    }
}
