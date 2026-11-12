package com.example.urlshortener;

import com.example.urlshortener.config.AppConfig;
import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.ShortCodeGenerator;
import com.example.urlshortener.service.UrlShortenerService;
import com.example.urlshortener.service.UrlShortenerService.LinkNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Обработка ошибок и устойчивость: валидация URL,
 * граничные значения лимитов, несуществующие ссылки.
 */
class ErrorHandlingTest {

    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        AppConfig config = new AppConfig(Duration.ofHours(24), "clck.ru", 100);
        service = new UrlShortenerService(new LinkRepository(), new ShortCodeGenerator(),
                (userId, message) -> { }, config, Clock.systemUTC());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "not-a-url",
            "ftp://example.com",
            "http://",
            "https://",
            "https://exam ple.com",
            "https://example.com/page with space"
    })
    void invalidUrlsAreRejected(String badUrl) {
        assertThrows(IllegalArgumentException.class,
                () -> service.shorten(UUID.randomUUID(), badUrl, 10),
                "должен отклоняться URL: '" + badUrl + "'");
    }

    @Test
    void validUrlsAreAccepted() {
        UUID user = UUID.randomUUID();
        assertDoesNotThrow(() -> service.shorten(user, "https://example.com", 10));
        assertDoesNotThrow(() -> service.shorten(user, "http://sub.example.com:8080/p?q=1#f", 10));
    }

    @Test
    void negativeOrZeroLimitOnEditIsRejected() {
        UUID user = UUID.randomUUID();
        String code = service.extractCode(service.shorten(user, "https://example.com/lim", 5));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateMaxClicks(user, code, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateMaxClicks(user, code, -10));
        // лимит не изменился
        assertEquals(5, service.findByCode(code).orElseThrow().getMaxClicks());
    }

    @Test
    void resolvingUnknownCodeGivesClearError() {
        LinkNotFoundException ex = assertThrows(LinkNotFoundException.class,
                () -> service.resolve("noSuchCode"));
        assertTrue(ex.getMessage().contains("не найдена"));
    }

    @Test
    void fullShortUrlAndBareCodeAreBothAccepted() {
        UUID user = UUID.randomUUID();
        String shortUrl = service.shorten(user, "https://example.com/both", 5);
        String code = service.extractCode(shortUrl);

        assertEquals(code, service.extractCode(shortUrl), "извлечение кода из полной ссылки");
        assertEquals(code, service.extractCode(code), "голый код проходит как есть");
        assertEquals("https://example.com/both", service.resolve(service.extractCode(shortUrl)));
    }

    @Test
    void operationsOnMissingLinkGiveClearError() {
        UUID user = UUID.randomUUID();
        assertThrows(LinkNotFoundException.class,
                () -> service.updateMaxClicks(user, "absent", 10));
        assertThrows(LinkNotFoundException.class,
                () -> service.deleteLink(user, "absent"));
    }
}
