package com.example.urlshortener;

import com.example.urlshortener.config.AppConfig;
import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.ShortCodeGenerator;
import com.example.urlshortener.service.UrlShortenerService;
import com.example.urlshortener.service.UrlShortenerService.ClickLimitException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Этап 3: комплексный сценарий по чек-листу ТЗ —
 * от создания ссылок двумя пользователями до блокировки,
 * истечения срока жизни и уведомлений.
 */
class IntegrationTest {

    private static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration d) {
            instant = instant.plus(d);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    @Test
    void fullUserJourney() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-12T12:00:00Z"));
        List<String> notifications = new ArrayList<>();
        AppConfig config = new AppConfig(Duration.ofHours(1), "clck.ru", 100);
        UrlShortenerService service = new UrlShortenerService(
                new LinkRepository(), new ShortCodeGenerator(),
                (userId, message) -> notifications.add(userId + " | " + message),
                config, clock);

        // 1. Два пользователя сокращают один и тот же URL — ссылки уникальны
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        String url = "https://www.baeldung.com/java-9-http-client";
        String aliceLink = service.shorten(alice, url, 2);
        String bobLink = service.shorten(bob, url, 5);
        assertNotEquals(aliceLink, bobLink);

        // 2. Переход по полной короткой ссылке (как ввод в консоль) возвращает исходный URL
        assertEquals(url, service.resolve(service.extractCode(aliceLink)));

        // 3. Лимит Алисы (2 перехода) исчерпывается, ссылка блокируется
        service.resolve(service.extractCode(aliceLink));
        assertThrows(ClickLimitException.class,
                () -> service.resolve(service.extractCode(aliceLink)));

        // 4. Ссылка Боба при этом продолжает работать
        assertEquals(url, service.resolve(service.extractCode(bobLink)));

        // 5. Уведомления пришли именно Алисе
        assertTrue(notifications.stream()
                .anyMatch(n -> n.startsWith(alice.toString()) && n.contains("лимит")));
        assertTrue(notifications.stream()
                .noneMatch(n -> n.startsWith(bob.toString())));

        // 6. Истекает TTL (1 час) — обе ссылки удаляются, оба владельца уведомлены
        clock.advance(Duration.ofMinutes(61));
        service.purgeExpired();
        assertTrue(service.listUserLinks(alice).isEmpty());
        assertTrue(service.listUserLinks(bob).isEmpty());
        assertTrue(notifications.stream()
                .anyMatch(n -> n.startsWith(bob.toString()) && n.contains("истёк")));

        // 7. Переход по удалённой ссылке невозможен (она уже удалена из хранилища)
        assertThrows(UrlShortenerService.LinkNotFoundException.class,
                () -> service.resolve(service.extractCode(bobLink)));
    }
}
