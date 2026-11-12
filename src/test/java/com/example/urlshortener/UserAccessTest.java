package com.example.urlshortener;

import com.example.urlshortener.config.AppConfig;
import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.NotificationService;
import com.example.urlshortener.service.ShortCodeGenerator;
import com.example.urlshortener.service.UrlShortenerService;
import com.example.urlshortener.service.UrlShortenerService.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Этап 2.4: идентификация пользователя по UUID —
 * редактирование и удаление доступны только создателю ссылки.
 */
class UserAccessTest {

    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        NotificationService notifier = (userId, message) -> { };
        AppConfig config = new AppConfig(Duration.ofHours(24), "clck.ru", 100);
        service = new UrlShortenerService(new LinkRepository(), new ShortCodeGenerator(),
                notifier, config, Clock.systemUTC());
    }

    @Test
    void ownerCanEditAndDeleteOwnLink() {
        UUID owner = UUID.randomUUID();
        String code = service.extractCode(service.shorten(owner, "https://example.com/own", 5));

        assertDoesNotThrow(() -> service.updateMaxClicks(owner, code, 50));
        assertEquals(50, service.findByCode(code).orElseThrow().getMaxClicks());

        assertDoesNotThrow(() -> service.deleteLink(owner, code));
        assertTrue(service.findByCode(code).isEmpty());
    }

    @Test
    void strangerCannotEditLink() {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        String code = service.extractCode(service.shorten(owner, "https://example.com/protected", 5));

        assertThrows(AccessDeniedException.class,
                () -> service.updateMaxClicks(stranger, code, 999));
        // лимит не изменился
        assertEquals(5, service.findByCode(code).orElseThrow().getMaxClicks());
    }

    @Test
    void strangerCannotDeleteLink() {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        String code = service.extractCode(service.shorten(owner, "https://example.com/protected2", 5));

        assertThrows(AccessDeniedException.class,
                () -> service.deleteLink(stranger, code));
        assertTrue(service.findByCode(code).isPresent(), "ссылка должна остаться");
    }

    @Test
    void userSeesOnlyOwnLinks() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        service.shorten(alice, "https://example.com/alice", 5);
        service.shorten(bob, "https://example.com/bob1", 5);
        service.shorten(bob, "https://example.com/bob2", 5);

        assertEquals(1, service.listUserLinks(alice).size());
        assertEquals(2, service.listUserLinks(bob).size());
    }

    @Test
    void oneUserCanHaveManyLinksAndTheyAreIndependent() {
        UUID user = UUID.randomUUID();
        String code1 = service.extractCode(service.shorten(user, "https://example.com/p1", 1));
        String code2 = service.extractCode(service.shorten(user, "https://example.com/p2", 5));
        String code3 = service.extractCode(service.shorten(user, "https://example.com/p3", 5));

        assertEquals(3, service.listUserLinks(user).size());

        // исчерпание лимита одной ссылки не влияет на остальные
        service.resolve(code1);
        assertThrows(UrlShortenerService.ClickLimitException.class, () -> service.resolve(code1));
        assertEquals("https://example.com/p2", service.resolve(code2));
        assertEquals("https://example.com/p3", service.resolve(code3));
    }
}
