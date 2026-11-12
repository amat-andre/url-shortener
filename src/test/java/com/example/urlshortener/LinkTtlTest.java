package com.example.urlshortener;

import com.example.urlshortener.config.AppConfig;
import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.NotificationService;
import com.example.urlshortener.service.ShortCodeGenerator;
import com.example.urlshortener.service.UrlShortenerService;
import com.example.urlshortener.service.UrlShortenerService.LinkExpiredException;
import org.junit.jupiter.api.BeforeEach;
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
 * Этап 2.3: время жизни ссылки — TTL задаётся системой,
 * «протухшие» ссылки удаляются, владелец уведомляется.
 */
class LinkTtlTest {

    /** Часы, которые можно «перематывать» вперёд. */
    private static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
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

    private MutableClock clock;
    private LinkRepository repository;
    private UrlShortenerService service;
    private List<String> notifications;

    private UrlShortenerService serviceWithTtl(Duration ttl) {
        AppConfig config = new AppConfig(ttl, "clck.ru", 100);
        return new UrlShortenerService(repository, new ShortCodeGenerator(),
                (userId, message) -> notifications.add(message), config, clock);
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-12T12:00:00Z"));
        repository = new LinkRepository();
        notifications = new ArrayList<>();
    }

    @Test
    void linkLivesWithinTtlAndExpiresAfter() {
        service = serviceWithTtl(Duration.ofHours(1)); // TTL = 1 час
        UUID user = UUID.randomUUID();
        String code = service.extractCode(service.shorten(user, "https://example.com/ttl", 10));

        // в пределах часа ссылка работает
        clock.advance(Duration.ofMinutes(59));
        assertEquals("https://example.com/ttl", service.resolve(code));

        // после истечения TTL — недоступна
        clock.advance(Duration.ofMinutes(2));
        assertThrows(LinkExpiredException.class, () -> service.resolve(code));
    }

    @Test
    void expiredLinkIsDeletedFromRepository() {
        service = serviceWithTtl(Duration.ofHours(24)); // TTL = сутки
        UUID user = UUID.randomUUID();
        String code = service.extractCode(service.shorten(user, "https://example.com/day", 10));

        clock.advance(Duration.ofHours(25));
        assertThrows(LinkExpiredException.class, () -> service.resolve(code));
        assertTrue(service.findByCode(code).isEmpty(), "просроченная ссылка должна удаляться");
    }

    @Test
    void purgeRemovesOnlyExpiredLinks() {
        service = serviceWithTtl(Duration.ofHours(1));
        UUID user = UUID.randomUUID();
        String oldCode = service.extractCode(service.shorten(user, "https://example.com/old", 10));

        clock.advance(Duration.ofHours(2));
        String freshCode = service.extractCode(service.shorten(user, "https://example.com/fresh", 10));

        service.purgeExpired();

        assertTrue(service.findByCode(oldCode).isEmpty(), "протухшая ссылка удалена");
        assertTrue(service.findByCode(freshCode).isPresent(), "живая ссылка осталась");
    }

    @Test
    void ownerIsNotifiedAboutExpiration() {
        service = serviceWithTtl(Duration.ofMinutes(30));
        UUID user = UUID.randomUUID();
        String code = service.extractCode(service.shorten(user, "https://example.com/notify", 10));

        clock.advance(Duration.ofMinutes(31));
        service.purgeExpired();

        assertTrue(notifications.stream().anyMatch(m -> m.contains("истёк")),
                "владелец должен получить уведомление об истечении срока жизни");
    }

    @Test
    void differentTtlIntervalsWork() {
        // проверяем несколько интервалов: 10 минут, час, сутки
        for (Duration ttl : List.of(Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofDays(1))) {
            repository = new LinkRepository();
            notifications = new ArrayList<>();
            clock = new MutableClock(Instant.parse("2026-11-12T12:00:00Z"));
            service = serviceWithTtl(ttl);

            UUID user = UUID.randomUUID();
            String code = service.extractCode(service.shorten(user, "https://example.com/x", 10));

            // за секунду до истечения — работает
            clock.advance(ttl.minusSeconds(1));
            assertEquals("https://example.com/x", service.resolve(code), "TTL=" + ttl);

            // после — удалена
            clock.advance(Duration.ofSeconds(2));
            assertThrows(LinkExpiredException.class, () -> service.resolve(code), "TTL=" + ttl);
        }
    }
}
